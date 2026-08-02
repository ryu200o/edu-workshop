# ADR 0014: Registration Reschedule Emergency Grace Period and System Refund Semantics

* **Status**: ACCEPTED
* **Date**: 2026-08-02
* **Deciders**: Lead Engineer, Solution Architect (SA)
* **Technical Domain**: `Registration` Aggregate, Cross-Module Integration (`Workshop` $\rightarrow$ `Registration`), Event Driven Architecture (Outbox Pattern)

---

## 1. Context & Problem Statement

Trong quá trình vận hành hệ thống **EduWorkshop**, hai lỗ hổng nghiệp vụ (Business Blind Spots) nghiêm trọng đã được phát hiện tại module `Registration`:

1. **Sự nhầm lẫn chỉ số Analytics khi Workshop bị Hủy (Registration Status Pollution)**:
   Trước đây, khi Ban tổ chức hủy bài học (`WorkshopCancelledIntegrationEvent`), tất cả các vé đăng ký active (`REGISTERED`) đều bị chuyển về trạng thái `CANCELLED`. Việc này làm hòa tan dữ liệu báo cáo: không thể phân biệt giữa **"Học viên chủ động hủy vé"** (Churn/User behavior) và **"Học viên bị hủy vé do lỗi từ Ban tổ chức"** (System Refund/Operational issue).
2. **"Bẫy thời gian" khi Ban tổ chức đổi lịch học (Reschedule Time Trap)**:
   Theo ADR 0005, Ban tổ chức được phép `Reschedule` bài học trước giờ học 24h (`RESCHEDULE_DEADLINE`). Nếu bài học bị dời lịch vào mốc 25 giờ trước giờ G, học viên chỉ còn **đúng 1 giờ** để bấm hủy vé theo quy định `CancellationDeadline` = 24h. Nếu không kịp đọc thông báo trong 1 giờ đó, học viên hoàn toàn bị "khóa cứng" quyền hủy vé cho lịch học mới mà họ không thể tham dự.

---

## 2. Decision Drivers

* **Product Ethics & Customer Protection**: Bảo vệ quyền lợi hợp pháp của học viên khi Ban tổ chức thay đổi cam kết dịch vụ.
* **Data & Analytics Integrity**: Phân định rạch ròi lý do hủy vé phục vụ báo cáo và kiểm toán tài chính/hoàn tiền.
* **Hexagonal Architecture & Eventual Consistency**: Đảm bảo việc xử lý sự kiện liên module (`Workshop` $\rightarrow$ `Registration`) diễn ra bất đồng bộ qua Outbox Pattern, không tạo ra Cross-Module Transaction hay Direct Dependency.
* **High-Throughput Batch Processing**: Đảm bảo xử lý hàng loạt vé đăng ký khi Event xảy ra với chi phí I/O Database thấp nhất (JDBC Batching).

---

## 3. Decision Outline

### 3.1 Khái niệm trạng thái `REFUNDED` (System-Initiated Refund)

* Bổ sung giá trị `REFUNDED` vào `RegistrationState` enum (`REGISTERED`, `CANCELLED`, `REFUNDED`).
* Đưa vào Domain Aggregate method `refundBySystem(Instant now)` với quy tắc **Idempotent Guard**:
* Nếu `state == REGISTERED` $\rightarrow$ Chuyển sang `REFUNDED`, `touch(now)`, phát sinh domain event `RegistrationRefunded`.
* Nếu `state == CANCELLED` hoặc `REFUNDED` $\rightarrow$ **No-Op** (Lặng lẽ bỏ qua, không đổi trạng thái, không throw Exception) để bảo vệ tính toàn vẹn của Transaction khi xử lý Batch.


* Cập nhật `WorkshopCancelledEventHandler` theo **3-Phase Execution Pattern** trong một giao dịch `@Transactional(propagation = Propagation.REQUIRES_NEW)`:
* *Phase 1 (Mutate)*: Tải danh sách `REGISTERED` via `loadAllByWorkshopIdAndState`, thực thi `refundBySystem(now)`.
* *Phase 2 (Persist)*: Gọi `registrationRepository.saveAll(activeList)` tận dụng JDBC Batching (`hibernate.jdbc.batch_size=50`).
* *Phase 3 (Publish)*: Phát hành hàng loạt Domain Events.



### 3.2 Cửa sổ Hủy vé Cấp bách 12 Giờ (12-Hour Emergency Grace Period)

* Bổ sung thuộc tính `gracePeriodUntil` (`Instant`, nullable) vào Aggregate Root `Registration`.
* Khi nhận `WorkshopRescheduledIntegrationEvent`, Handler kích hoạt `grantGracePeriod(rescheduledAt, newStartTime, now)`.
* **Grace Period chỉ được cấp khi Reschedule khẩn cấp (Urgent)**: giờ học mới đến trước 12 giờ kể từ thời điểm Reschedule diễn ra:

$$\text{Urgent} = \big( \text{newStartTime} - \text{rescheduledAt} < 12\text{ Hours} \big)$$

Nếu khẩn cấp ($\text{Urgent} = true$):

$$\text{gracePeriodUntil} = \text{rescheduledAt} + 12\text{ Hours}$$

Ngược lại (Reschedule xa, ví dụ vài ngày — deadline hủy 24h chuẩn đã đủ đảm bảo quyền lợi), **không cấp** cửa sổ grace: $\text{gracePeriodUntil}$ được đưa về `null`.

Đồng thời cập nhật snapshot `workshopStartTime = newStartTime` (trong cả hai nhánh).
* Mở rộng quy tắc Guard Check trong `Registration.cancel(Instant now)`:

$$\text{CanCancel} = (\text{now} < \text{workshopStartTime}) \quad \land \quad \Big[ (\text{now} < \text{workshopStartTime} - 24\text{h}) \ \lor \ (\text{gracePeriodUntil} \neq \text{null} \ \land \ \text{now} < \text{gracePeriodUntil}) \Big]$$

* **Invariants bất biến**:
1. Nếu $\text{now} \ge \text{workshopStartTime}$, lệnh hủy **luôn bị từ chối** (`CancellationDeadlineExceededException`), bất kể còn trong Grace Period hay không.
2. Nếu bài học bị Reschedule lần 2 **khẩn cấp**, `gracePeriodUntil` sẽ được gia hạn thành $\text{newRescheduledAt} + 12\text{h}$; nếu Reschedule không khẩn cấp, `gracePeriodUntil` được đưa về `null`.



---

## 4. Technical & Database Impacts

1. **Database Schema Migrations**:
* `V10__allow_refunded_in_check_constraint.sql`: Bổ sung giá trị `'REFUNDED'` vào DB Check Constraint của bảng `registrations`.
* `V11__add_grace_period_to_registrations.sql`: Bổ sung cột `grace_period_until TIMESTAMP WITH TIME ZONE NULL` vào bảng `registrations`.


2. **Persistence Layer Optimization**:
* Bật cấu hình `hibernate.jdbc.batch_size=50`, `order_inserts=true`, và `order_updates=true` trong `application.properties` để tối ưu hóa lệnh `saveAll()` thành multi-row JDBC batch payloads.


3. **CQRS Read-Side Exemption**:
* Cột `grace_period_until` chỉ phục vụ Write-Side Domain Invariants. Không cập nhật JOOQ Codegen Read-Model ở giai đoạn này (YAGNI).



---

## 5. Consequences & Trade-offs

### Positive

* **Báo cáo sạch (Clean Analytics)**: Phân định tuyệt đối giữa User Churn (`CANCELLED`) và System Refund (`REFUNDED`).
* **Trải nghiệm khách hàng công bằng**: Khi Ban tổ chức dời lịch gấp (giờ mới trong vòng 12h), học viên luôn có tối thiểu 12 giờ để phản ứng hủy vé; khi dời xa, deadline hủy 24h chuẩn đã đảm bảo quyền lợi, nên không cấp cửa sổ dư thừa.
* **Hiệu năng hệ thống cao**: Áp dụng 3-Phase Execution và JDBC Batching giúp Event Listener xử lý hàng ngàn bản ghi `Registration` trong 1 Database Roundtrip.

### Negative / Trade-offs

* **Tăng độ phức tạp Domain**: Guard clause trong `cancel()` phải quản lý thêm nhánh kiểm tra `gracePeriodUntil`.
* **Phát sinh Schema Mutation**: Yêu cầu 2 bản SQL Migration (`V10`, `V11`) trên môi trường sản xuất.