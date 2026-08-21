# ADR 0022: Declarative Idempotency Framework Using Redis

* **Trạng thái:** PROPOSED (Đã thống nhất SA, BA và Đội thi công)
* **Ngày:** 2026-08-21
* **Người quyết định:** Software Architect (SA), Business Analyst (BA), Core Engineering Team
* **Tài liệu liên quan:**
* `ADR 0021: Strict Command-Query Separation (Strict CQS)`

* `ADR 0015: Database Concurrency Control and Unique Constraints`

* `ADR 0020: IAM Architecture & Security Ingress`




---

## 1. Bối cảnh & Vấn đề (Context & Problem Statement)

Sau khi hoàn tất chuẩn hóa **Strict CQS theo ADR 0021**:

* Toàn bộ Domain Command Handlers trên `CommandBus` trả về `void`.


* Các HTTP Inbound Controllers phản hồi các lệnh mutation bằng **`201 Created` (kèm header `Location`)** hoặc **`204 No Content`** với body rỗng.



Trong môi trường mạng không ổn định, hành vi double-submit (người dùng bấm nhiều lần hoặc client tự động retry) tại các endpoint nhạy cảm như *Tạo phòng, Đăng ký vé Workshop, Check-in, Khiếu nại* đòi hỏi một cơ chế bảo vệ cửa ngõ (Ingress Idempotency Guard).

Khảo sát phương án lưu trữ trên RDBMS (PostgreSQL) cho thấy độ phức tạp không đáng có: bẫy `ABORTED transaction` khi trùng khóa, phải chia nhỏ nhiều transaction `REQUIRES_NEW`, và phải vận hành `@Scheduled` job để dọn dẹp TTL. Do đó, hệ thống quyết định chuyển dịch toàn bộ tầng lưu trữ Idempotency sang **Redis** (`StringRedisTemplate`) để tận dụng tính nguyên tử `SET ... NX EX` và cơ chế tự hủy tự nhiên của Key-Value Store.

---

## 2. Quyết định Kiến trúc (Decision)

### 2.1 Chính sách Giao ước Bắt buộc (@Idempotent)

* Khai báo Annotation `@Idempotent` tại phương thức Controller của Inbound Web Adapter.
* **Giao ước bắt buộc (Mandatory Contract):** Client gọi endpoint có gắn `@Idempotent` **bắt buộc** phải gửi kèm header `Idempotency-Key` (chuỗi ký tự độ dài từ `1` đến `64`).
* Nếu thiếu header hoặc header rỗng/quá 64 ký tự: Hệ thống ngắt luồng ngay lập tức và trả về **`400 Bad Request`** (chuẩn RFC 7807 Problem Details).
* Các endpoint không gắn annotation sẽ xử lý bình thường và bỏ qua header này.

```java
package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentCommand {
    long ttlMinutes() default 1440; // Mặc định 24 giờ (1440 phút)
}

```

### 2.2 Danh mục Whitelist Endpoints Bảo Vệ

Chỉ áp dụng `@Idempotent` cho đúng 8 endpoints có rủi ro double-submit cao:

| Phân nhóm | Endpoint áp dụng | Rủi ro nếu thiếu Idempotency |
| --- | --- | --- |
| **Khởi tạo tài nguyên** | `POST /api/v1/workshops`<br>

<br>`POST /api/v1/rooms`<br>

<br>`POST /api/v1/iam/admin/users`<br>

<br>`POST /api/v1/rooms/{id}/maintenance-schedules` | Trùng lặp phòng, lịch bảo trì hoặc tài khoản quản trị. |
| **Giao dịch & Vé** | `POST /api/v1/registrations` | Giữ trùng slot ghế / tạo nhiều vé cho 1 người. |
| **Điểm danh & Khiếu nại** | `POST /api/v1/workshops/{id}/attendance/check-in`<br>

<br>`POST /api/v1/attendance-records/{id}/appeal` | Gửi 2 bản ghi check-in hoặc spam đơn khiếu nại. |
| **Xác thực Ingress** | `POST /api/v1/iam/auth/register` | Spam gửi lệnh tạo user mới. |

### 2.3 Không Gian Tên Redis (Redis Key Namespace)

Để phân lập hoàn toàn giữa các người dùng, phương thức và đường dẫn, Redis Key được chuẩn hóa theo định dạng:

$$\text{Key} = \text{idempotency:}\{\text{principal\_id}\}\text{:}\{\text{http\_method}\}\text{:}\{\text{normalized\_path}\}\text{:}\{\text{idempotency\_key}\}$$

* `principal_id`: UUID của User đã xác thực từ `SecurityContextHolder` (dùng `00000000-0000-0000-0000-000000000000` cho unauthenticated endpoint như Register).
* `http_method`: `POST`, `PUT`, `PATCH`.
* `normalized_path`: Request URI đã chuẩn hóa (ví dụ: `/api/v1/workshops/550e8400.../attendance/check-in`), **loại bỏ toàn bộ Query String**.
* `idempotency_key`: Giá trị đọc từ Header `Idempotency-Key`.

### 2.4 Cấu trúc Dữ liệu Lưu trữ (Lightweight Metadata)

* **Trạng thái đang xử lý:** Lưu giá trị chuỗi `"IN_PROGRESS"`.
* **Trạng thái hoàn tất:** Lưu JSON chuỗi metadata tối thiểu (phù hợp với Strict CQS no-body):
```json
{"status": 201, "location": "/api/v1/rooms/550e8400-e29b-41d4-a716-446655440000"}

```


*(Đối với mã `204`, trường `location` là `null`).*

### 2.5 Chu trình Xử lý Đồng bộ qua Spring AOP Aspect (`@Around`)

```
[Request đến Controller có @Idempotent]
                   │
                   ├── Thiếu 'Idempotency-Key' ──▶ Trả về HTTP 400 Bad Request
                   │
                   ▼
[1. Reserve Phase: SET key "IN_PROGRESS" NX EX <ttl>]
                   │
                   ├── THÀNH CÔNG (Redis trả về true):
                   │       │
                   │       ▼
                   │   [2. Execute: pjp.proceed() -> Gọi Controller / CommandBus] [void]
                   │       │
                   │       ├── Thành công (Trả về ResponseEntity):
                   │       │     └── SET key "{\"status\":201,\"location\":\"...\"}" XX EX <ttl>
                   │       │         (Cập nhật COMPLETED trước khi commit socket mạng)
                   │       │
                   │       └── Thất bại (DomainException / Lỗi hệ thống):
                   │             └── DEL key (Xóa key ngay lập tức để cho phép retry sạch)
                   │                 ──▶ Ném exception ra @RestControllerAdvice
                   │
                   └── THẤT BẠI (Redis trả về false - Key đã tồn tại):
                           │
                           ├── Value = "IN_PROGRESS" ──▶ Trả về HTTP 409 Conflict
                           │
                           └── Value = JSON Metadata ──▶ Parse JSON và Replay ngay lập tức

```

1. **Pha 1 (Reserve):** Thực thi lệnh nguyên tử `SET key "IN_PROGRESS" NX EX <seconds>`.
* Nếu thành công: Cho phép luồng đi tiếp vào Controller.
* Nếu thất bại (Key đã tồn tại): Đọc giá trị hiện có:
* Nếu giá trị là `"IN_PROGRESS"`: Ném ngoại lệ trả về **`409 Conflict`** (Fast-fail tức thì).
* Nếu giá trị là JSON Metadata: Deserialize thành `ResponseEntity<Void>` kèm `Location` header tương ứng để **Replay ngay lập tức** (ngắt luồng, không gọi Controller).




2. **Pha 2 (Execute):** Cho phép `pjp.proceed()` thực thi Controller $\rightarrow$ `CommandBus.execute(command)`.


3. **Pha 3 (Finalize & Cleanup):**
* *Khi Controller trả về `ResponseEntity`:* Trích xuất `status` và `Location`, ghi đè value trong Redis thành JSON metadata kèm TTL ban đầu.
* *Khi Controller/Handler ném Exception:* Thực thi `DEL key` ngay lập tức để giải phóng key, cho phép Client sửa đổi form và submit lại bình thường.



---

## 3. Cấu Trúc Gói Mã Nguồn (Package Layout)

Toàn bộ triển khai được đóng gói gọn trong module `shared`, giữ nguyên tắc `internal/` của dự án:

```
shared/infrastructure/idempotency/
├── api/
│   └── IdempotentCommand.java              # [PUBLIC] Annotation cho Controller
└── internal/
    ├── IdempotentCommandAspect.java        # [PACKAGE-PRIVATE] Spring AOP Aspect
    ├── RedisIdempotencyStorageService.java # [PACKAGE-PRIVATE] Thao tác StringRedisTemplate
    ├── IdempotencyMetadata.java            # [PACKAGE-PRIVATE] Record đóng gói JSON status/location
    └── exception/
        ├── MissingIdempotencyKeyException.java    # Trả về 400
        └── ConcurrentIdempotencyException.java   # Trả về 409

```

---

## 4. Đánh Đổi & Hậu Quả (Consequences)

### Ưu Điểm

* **Đơn Giản Hóa Tuyệt Đối:** Loại bỏ 100% Flyway SQL migrations, không cần entity JPA, không cần cấu hình transaction `REQUIRES_NEW`, và không cần `@Scheduled` cleanup job.
* **Hiệu Năng Cực Cao:** Thao tác In-memory trên Redis có độ trễ $< 1\text{ms}$, không gây tải I/O hay tranh chấp Connection Pool trên PostgreSQL.
* **Tự Động Thu Gom Rác:** Cơ chế TTL tự nhiên của Redis đảm bảo key tự động biến mất sau thời gian hết hạn mà không cần can thiệp mã nguồn.
* **Bảo Vệ Toàn Diện & Chống Race Condition:** Lệnh `SET NX EX` đảm bảo tính nguyên tử tuyệt đối ở mức hạ tầng.

### Nhược Điểm & Ràng Buộc

* **Phụ Thuộc Hạ Tầng:** Bắt buộc môi trường triển khai (Dev, Testcontainers, Staging, Production) phải có Redis instance hoạt động.
* **Giao Ước Bắt Buộc:** Client (Web/Mobile/Test Suite) bắt buộc phải chủ động sinh header `Idempotency-Key` khi gọi 8 endpoints whitelist.

---

## 5. Kế Hoạch Triển Khai (Implementation Slices)

* **Slice 1 (Core Redis Framework & Isolation Tests):**
* Thêm dependency `spring-boot-starter-aop` và `spring-boot-starter-data-redis` (nếu chưa có).
* Viết trọn bộ package `shared/infrastructure/idempotency/`.
* Viết `RedisIdempotencyIntegrationTest` (dùng Testcontainers Redis hoặc embedded) xác thực 5 ca kiểm thử: Missing Key (400), First-time execute (201/204), In-Progress lock (409), Replay (201/204), và Cleanup on Failure.


* **Slice 2 (Whitelist Rollout & Explicit Test Migration):**
* Gắn `@Idempotent` lên đúng 8 endpoints whitelist.
* Cập nhật các bài test E2E/Integration liên quan bằng `withIdempotencyKey()` explicit helper.
* Xác nhận toàn bộ test suite xanh.