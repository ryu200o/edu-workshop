# ADR 0018: System Buffer as an Operational Guardrail for Room Occupancy

* **Status**: ACCEPTED (Selective Occupancy Denormalization — REVISED v2)
* **Date**: 2026-08-10
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
| **Occupancy Window** | `[occupancy_start, occupancy_end]` | Khung phòng bị giữ (reserved) trên thực tế cho mục đích lập lịch. **Được denormalize trực tiếp thành 2 cột** trong bảng `workshops` (Selective Denormalization). |

Occupancy Window được tính **một lần tại thời điểm lập lịch** rồi được lưu trực tiếp:
`occupancy_start = startTime - bufferBefore` (bufferBefore = config tại lúc tạo), `occupancy_end = endTime`.
Buffer là **bất biến sau khi lập lịch** — khác với bản nháp cũ (có thể đàm phán lại). Số phút buffer **không được lưu
làm cột riêng**; khi cần hiển thị thì suy ra từ cặp occupancy: `Duration.between(occupancy_start, startTime).toMinutes()`.

---

## 2. Scope & Boundary — System Guardrail

| Thành phần | Trạng thái | Mô tả Kỹ thuật |
|---|---|---|
| **Scope** | **System Guardrail** | Buffer được cấp từ `application.properties` (`app.workshop.buffer.before-default-minutes` — single knob), dùng **đúng một lần** để tính `occupancy_start` lúc lập lịch rồi **denormalize** vào `occupancy_start`/`occupancy_end`. Cột `buffer_before_minutes` **KHÔNG tồn tại** trong schema. |
| **ReBuffer Flow** | **REMOVED** | Xóa hoàn toàn `ReBufferWorkshopCommand` và `BufferJustification`. Buffer là **bất biến sau khi lập lịch**. Không có use case "đàm phán lại" buffer. |
| **Domain Model** | **Lean `Workshop`** | Chỉ giữ `startTime`, `endTime`, `occupancyStart`, `occupancyEnd` (cặp occupancy tính tại create/reschedule). Không VO nguồn `buffer_justification`, không lưu số phút buffer. |
| **UI/UX Responsibility** | **Presentation Layer** | UI chịu trách nhiệm render **vùng xám System Transition Buffer** để giải thích cho Planner vì sao 1 phòng không book được trong khoảng xung đột, từ đó Planner đồng ý/hiểu rõ rule rồi chủ động xử lý. Giá trị phút buffer để hiển thị được suy ra bằng `Duration.between(occupancyStart, startTime)`. |

### 2.1 Bounded Contexts — tách bạch

| Bounded Context | Quản lý | Công thức | Phạm vi |
|---|---|---|---|
| **Facility/Workshop Context** | Không gian & Phòng vật lý | Occupancy Window = cặp `occupancy_start`/`occupancy_end` denormalized | **Task hiện tại** |
| **Attendance Context** | Con người & Điểm danh | Check-in/Check-out — quy tắc riêng, **độc lập** với System Buffer | **Epic 3 (tương lai)** — ghi nhận quan trọng, chưa phạm vi task này |

Attendance không phụ thuộc buffer: kể cả khi Organizer check-in sớm/điểm danh trong ngưỡng buffer, hệ thống điểm danh
có luật riêng của nó. Đây là ranh giới rõ để Epic 3 triển khai sau mà không rối với buffer.

---

## 3. Decision Drivers

- **Bảo lãnh kinh doanh (loyalty)**: buffer là guardrail (rail) không phải business contract. Thay đổi default 15→30 chỉ
  là thay config ở tầng vận hành, **không đụng domain logic**.
- **Bất biến sau lập lịch**: hợp đồng đã chốt (workshop được lịch với occupancy window C) không bị đảo lộn khi config
  toàn hệ thống đổi ngày mai. Denormalization snapshot bảo vệ hợp đồng với giảng viên/room hirer.
- **DB Native Query Pushdown**: vì occupancy window là **cột thật**, overlap check bắt buộc đè lịch phòng được đơn giản
  hoá 100% về predicate thuần `occupancy_end > :tOccStart AND occupancy_start < :tOccEnd`, tận dụng **Composite B-Tree
  Index** `(room_id, occupancy_start, occupancy_end)` — portable giữa H2/PostgreSQL, không còn superset `+300`, không còn
  bước lọc in-memory Java (thay cho ADR v1).
- **Giảm phức tạp**: Bỏ `BufferJustification` + `ReBuffer` + bỏ hẳn cơ chế superset `STORAGE_CEILING=300` + filter
  in-memory → bớt 1 command, 1 handler, 1 event, 1 cụm test, 0 bề mặt concurrency không cần.
- **Attendance độc lập**: Epic 3 không bị vướng buffer.

---

## 4. Decision Outline

### 4.1 Buffer Policy (Operational Config)

| | Key property | Default |
|---|---|---|
| Default buffer (tại lúc lập lịch) | `app.workshop.buffer.before-default-minutes` | `15` |

- **Single knob**: chỉ duy nhất 1 key — `before-default-minutes`. Không có `max-minutes`.
- Buffer **không phải cột DB**. Không có hằng số storage ceiling 300 (v1) — cơ chế ấy đã được thay bằng denormalization.
- Domain VO `WorkshopBuffer` (nếu còn) chỉ giữ invariant `beforeMinutes >= 0`.
- **Không hardcode** default trong domain.

### 4.2 Selective Denormalization — Occupancy Window (Flyway V16)

Là thay đổi cốt lõi so với ADR v1 (Pure Normalization: lưu `buffer_before_minutes` + query superset `+300` + lọc
in-memory). Bản mới **lưu trực tiếp cặp mốc thời gian Occupancy Window**:

```sql
ALTER TABLE workshops ADD COLUMN occupancy_start TIMESTAMP WITH TIME ZONE NOT NULL;
ALTER TABLE workshops ADD COLUMN occupancy_end   TIMESTAMP WITH TIME ZONE NOT NULL;
CREATE INDEX idx_workshops_room_occupancy ON workshops (room_id, occupancy_start, occupancy_end);
```

- `occupancy_start = start_time - bufferBefore` với `bufferBefore` = `app.workshop.buffer.before-default-minutes`
  nạp từ config (qua `WorkshopBufferParameters` ở Application) **tại thời điểm create / reschedule**.
- `occupancy_end = end_time`.
- **Trường suy ra (derived field)**: số phút buffer hiển thị tính ngược trên Java:
  `Duration.between(occupancyStart, startTime).toMinutes()` — không cần cột riêng.

### 4.3 Overlap Check (Room Occupancy Invariant) — DB Native Pushdown

Thuật toán kiểm tra trùng phòng (Đặt lịch / Đổi phòng / Xuất bản) **BẮT BUỘC** dựa trên **cột Occupancy Window**
denormalized, so với occupancy của các workshop khác:

```
Occupancy Window của bản ghi: [occupancy_start, occupancy_end]
Xung đột khi: targetOccEnd > :occStart AND targetOccStart < :occEnd  (= overlap 2 chiều)
```

Vận dụng:

- Quy trình này là **global / set-based rule** → do Application orchestrate + **lock-set-first** (ADR 0005, ADR 0015);
  không inject query hay policy vào Aggregate.
- Predicate **Native/JPQL thuần trên cột** (không trừ `Instant - Integer` trong query), tận dụng composite index:

  ```sql
  WHERE room_id = :roomId
    AND state IN ('PUBLISHED', 'PLANNED')
    AND occupancy_end > :targetOccStart
    AND occupancy_start < :targetOccEnd
  ```

  Trong đó `:targetOccStart`/`:targetOccEnd` là occupancy của workshop target (đọc từ chính bản ghi); `PublishWorkshop`
  còn cần loại target khỏi tập kết quả.
- **Không còn** superset widen `+300`, **không còn** `STORAGE_CEILING`, **không còn** lọc chính xác in-memory bằng
  `occupancyStart()/occupancyEnd()` — toàn bộ được đẩy xuống DB (DB Query Pushdown, ADR 0016).
- JOOQ/JPQL read-side (`getByRoomAndTimeOverlap`, maintenance impact) dùng cùng predicate trên `OCCUPANCY_START`/
  `OCCUPANCY_END`.

### 4.4 Use Case Scope (chốt mới)

| Use Case | Phase | Occupancy Window | Governance |
|---|---|---|---|
| `CreateWorkshop` | DRAFT | Tính `occupancy_start = startTime - configBuffer`, `occupancy_end = endTime`; snapshot vào bản ghi | Config → denormalize |
| `UpdateWorkshopSchedule` | DRAFT/PLANNED | Giữ nguyên (bất biến) | — |
| `RescheduleWorkshop` | PUBLISHED | **Re-tính lại** cặp occupancy theo buffer đang giữ, snapshot mới | Recompute + snapshot |
| `ChangeWorkshopRoom` | PUBLISHED | Giữ nguyên | — |
| `PublishWorkshop` | → PUBLISHED | overlap check trên cột occupancy (native predicate, lock-set-first) | lock-set-first |
| ~~ReBuffer~~ | **REMOVED** | — | — |

---

## 5. UI/UX Responsibility (System Transition Buffer)

- Presentation layer (query/view/Controller) render "vùng xám System Transition Buffer" trong lịch/biểu đồ phòng.
- Mục đích: giải thích cho Planner lý do 1 slot không book được (do bị giữ occupancy của event khác), **không phải**
  modal reject cứng — UI giữ vai trò giải thích & hướng dẫn.
- Số phút buffer suy ra từ cặp occupancy (`Duration.between`) — đây là derived field, không phải cột.
- Phần này nằm ở read-side/view DTO, không đụng domain.

---

## 6. Consequences

**Positive**
- Overlap check là predicate **thuần native trên cột** → đơn giản, chính xác, tận dụng index B-Tree, portable H2/Postgres.
- Không còn superset widen `+300` + lọc in-memory Java → giảm mạnh bề mặt code & test; không còn cơ hội false-positive/
  false-negative do lệch bound.
- Thỏa mãn bên vận hành: đổi buffer default chỉ đổi config, không restart domain logic — trả lời được email "linh hoạt hợp đồng".
- Không còn `BufferJustification`/`ReBuffer` → giảm mạnh bề mặt phức tạp, test, event.
- Immutable buffer → hợp đồng đã ký an toàn khi config đổi (denormalized snapshot).
- Ranh giới Attendance (Epic 3) rõ ràng độc lập buffer.

**Negative**
- Denormalization mang tính dai dẳng dữ liệu: khi `RescheduleWorkshop`/duration đổi, handler **phải** re-tính và cập nhật
  lại cặp `occupancy_start`/`occupancy_end` (Application orchestration chịu trách nhiệm, không để lệch).
- Không hỗ trợ đàm phán lại buffer, loại bỏ 1 use case thực tế (nếu sau này business cần đổi buffer cho workshop đang
  active thì phải mở 1 task riêng, không thuộc phạm vi buffer guardrail).

---

## 7. Compliance Checklist (Workshop module)

- [ ] `workshops` có cặp `occupancy_start TIMESTAMPTZ NOT NULL` / `occupancy_end TIMESTAMPTZ NOT NULL` + index
      `idx_workshops_room_occupancy (room_id, occupancy_start, occupancy_end)`. **Không** cột `buffer_before_minutes`,
      **không** `buffer_justification`, `buffer_after_minutes`, `scheduled_occupancy_*`, **không** hằng số `STORAGE_CEILING = 300`.
- [ ] `Workshop` domain giữ `startTime`, `endTime`, `occupancyStart`, `occupancyEnd`; occupancy tính tại `create`/
      `reschedule`. Bỏ `BufferJustification`. (VO `WorkshopBuffer` nếu còn chỉ giữ invariant `>= 0`.)
- [ ] Config chỉ 1 knob `app.workshop.buffer.before-default-minutes` nạp qua `WorkshopBufferParameters` (Application). **Không** `max-minutes`.
- [ ] Overlap predicate (JPA lock + JOOQ read) là **Native/JPQL thuần trên cột** `occupancy_start`/`occupancy_end` —
      **không** superset widen, **không** lọc exact in-memory, không phụ thuộc config runtime.
- [ ] Derived buffer hiển thị = `Duration.between(occupancyStart, startTime).toMinutes()` (Java), không phải cột DB.
- [ ] Không còn `ReBufferWorkshopCommand` / `BufferJustification` / event liên quan trong code & spec.
- [ ] UI read-side render "System Transition Buffer" zone (để sau, presentation-layer).

---

> **Ghi chú**: Đây là REVISED v2 theo khuyến nghị chốt hạ từ Bên Nghiệm thu — chuyển từ **Pure Normalization**
> (lưu `buffer_before_minutes` + superset `+300` + lọc in-memory) sang **Selective Denormalization** (lưu trực tiếp cặp
> `occupancy_start`/`occupancy_end`). Cơ chế v1 (hằng số storage ceiling 300, widening superset, filter exact trong memory)
> hoàn toàn được thay thế và không còn giá trị triển khai.