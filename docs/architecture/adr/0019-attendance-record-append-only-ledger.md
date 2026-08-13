# ADR 0019: Attendance Record Aggregate & Append-Only Decision Ledger

- **Trạng thái**: Accepted — REVISED v2 (phán quyết 13 OQ, ngày 13/08/2026, Kỹ sư trưởng)
- **Ngày chốt**: 12/08/2026 (v2: 13/08/2026)
- **Tác giả**: Solution Architect & Kỹ sư trưởng
- **Ngữ cảnh**: Epic 3 - Attendance & Reconciliation Management

---

## 1. Ngữ Cảnh & Vấn Đề (Context)

Nghiệp vụ điểm danh (Attendance) yêu cầu ghi nhận sự có mặt của học viên và xử lý các ca khiếu nại/điều chỉnh trong khung thời gian đối soát (Reconciliation Window). Ban Giám đốc và Đội Kiểm toán đặt ra yêu cầu cốt lõi về **Tính Minh Bạch Quyết Định (Decision Accountability)**:

1. **Không ghi đè dữ liệu quá khứ**: Mọi tác động (điểm danh, khiếu nại, kiểm toán điều chỉnh) phải lưu lại lịch sử nguyên vẹn, không bao giờ dùng lệnh `UPDATE` hay `DELETE` đè lên kết quả cũ.
2. **Tối ưu tra cứu hai tầng**: Màn hình vận hành hàng ngày cần "Xem nhanh" kết quả hiện tại ($O(1)$ read), trong khi màn hình kiểm toán cần "Xem chi tiết" con đường dẫn tới kết quả đó.
3. **Phân định trách nhiệm rõ ràng**: Phân định chính xác các hành vi theo vai trò (`TRAINER`, `STUDENT`, `AUDITOR`) và theo trạng thái vòng đời (`OPEN`, `RECONCILING`, `FINALIZED`).

---

## 2. Quyết Định Kiến Trúc (Decisions)

### 2.1. Đóng Gói Domain Behavior-Driven (Không dùng Event Sourcing)

- **Aggregate Root**: Xây dựng `AttendanceRecord` đóng vai trò "Hồ sơ điểm danh" đại diện cho giao dịch giữa 1 Học viên và 1 Workshop Session.
- **Encapsulated Append-Only**: Danh sách `entries` (`List<AttendanceEntry>`) được giấu kín bên trong Aggregate. Việc thêm dòng mới chỉ thông qua các phương thức nghiệp vụ công khai. `appendEntry()` là **private helper** nội bộ, không xuất hiện ở interface bên ngoài.
- **Cấm getter có thể Modify**: getter trả danh sách entries phải là `Collections.unmodifiableList`.
- **Lock cứng FINALIZED**: khi `state == FINALIZED`, mọi method thay đổi dữ liệu phải ném `DomainException` với thông điệp **"Attendance record is finalized and locked"**.

### 2.2. Master-Ledger Persistence Schema

- Sử dụng mô hình Quan hệ tiêu chuẩn trên PostgreSQL (Flyway Migration `V17__create_attendance_tables.sql`):
- Bảng Master `attendance_records`: thông tin định danh + cột khử chuẩn `current_result` phục vụ Quick View $O(1)$ (`current_result` là **materialized current state**, KHÔNG phải nguồn lịch sử).
- Bảng Ledger `attendance_entries`: nhật ký vết quyết định, khóa chính hợp phần `(record_id, entry_number)`.

**Định danh & khóa (v2):**
- `attendance_records.id`, `attendance_entries.record_id` = **`UUID`** (nhất quán toàn hệ thống; không `VARCHAR(36)`).
- `attendance_records.version BIGINT NOT NULL DEFAULT 0` — optimistic locking (khi Trainer mark/correct và Auditor adjust là các authoritative mutation ĐỒNG THỜI đều hợp lệ trên cùng aggregate). Conflict → `ObjectOptimisticLockingFailureException` → **HTTP 409**.
- **FK ledger**: `ON DELETE RESTRICT` — không cascade, bảo vệ append-only. Khi `AttendanceRecord` tồn tại, lịch sử `AttendanceEntry` của nó không được phép bị xóa độc lập.

---

## 3. Quyền Sở Hữu Lifecycle — Workshop State Authority

> `Attendance` **không tự suy diễn Workshop lifecycle từ `startTime` / `endTime`**.

Nguồn sự thật duy nhất cho phase của Attendance là lifecycle **state/event từ Workshop**:

```
Workshop IN_PROGRESS
    → Attendance session active (OPEN)

Workshop COMPLETED
    → Attendance enters reconciliation (RECONCILING)

Reconciliation deadline reached
    → Attendance FINALIZED
```

`startTime`/`endTime` KHÔNG được Attendance dùng làm thay thế cho state authority. Attendance **không tự query Workshop Repository** — mọi thông tin workshop đến từ **Module Facade (Expose API) hoặc Integration Event** (ADR 0010).

- Mark chỉ hợp lệ khi `Workshop.state == IN_PROGRESS` — không suy diễn bằng `now >= startTime`.
- Chuyển RECONCILING thông qua **`WorkshopCompletedIntegrationEvent`** (outbox, ADR 0011), không phải suy diễn thời gian.

---

## 4. Reconciliation Temporal Semantics

> `WorkshopCompleted.completedAt` là **authoritative temporal anchor** cho Reconciliation Window.

Công thức:

```
reconciliationDeadline = completedAt + activeReconciliationWindow
```

- `activeReconciliationWindow` là **Operational Setting** hiện hành (mặc định **24h**), KHÔNG phải Domain constant — không hard-code `Duration.ofHours(24)` trong Domain. Giá trị được đưa vào qua Application edge (config), domain nhận param.
- `AttendanceRecord` snapshot **`reconciliation_started_at` (= completedAt)** — thứ attendance thực sự cần cho reconciliation; **KHÔNG** snapshot `workshop_end_time` để tự tính lifecycle.
- `Instant.now()` CHỈ được dùng trong **recovery path bất thường** khi completion timestamp bị thiếu (event lost) — phải trace/audit; `now` không phải normal fallback. Recovery phải lấy lại `completedAt` authoritative (qua Expose API), không dùng `now`.

---

## 5. Appeal Semantics — BẮT BUỘC

> **Student Appeal KHÔNG thay đổi `AttendanceRecord.currentResult`.**

Appeal chỉ:

```
submit request + reason + evidence
```

Sau đó Auditor mới:

```
review → auditorAdjust() → append authoritative entry → currentResult thay đổi
```

Điều này bảo đảm:

```
Student request  ≠  Attendance decision
Auditor decision = authoritative adjustment
```

`submitAppeal()` chỉ ghi nhận **Appeal Request & Evidence** để Auditor xử lý. Chỉ `auditorAdjust()` tạo một **Attendance Decision mới** làm thay đổi `currentResult`.

---

## 6. Attendance History Rule

> **Mọi thay đổi nghiệp vụ đối với Attendance Record đều append một dòng mới. Không sửa/xóa dòng lịch sử đã tồn tại.**

- Không có lệnh `UPDATE`/`DELETE` nhắm vào `attendance_entries` (INSERT duy nhất).
- `currentResult` chỉ là **materialized current state** phục vụ Quick View. Nguồn lịch sử là **`attendance_entries`**.
- Mạnh hơn OQ-8: chính sách là đặc quyền hệ thống — việc xóa phải là exception được kiểm soát, không phải thao tác nghiệp vụ thông thường.

---

## 7. "Quick View" & "Detailed View" — Business Requirement

### Quick View
Hiển thị `currentResult` phục vụ vận hành nhanh (đọc bảng master, $O(1)$).

### Detailed View
Hiển thị `entries` theo thứ tự `entryNumber ASC` — để Giám đốc/Kiểm toán viên nhìn thấy toàn bộ:

```
who · when · what · why · evidence · result
```

Đây là **business requirement** (minh bạch kiểm toán), không phải optimization tùy ý.

---

## 8. Security Boundary

> Actor role **không được lấy từ HTTP request header do client tự khai báo** (`X-Actor-Role`).

- Luồng đúng: `Authenticated Principal → Security/Actor Context → Application Authorization → Command Handler → Domain behavior`.
- Application kiểm tra `TRAINER` / `STUDENT` / `AUDITOR` / `SYSTEM`, nhưng role phải đến từ **trusted authenticated actor context**.
- **Domain chỉ bảo vệ business invariants**; quyền truy cập use case được enforce ở **Application/Security boundary**.
- Role violation → **HTTP 403**.
- Header `X-Actor-Role` nếu còn dùng chỉ là **dev/test stand-in TẠM THỜI**, phải gỡ khi có Security thật (không coi là nguồn sự thật).

---

## 9. State Matrix chính thức

| Workshop / Attendance Phase | Trainer | Student | Auditor | System |
|---|---|---|---|---|
| `IN_PROGRESS` / **OPEN** | Mark / Correct | — | — | — |
| `COMPLETED` / **RECONCILING** | Không mark thông thường | Submit Appeal | Adjust | — |
| Deadline expired / **FINALIZED** | — | — | — | Finalize (đóng sổ) |

> **Appeal KHÔNG phải Attendance Adjustment** — chỉ là request/evidence chờ Auditor.

---

## 10. Hợp Đồng Hành Vi (Behavior Contract — v2 hiệu chỉnh)

- `markAttendance()`: Trainer ghi nhận/sửa kết quả **khi workshop đang IN_PROGRESS** (state OPEN). Append MARK entry, flip `currentResult`.
- `submitAppeal()`: Student nộp khiếu nại kèm bằng chứng **trong Reconciliation Window** (state RECONCILING). Append APPEAL entry, **KHÔNG đổi `currentResult`**; chuyển/cập nhật trạng thái cho Auditor.
- `auditorAdjust()`: Auditor chốt điều chỉnh (bắt buộc kèm reason). Append AUDITOR_ADJUST entry → **chỉ đây mới đổi `currentResult`**.
- `finalizeRecord()`: System đóng sổ **sau khi deadline hết hạn**. Append FINALIZE entry → state FINALIZED, khóa mọi mutation thông thường.

---

## 11. Integration Events (Outbox, ADR 0011)

- Attendance nhận sự kiện cross-module qua outbox: **`WorkshopCompletedIntegrationEvent`** (cần bổ sung vào Workshop contract) kích hoạt `beginReconciliation(completedAt)` — replay-safe (idempotent).
- Event catalog của Attendance phản ánh **business fact đã xảy ra**, không phải CRUD persistence:
  - `AttendanceMarked` — quyết định điểm danh của Trainer.
  - `AuditorAdjustedAttendance` — quyết định điều chỉnh của Auditor (authoritative).
  - `AttendanceRecordFinalized` — chốt sổ.
  - `AttendanceAppealSubmitted` — sự kiện quy trình Appeal (nếu Appea boundary tách riêng — đánh giá trong API design final; không nhất thiết là state transition của AttendanceRecord).

---

## 12. Hệ Quả & Đánh Giá (Consequences)

### Tích cực
- 100% Invariants bảo vệ tại tầng Domain; không phụ thuộc Event Store phức tạp.
- Quick View 1 câu `SELECT` (master) / Detailed View JOIN ledger theo `entry_number ASC`.
- Sự khác biệt Student request vs Auditor decision được pháp chế hóa (Appeal không mutation) → đáp ứng kiểm toán 100%.

### Đánh đổi
- `attendance_entries` phình to theo thời gian (chấp nhận; partition theo `created_at` ở tương lai).
- Collaboration mới: Workshop phải phát hành `WorkshopCompletedIntegrationEvent` (thêm contract + publish case trong `WorkshopDomainEventListener`).
- Reconciliation Window là operational setting → thay đổi ảnh hưởng deadline nhưng không đụng domain (consistent với ADR 0018 guardrail).