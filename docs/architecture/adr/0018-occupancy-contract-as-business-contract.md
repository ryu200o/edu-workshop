# ADR 0018: System Buffer as an Operational Guardrail for Room Occupancy

* **Status**: ACCEPTED (Lean & Clean — REVISED)
* **Date**: 2026-08-08
* **Deciders**: Lead Engineer, Solution Architect (SA), Business/Nghiệm thu
* **Technical Domain**: `Workshop` Aggregate, Room Scheduling, Time Modeling (Occupancy Window), Operational Configuration, Cross-Module Contracts (`workshop` → `room` / `facilityops`)
* **Related**: ADR 0001 (Room static vs temporal state), ADR 0005 (Application orchestration of global rules), ADR 0007 (Selective Snapshotting), ADR 0008 (Planning vs Reservation), ADR 0015 (Concurrency), ADR 0016 (Port/ExposeAPI naming & DB pushdown), ADR 0017 (Task-Tailored Views), Spec v3 (`.llm/epic1_extension_gap_time_spec.md`)

---

## 1. Ubiquitous Language & Core Invariant

> **"The platform schedules room occupancy, not human activities."**
> Nền tảng quản lý việc **chiếm dụng phòng vật lý**, không quản lý cách con người sử dụng thời gian trong phòng.

Từ bất biến này, mọi thời lượng setup, khai mạc, giảng dạy, networking hoặc dọn dẹp của Organizer đều là trách nhiệm
nội bộ của Organizer và **nằm trọn trong** khung `[startTime, endTime]` đã đặt. Hệ thống không biết (và không cần biết)
chúng chi tiết ra sao.

### 1.1 Tái định nghĩa Cửa sổ Thời gian

| Thuật ngữ | Định nghĩa mới | Ghi chú |
|---|---|---|
| **Window** | `[startTime, endTime]` | Trung lập (neutral), thay cho khái niệm `Teaching Window` gượng ép. |
| **`System Buffer`** | `bufferBefore` (phút) | **Operational Guardrail** thuộc Tầng Vận hành (Ops/DevOps) — dùng để chống đè lịch & rủi ro chuyển giao phòng. **Không phải** giờ chuẩn bị của Organizer. |
| **Occupancy Window** | `[startTime - bufferBefore, endTime]` | Khung phòng bị giữ (reserved) trên thực tế cho mục đích lập lịch. |

Buffer là **bất biến sau khi lập lịch** — khác với bản nháp cũ (có thể đàm phán lại). Một khi Workshop đã được tạo với
một buffer cụ thể, giá trị đó được chốt snapshot vào bản ghi và không bao giờ thay đổi bởi hệ thống.

---

## 2. Scope & Boundary — System Guardrail

| Thành phần | Trạng thái | Mô tả Kỹ thuật |
|---|---|---|
| **Scope** | **System Guardrail** | Buffer được cấp từ `application.properties` (`app.workshop.buffer.before-default-minutes`, `app.workshop.buffer.max-minutes`), đóng dấu **snapshot** vào DB (`buffer_before_minutes`). |
| **ReBuffer Flow** | **REMOVED** | Xóa hoàn toàn `ReBufferWorkshopCommand` và `BufferJustification`. Buffer là **bất biến sau khi lập lịch**. Không có use case "đàm phán lại" buffer. |
| **Domain Model** | **Lean `Workshop`** | Chỉ giữ `startTime`, `endTime` và `WorkshopBuffer` (validate `beforeMinutes >= 0`). Không VO nguồn `buffer_justification`. |
| **UI/UX Responsibility** | **Presentation Layer** | UI chịu trách nhiệm render **vùng xám System Transition Buffer** để giải thích cho Planner vì sao 1 phòng không book được trong khoảng xung đột, từ đó Planner đồng ý/hiểu rõ rule rồi chủ động xử lý. |

### 2.1 Bounded Contexts — tách bạch

| Bounded Context | Quản lý | Công thức | Phạm vi |
|---|---|---|---|
| **Facility/Workshop Context** | Không gian & Phòng vật lý | `Occupancy Window = [startTime - bufferBefore, endTime]` | **Task hiện tại** |
| **Attendance Context** | Con người & Điểm danh | Check-in/Check-out — quy tắc riêng, **độc lập** với System Buffer | **Epic 3 (tương lai)** — ghi nhận quan trọng, chưa phạm vi task này |

Attendance không phụ thuộc buffer: kể cả khi Organizer check-in sớm/điểm danh trong ngưỡng buffer, hệ thống điểm danh
có luật riêng của nó. Đây là ranh giới rõ để Epic 3 triển khai sau mà không rối với buffer.

---

## 3. Decision Drivers

- **Bảo lãnh kinh doanh (loyalty)**: buffer là guardrail (rail) không phải business contract. Thay đổi default 15→30 hay
  trần 60→45/120 chỉ là thay config ở tầng vận hành, **không đụng domain logic**.
- **Bất biến sau lập lịch**: hợp đồng đã chốt (workshop được lịch với buffer C) không bị đảo lộn khi config toàn hệ thống
  đổi ngày mai. Snapshot bảo vệ hợp đồng với giảng viên/room hirer.
- **Giảm phức tạp**: Bỏ `BufferJustification` + `ReBuffer` → bớt 1 command, 1 handler, 1 event, 1 cụm test, 0 bề mặt concurrency
  không cần.
- **Tính toán runtime**: occupancy window là hàm `(startTime, endTime, bufferBefore)` — luôn tính được, không cần cột
  denormalized (ADR 0001).
- **Attendance độc lập**: Epic 3 không bị vướng buffer.

---

## 4. Decision Outline

### 4.1 Buffer Policy (Operational Config)

| | Key property | Default |
|---|---|---|
| Default buffer (snapshot) | `app.workshop.buffer.before-default-minutes` | `15` |
| Max cap | `app.workshop.buffer.max-minutes` | `60` |

- Domain VO `WorkshopBuffer` chỉ giữ invariant `beforeMinutes >= 0`.
- Cap max là Operational rule đặt tại Application handler; giá trị > max → `InvalidBufferSizeException` (HTTP 400/422),
  đổi cap không cần migration.
- **Không hardcode** default trong domain.

### 4.2 Snapshot tại lập lịch

- Khi tạo/phân phối Workshop, `buffer_before_minutes` snapshot từ config hiện hành trực tiếp vào bản ghi.
- Sau khi config thay đổi: Workshop mới lấy buffer mới, Workshop đã tồn tại giữ buffer cũ (immutability).

### 4.3 Overlap Check (Room Occupancy Invariant)

Thuật toán kiểm tra trùng phòng (Đặt lịch / Đổi phòng / Xuất bản) **BẮT BUỘC** dựa trên **Occupancy Window**
`[target = startTime - bufferBefore, endTime]` của target so với các workshop khác.

Hai đối tượng A, B cùng phòng xung đột khi:
```
(S_A - B_before_A) < E_B AND E_A > (S_B - B_before_B)
```

Vận dụng:

- Quy trình này là **global / set-based rule** → do Application orchestrate + **lock-set-first** (ADR 0005, ADR 0015);
  không inject query hay policy vào Aggregate.
- JPQL/Hibernate không trừ được `Instant - Integer` portable → gọi method hiện có
  `loadPublishedAndPlannedOverlappingWithLock(roomId, startTime, endTime)` với window **bảo thủ superset**
  `[targetStart - maxBuffer, targetEnd + maxBuffer]`, rồi **lọc chính xác trong memory** bằng
  `occupancyStart()/occupancyEnd()` của từng bản ghi (dựa trên `BUFFER_BEFORE_MINUTES` snapshot, độc lập với config runtime).
- JOOQ read-side (`getByRoomAndTimeOverlap`) áp dụng cùng kỹ thuật: dùng `BUFFER_BEFORE_MINUTES` từ DB làm predicate
  (pushed-down), không phụ thuộc config.

### 4.4 Use Case Scope (chốt mới)

| Use Case | Phase | Buffer | Governance |
|---|---|---|---|
| `CreateWorkshop` | DRAFT | Optional truyền trực tiếp; nếu null → snapshot default | Config → snapshot |
| `UpdateWorkshopSchedule` | DRAFT/PLANNED | Giữ nguyên (bất biến) | — |
| `RescheduleWorkshop` | PUBLISHED | Giữ nguyên | — |
| `ChangeWorkshopRoom` | PUBLISHED | Giữ nguyên | — |
| `PublishWorkshop` | → PUBLISHED | overlap check trên occupancy window | lock-set-first |
| ~~ReBuffer~~ | **REMOVED** | — | — |

---

## 5. UI/UX Responsibility (System Transition Buffer)

- Presentation layer (query/view/Controller) render "vùng xám System Transition Buffer" trong lịch/biểu đồ phòng.
- Mục đích: giải thích cho Planner lý do 1 slot không book được (do bị giữ buffer của event khác), **không phải**
  modal reject cứng — UI giữ vai trò giải thích & hướng dẫn.
- Phần này nằm ở read-side/view DTO, không đụng domain.

---

## 6. Consequences

**Positive**
- Thỏa mãn bên vận hành: đổi buffer default/trần chỉ đổi config, không restart domain logic — trả lời được email "linh hoạt hợp đồng".
- Không còn `BufferJustification`/`ReBuffer` → giảm mạnh bề mặt phức tạp, test, event.
- Immutable buffer → hợp đồng đã ký an toàn khi config đổi.
- Ranh giới Attendance (Epic 3) rõ ràng độc lập buffer.

**Negative**
- Không hỗ trợ đàm phán lại buffer, loại bỏ 1 use case thực tế (nếu sau này business cần đổi buffer cho workshop đang
  active thì phải mở 1 task riêng, không phải là lãnh từ buffer guardrail).

---

## 7. Compliance Checklist (Workshop module)

- [ ] `workshops` có cột `buffer_before_minutes INT NOT NULL DEFAULT 15` (snapshot). **Không** `buffer_justification`, `buffer_after_minutes`, `scheduled_occupancy_*`.
- [ ] `WorkshopBuffer` domain VO: chỉ `beforeMinutes`, invariant `>= 0`, **không** `DEFAULT` hardcode. Bỏ `BufferJustification`.
- [ ] Config `app.workshop.buffer.before-default-minutes` / `max-minutes` nạp qua `WorkshopBufferParameters` (Application).
- [ ] Overlap predicate (JPA lock + JOOQ read) push-down bằng `BUFFER_BEFORE_MINUTES` + runtime superset, rồi lọc chính xác trong memory, không phụ thuộc config runtime (đọc snapshot từng row).
- [ ] Không còn `ReBufferWorkshopCommand` / `BufferJustification` / event liên quan trong code & spec.
- [ ] UI read-side render "System Transition Buffer" zone (để sau, presentation-layer).

---

> **Ghi chú**: Đây là REVISED version theo quyết định buổi họp (Lead Engineer + SA + Business). Bản nháp cũ
> (double-sided buffer, justification, scheduled_occupancy_*) hoàn toàn được thay thế và không còn giá trị triển khai.