# ADR 0021: Strict Command-Query Separation (CQS) Architecture

* **Trạng thái:** ACCEPTED
* **Ngày ban hành:** 2026-08-19
* **Người đề xuất:** Solution Architect & Kỹ sư trưởng
* **Tài liệu bị thay thế (Supersedes):** ADR 0004 (*Hybrid CQS Architecture*)


* **Baseline Codebase:** Commit `12ee833` (745/745 tests PASS, 6 business modules: `Room`, `Workshop`, `Registration`, `Attendance`, `FacilityOps`, `IAM`).



---

## 1. Bối cảnh & Vấn đề (Context)

Tại thời điểm áp dụng ADR 0004, hệ thống sử dụng mô hình *Hybrid CQS*, cho phép `CommandHandler` trả về các đối tượng `Result` mang dữ liệu phụ trợ (như `UUID` vừa tạo, `Instant updatedAt`, `processedCount`, hoặc cờ cảnh báo `hasRoomWarning`).

Sau khi hoàn thiện lõi nghiệp vụ (Command Side) của toàn bộ 6 modules với 48 Command Handlers (sau khi tách Login/Refresh khỏi bus còn **46 bus handlers**) và đóng gói module `IAM`, mô hình Hybrid CQS bộc lộ các hạn chế kiến trúc:

1. **Rò rỉ dữ liệu Read-Model vào Write-Model:** Một số Command Handler vừa thực thi thay đổi trạng thái (State Mutation), vừa gộp logic tính toán hiển thị (như `IamSelfController.updateProfile` trả về `MeView`, hay `PlanWorkshopCommand` tính toán `hasRoomWarning`).


2. **Cản trở chuyển đổi Bất đồng bộ (Async Ingress):** Việc Command Handler trả về dữ liệu đồng bộ khiến chữ ký hàm (Interface Contract) bị gắn chặt vào cơ chế Request/Response tức thì, gây khó khăn khi cần đưa các tác vụ chịu tải cao vào hàng đợi (Queue/Outbox).
3. **Nguy cơ rò rỉ bảo mật (Token Leakage):** Một số lệnh đăng ký hoặc quên mật khẩu trả trực tiếp mã `verifyToken`/`resetToken` về HTTP Response Body thay vì phân phối qua kênh bảo mật Outbox/Email.



Hệ thống cần một quy chuẩn **Strict CQS** chính quy, nhất quán và phân rã tuyệt đối giữa nhánh Ghi (Write) và nhánh Đọc (Read).

---

## 2. Quyết định Kiến trúc (Decisions)

### 2.1. Chuẩn hóa Shared Kernel: Command thuần `void`

* Triệt tiêu toàn bộ Generic Return Type trên bus CQS dùng chung:


* `Command<R>` $\rightarrow$ `Command` (Marker Interface).


* `CommandHandler<C Command<R extends>, R>` $\rightarrow$ `CommandHandler<C Command extends>` với phương thức duy nhất: `void handle(C command)`.


* `CommandBus.execute(Command command)` $\rightarrow$ trả về `void`.




* Command chỉ có hai trạng thái kết thúc:
* **Thành công:** Kết thúc hàm `handle()` và không trả về bất kỳ dữ liệu nào (`void`).


* **Thất bại:** Ném ngoại lệ nghiệp vụ (`DomainException` / `ApplicationException`) được định danh cụ thể.





### 2.2. Chiến lược Sinh định danh: Caller-Generated ID (Inbound Pre-generation)

* **Quy tắc:** Với 100% các lệnh khởi tạo tài nguyên (`CreateRoomCommand`, `CreateWorkshopCommand`, `RegisterWorkshopCommand`, `RegisterCommand`, `AdminCreateUserCommand`, `ScheduleRoomMaintenanceCommand` — **đúng 6 commands**, `RegisterCommand` thuộc nhóm này theo OQ-3), ID của Aggregate Root **không được sinh ngẫu nhiên bên trong Domain hay Application Handler**.


* **Thực thi:**
1. Tầng Inbound Adapter (REST Controller / Message Ingress) chủ động sinh `UUID` (UUIDv7/UUIDv4).


2. ID này được nạp trực tiếp vào Constructor của Command Object.


3. Command Handler nhận ID có sẵn từ Command để gán vào Aggregate Root.


4. Controller sử dụng chính ID đã sinh trước để tạo HTTP Header `Location: /{resources}/{pre_generated_id}`.





### 2.3. Ma trận Phản hồi HTTP (REST Mapping)

Tầng Controller ánh xạ các lệnh Command thành HTTP Responses theo chuẩn ngữ nghĩa REST:

| Nhóm hành vi | HTTP Status | Headers | Response Body |
| --- | --- | --- | --- |
| **Khởi tạo tài nguyên** (6 endpoints)

| `201 Created`<br> | `Location: /{resource}/{id}`<br> | **Rỗng (Empty)**<br> |
| **Biến đổi trạng thái / Mutation** (~29 endpoints)

| `204 No Content`<br> | *Không* | **Rỗng (Empty)**<br> |
| **Ngoại lệ: Cấp phát Token Bảo mật** (`login`, `refresh`)

| `200 OK` | *Không* | `AuthTokenResponse`<br> |

*Sửa lỗi trạng thái:* Chuyển đổi endpoint `POST /api/v1/rooms` và `POST /api/v1/rooms/{id}/maintenance-schedules` từ `200 OK` sang `201 Created` kèm header `Location` tương ứng.

### 2.4. Tách biệt Tác vụ Cấp phát Token khỏi CommandBus (Security Token Minting)

* `Login`/`Refresh` **không phải là Command trên CommandBus** — chúng được **tách hoàn toàn** ra khỏi bus. Chữ ký `Command → void` chỉ áp dụng cho **Domain State Mutations**; token minting là một nhóm thao tác riêng biệt (Security Ingress), không được đưa lên bus dưới dạng `Command`/`CommandHandler`.

* Cơ chế thực thi (không phải "ngoại lệ trên bus"):
  1. Định nghĩa **Inbound Port** `AuthTokenUseCase` (`login(email, password)` / `refresh(refreshToken)` → `AuthTokenResponse`).
  2. Triển khai **package-private** `AuthTokenService` (nằm trong `application/handler/`) — đóng gói toàn bộ logic BCrypt verify, lockout, JWT claims, RTR + family-revoke của Refresh Token.
  3. Controller (IAM) gọi **trực tiếp qua port** (`authTokenUseCase.login(...)`), trả `200 OK` với `AuthTokenResponse`. **Không** qua `commandBus.execute(...)`.

* Hệ quả: `CommandBus` / `CommandHandler` thuần túy `void` cho domain mutations; `AuthTokenUseCase` là một Security Ingress Port song song, bypass bus. Số lượng handler trên bus giảm từ 48 → **46** (2 handler Login/Refresh đã tách ra).



### 2.5. Bảo mật Token Xác thực & Quên mật khẩu

* `RegisterCommand` và `ForgotPasswordCommand` tuân thủ nghiêm ngặt Strict CQS:


* `POST /api/v1/iam/auth/register`: Trả về `201 Created` (Header `Location: /api/v1/iam/users/{userId}`), **Body rỗng**. Mã `verifyToken` thô **KHÔNG** nằm trong HTTP Response Body và **KHÔNG** nằm trong integration event (ADR 0020 §1.6). Raw token được phân phối qua kênh Notification/Email (triển khai tương lai); `UserRegisteredIntegrationEvent` chỉ mang `(userId, email)`.
* `POST /api/v1/iam/auth/forgot-password`: Trả về `204 No Content`, **Body rỗng**. Raw `resetToken` cũng **KHÔNG** nằm trong body lẫn event; `PasswordResetRequestedIntegrationEvent` chỉ mang `(userId, email, tokenId)`. Phân phối raw token qua kênh Notification/Email.



* Tuyệt đối không trả token kích hoạt hoặc đặt lại mật khẩu thô trong HTTP Response Body, và tuyệt đối không đóng gói raw token vào integration event (OQ-1/2).




### 2.6. Tách biệt Cảnh báo và Đọc dữ liệu (Query Separation)

* **Loại bỏ `hasRoomWarning`:** `PlanWorkshopCommandHandler` chuyển sang trả về `void`. Các cảnh báo xung đột lịch phòng không bắt buộc (Advisory Warnings) sẽ do Client chủ động truy vấn qua `FacilityOpsQueryController`.


* **Tách Command - Query tại IAM:** `PUT /api/v1/iam/me/profile` chuyển sang trả về `204 No Content`. Client cần đọc thông tin hồ sơ mới sẽ gọi `GET /api/v1/iam/me`.



---

## 3. Hệ quả (Consequences)

### 3.1. Tích cực (Positive)

* **Thuần khiết kiến trúc:** Tách bạch hoàn toàn trách nhiệm của Command Handlers (bảo vệ Invariants, đổi trạng thái) khỏi Query Handlers (chiếu dữ liệu, tối ưu hóa đọc).


* **Hỗ trợ Idempotency tự nhiên:** Caller-Generated ID đóng vai trò như một Idempotency Key tự nhiên, giúp tầng Database ngăn chặn trùng lặp giao dịch khi Client retry.


* **Sẵn sàng cho Asynchronous Execution:** Chữ ký `Command -> void` cho phép chuyển đổi bất kỳ Command đồng bộ nào sang xử lý nền (Queue / Outbox Worker) mà không làm gãy Interface của Application Layer.


* **Nâng cao an toàn bảo mật:** Triệt tiêu hoàn toàn rủi ro rò rỉ mã token bảo mật qua HTTP Response.



### 3.2. Đánh đổi & Giải pháp giảm thiểu (Trade-offs & Mitigations)

* **Tác động đến Client/Frontend:** Client không còn nhận được DTO chi tiết trong body sau các lệnh POST/PUT (ngoại trừ Login/Refresh).


* *Giải pháp:* Client áp dụng mô hình Optimistic UI hoặc thực hiện truy vấn GET theo URI trong header `Location` khi cần đồng bộ dữ liệu lớn.


* **Tác động đến Test Suite:** ~70–120 test methods (chiếm ~9–16% tổng số 745 tests) cần cập nhật lại assertions từ kiểm tra `result.*` sang kiểm tra Side-Effects hoặc mã trạng thái HTTP 201/204.


* *Giải pháp:* Tiến hành refactor tuần tự theo 6 gói thi công, giữ test suite luôn xanh qua từng commit.





---

## 4. Bảng Ánh xạ Chuyển đổi Handlers (Summary Mapping)

```
[46 Command Handlers trên bus + 2 Auth Token Ops (bypass bus)][cite: 2]
  │
  ├── 12 Handlers IAM đã có Result rỗng ──────────▶ Chuyển trực tiếp sang void[cite: 2]
  ├── 22 Handlers Mutation (Room/Workshop/...) ───▶ Bỏ Result fields ──▶ Chuyển sang void (204)[cite: 2]
  ├──  6 Handlers Tạo mới (Create/Register/...) ──▶ Nhận Caller-Generated ID ──▶ void (201 + Location)[cite: 2]
  ├──  2 Handlers Nhạy cảm Token (Register/Forgot) ─▶ Token ra khỏi HTTP body/event ──▶ void (201/204)[cite: 1, 2]
  ├──  2 Auth Token Ops (Login/Refresh) ──────────▶ TÁCH KHỎI bus → AuthTokenUseCase (bypass, 200 OK)[cite: 2]
  └──  3 Handlers Internal (LifecycleJob: Start/Complete/CatchUp) ─▶ Chuyển sang void[cite: 2]

```