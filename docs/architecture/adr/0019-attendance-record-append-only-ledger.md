# ADR 0019: Attendance Record Aggregate & Append-Only Decision Ledger

- **Trạng thái**: Accepted — REVISED v2 (phán quyết 13 OQ, ngày 13/08/2026, Kỹ sư trưởng) + **làm rõ ranh giới Bounded Context** (Domain Discovery Round 2, ngày 14/08/2026 — Business–BA–SA)
- **Ngày chốt**: 12/08/2026 (v2: 13/08/2026; Round 2: 14/08/2026)
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

> `Attendance` **không tự suy luận Workshop lifecycle từ thời gian**. Business xác nhận (Round 2):

```
Attendance KHÔNG được suy luận:

    now >= startTime
    now <= endTime
```

Attendance chỉ dựa vào **`Workshop.state`** (`IN_PROGRESS` → `COMPLETED`) làm authority. Riêng
Reconciliation Window vẫn được anchor bằng **`WorkshopCompleted.completedAt`** (mục 4) — đúng thiết kế
hiện tại, không đổi.

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

> **Nguồn tạo Attendance (Round 2):** Trainer KHÔNG còn là actor duy nhất tạo Attendance. Theo Business,
> `TRAINER` = **manual attendance management**, `STUDENT` = **QR self check-in** (use case mới — Epic 3B),
> `AUDITOR` = **reconciliation adjustment**. State Matrix trên vẫn giữ nguyên về *authorization theo phase*;
> sự xuất hiện của QR check-in chỉ bổ sung một *nguồn MARK* mới, không thay đổi Aggregate (mục 13, 14).

---

## 10. Hợp Đồng Hành Vi (Behavior Contract — v2 hiệu chỉnh)

- `markAttendance()`: ghi nhận/sửa kết quả **khi workshop đang IN_PROGRESS** (state OPEN). Append MARK entry, flip `currentResult`. Nguồn MARK có thể là Trainer (manual, Epic 3A) hoặc QR self check-in (Epic 3B) — entry giống nhau, aggregate không phân biệt nguồn.
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

---

## 13. Bounded Context Responsibilities (làm rõ Round 2)

Business–BA–SA chốt ranh giới trách nhiệm giữa 3 module. Attendance KHÔNG đảm nhiệm trách nhiệm của
2 module kia:

```
Registration   → "Student có quyền tham dự?"  (verify vé, quản lý roster)
Workshop       → "Check-in lúc này là ATTENDED hay LATE?"  (Attendance Policy)
Attendance     → "Ghi nhận kết quả và quản lý Attendance Record"
```

### 13.1. Attendance không sở hữu bất kỳ Attendance Policy nào

> **Late là quy định của từng Workshop (Planner cấu hình), không phải quy định chung của hệ thống
> Attendance.**

Attendance **không sở hữu** `Late Threshold`, `Percentage`, `Grace Rule`, v.v. **Workshop là Policy
Owner.** Attendance chỉ *lưu kết quả đã được Workshop xác định* (ATTENDED / LATE …) vào
`AttendanceRecord`. Nếu sau này cần phán quyết thời điểm check-in, trách nhiệm thuộc về Workshop
(`evaluateCheckIn()` — mục 15.1), không thuộc Attendance.

### 13.2. Attendance không quản lý roster

Roster (danh sách học viên của workshop) **thuộc Registration**. Attendance KHÔNG sở hữu danh sách
học viên. Luồng vận hành đúng:

```
Registration → Roster → Trainer UI → Trainer chỉnh sửa → Attendance Command
```

Attendance chỉ quản lý **Attendance Record** (một record cho mỗi học viên được ghi nhận).

### 13.3. Attendance không verify vé

> **Attendance không verify vé. Registration là source of truth.**

Chỉ học viên đã Verify vé (`Registration.VERIFIED`) mới được hệ thống Attendance ghi nhận (mục 14).
Attendance chỉ **đọc** trạng thái verify qua `RegistrationExposeAPI.isVerified(...)` — không có bất kỳ
logic verify nào trong Attendance.

---

## 14. Registration VERIFIED Gate

- Chỉ học viên có vé **`VERIFIED`** (Registration) mới được Attendance ghi nhận. Đúng theo business;
  **không thay đổi** so với OQ-14.
- Attendance không verify vé — **Registration là source of truth**; Attendance chỉ đọc qua
  `RegistrationExposeAPI.isVerified(workshopId, studentId)` (read-only, không write).
- Gate là **global rule** → Application orchestration (ADR 0005), aggregate `AttendanceRecord` thuần —
  không biết về Registration.
- Fail-fast, atomic cả batch: nếu bất kỳ student nào trong batch chưa VERIFIED → reject toàn bộ.

---

## 15. Future Evolution (QR Self Check-in — không đổi Aggregate)

Business xác nhận Trainer **không còn là actor duy nhất** tạo Attendance. Workflow tương lai:

```
Student → Scan QR → Registration verify → Workshop evaluate → Attendance ghi nhận
→ Trainer chỉnh sửa nếu cần → Auditor đối soát → Finalize
```

- **Epic 3A** — Trainer-driven attendance (hiện tại, đã thi công).
- **Epic 3B** — QR Self Check-in (use case mới, chưa thi công).
- **KHÔNG thay đổi Aggregate.** KHÔNG refactor `AttendanceRecord`. Chỉ bổ sung use case mới phía trên
  nguồn MARK đã có.
- **KHÔNG cần**: Event Sourcing, QR Aggregate, Attendance Session Aggregate. `AttendanceRecord` vẫn là
  aggregate duy nhất.

### 15.1. Future Integration Workshop ↔ Attendance

**Triển khai (Epic 3B — QR Self Check-in, Slice A):**

```
WorkshopExposeAPI
    └── evaluateCheckIn(UUID workshopId, Instant checkedInAt) → Optional<AttendanceStatusContract>
        └── AttendanceStatusContract → ATTENDED | LATE
```

- **`evaluateCheckIn`** (read-only, không lock, qua `WorkshopReader`): Workshop quyết định `ATTENDED` vs
  `LATE` theo policy của chính nó tại Application edge — **Workshop-side operational setting**
  `app.workshop.checkin.late-after-minutes` (mặc định 15), nạp qua `WorkshopCheckInParameters`
  (mẫu `WorkshopBufferParameters`, ADR 0018). Attendance **KHÔNG sở hữu policy** — chỉ consume kết quả.
- `AttendanceStatusContract` (enum `ATTENDED | LATE`) nằm ở `workshop/contract/` (ADR 0010) —
  contract dùng chung giữa Workshop và Attendance qua Module Facade, không thuộc `internal/`.
- `evaluateAttendance()` (đánh giá tổng thể sau workshop) vẫn là **future work**, không thuộc 3B.
- Attendance ghi nhận kết quả do Workshop xác định vào `AttendanceRecord`; QR check-in chỉ là nguồn
  MARK bổ sung (role `STUDENT`), không phải authoritative decision (ADR 0019 §5).