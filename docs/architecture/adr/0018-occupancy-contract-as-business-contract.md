# ADR 0018: System Buffer as an Operational Guardrail for Room Occupancy

* **Status**: ACCEPTED (Selective Occupancy Denormalization — REVISED v2, ratified by Lead Engineer)
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
| **`System Buffer`** | `bufferBefore` (phút) | **Operational Guardrail** thuộc Tầng Vận hành (Ops/DevOps) — dùng để chống đè lịch & rủi ro chuyển giao phòng. **Không phải** giờ chuẩn bị của Organizer. **Không phải cột DB.** |
| **Occupancy Window** | `[occupancy_start, end_time]` | Khung phòng bị giữ (reserved) trên thực tế cho mục đích lập lịch. Biên phải **không cần cột riêng** — `occupancy_end ≡ end_time` — nên chỉ denormalize thêm **duy nhất cột `occupancy_start`** (Selective Denormalization). |

Occupancy Window được tính bằng **một công thức thuần duy nhất (Pure Function/Stateless)** được dùng chung bởi mọi
thao tác lập lịch:

```
occupancy_start = startTime - currentConfigBuffer
```

trong đó `currentConfigBuffer` = `app.workshop.buffer.before-default-minutes` **đọc tại thời điểm thao tác**; biên phải
luôn là `end_time` (cột có sẵn).

**Quy tắc đồng bộ (ratified với Kỹ sư trưởng):**

| Thao tác | Cách tính `occupancy_start` | Sử dụng Config Buffer |
|---|---|---|
| **`CreateWorkshop`** | `startTime - currentConfigBuffer` | Đọc config hiện hành |
| **`UpdateSchedule` / `Reschedule`** | `newStartTime - currentConfigBuffer` | **Cập nhật theo config hiện hành** |
| **Workshop đứng yên** | Giữ nguyên `occupancy_start` trong DB | **Không bị ảnh hưởng** khi Ops đổi config |

Dời lịch là một **hành vi lập lịch mới có chủ đích** → lịch mới bắt buộc tuân theo Ops Guardrail tại thời điểm hiện tại.
Handler **không truy vết buffer cũ** (no `oldBuffer = Duration.between(oldOccStart, oldStartTime)` → trừ again) — triệt
tiêu hoàn toàn logic đọc trạng thái cũ; `Create` và `Reschedule` dùng chung đúng 1 công thức.

Số phút buffer khi cần hiển thị (UI) là **derived field** — không phải cột DB: `Duration.between(occupancy_start, start_time).toMinutes()`.

---

## 2. Scope & Boundary — System Guardrail

| Thành phần | Trạng thái | Mô tả Kỹ thuật |
|---|---|---|
| **Scope** | **System Guardrail** | Buffer được cấp từ `application.properties` (`app.workshop.buffer.before-default-minutes` — single knob), dùng **một lần duy nhất** trong công thức `occupancy_start = startTime - currentConfigBuffer` tại lúc lập lịch. Cột `buffer_before_minutes` **KHÔNG tồn tại** trong schema. |
| **ReBuffer Flow** | **REMOVED** | Xóa hoàn toàn `ReBufferWorkshopCommand` và `BufferJustification`. Không có use case "đàm phán lại" buffer ở từng workshop (đổi buffer toàn cục = đổi config; tác động qua lần lập lịch kế tiếp). |
| **Domain Model** | **Lean `Workshop`** | Chỉ giữ `startTime`, `endTime`, `occupancyStart`. Không VO nguồn `buffer_justification`, không lưu số phút buffer, không lưu `occupancyEnd`. |
| **UI/UX Responsibility** | **Presentation Layer** | UI render **vùng xám System Transition Buffer** để giải thích Planner vì sao slot không book được; số phút buffer là derived field (`Duration.between`). |

### 2.1 Bounded Contexts — tách bạch

| Bounded Context | Quản lý | Công thức | Phạm vi |
|---|---|---|---|
| **Facility/Workshop Context** | Không gian & Phòng vật lý | Occupancy Window = `[occupancy_start, end_time]` (chỉ `occupancy_start` denormalized) | **Task hiện tại** |
| **Attendance Context** | Con người & Điểm danh | Check-in/Check-out — quy tắc riêng, **độc lập** với System Buffer | **Epic 3 (tương lai)** — ghi nhận quan trọng, chưa phạm vi task này |

Attendance không phụ thuộc buffer: kể cả khi Organizer check-in sớm/điểm danh trong ngưỡng buffer, hệ thống điểm danh
có luật riêng của nó. Đây là ranh giới rõ để Epic 3 triển khai sau mà không rối với buffer.

---

## 3. Decision Drivers

- **Bảo lãnh kinh doanh (loyalty)**: buffer là guardrail (rail) không phải business contract. Thay đổi default 15→30 chỉ
  là thay config ở tầng vận hành; workshop đứng yên không bị đảo lộn (đảm bảo hợp đồng đã ký), không đụng domain logic.
- **Stateless — dời lịch áp dụng config hiện hành**: `Reschedule`/`UpdateSchedule` là **hành vi lập lịch mới có chủ đích**.
  Handler dùng **chung 1 công thức thuần** `newOccupancyStart = newStartTime - currentConfigBuffer` với `Create` — không
  truy vết buffer cũ, code tối giản, tính nhất quán vận hành (lịch mới tuân Guardrail hiện hành).
- **DB Native Query Pushdown**: vì biên phải `occupancy_end ≡ end_time`, chỉ cần denormalize thêm `occupancy_start`;
  overlap check trở thành predicate thuần trên cột, tận dụng **Composite B-Tree Index** `(room_id, occupancy_start, end_time)`
  — portable giữa H2/PostgreSQL, không còn superset `+300`, không còn bước lọc in-memory Java (thay cho ADR v1).
- **Giảm phức tạp & dữ liệu**: 1 cột denormalized duy nhất (`occupancy_start`), không cột trùng `occupancy_end`; bỏ
  `BufferJustification` + `ReBuffer` + bỏ hẳn cơ chế superset `STORAGE_CEILING=300` + filter in-memory → bớt command,
  handler, event, cột, cụm test, 0 bề mặt concurrency không cần.
- **Attendance độc lập**: Epic 3 không bị vướng buffer.

---

## 4. Decision Outline

### 4.1 Buffer Policy (Operational Config)

| | Key property | Default |
|---|---|---|
| Default buffer (tại lúc lập lịch) | `app.workshop.buffer.before-default-minutes` | `15` |

- **Single knob**: chỉ duy nhất 1 key — `before-default-minutes`. Không có `max-minutes`.
- Buffer **không phải cột DB** và **không được hint vào Aggregate** — nó được tiêu thụ ở biên Application (qua
  `WorkshopBufferParameters`) để nạp vào công thức thuần `occupancy_start = startTime - currentConfigBuffer`
  (đúng tinh thần ADR 0005 Revised: không inject policy vào domain).
- Không có hằng số storage ceiling 300 (v1) — cơ chế ấy bị thay bằng denormalization.
- **Không hardcode** default trong domain.

### 4.2 Selective Denormalization — Occupancy Window (Flyway V16)

Là thay đổi cốt lõi so với ADR v1 (Pure Normalization: lưu `buffer_before_minutes` + query superset `+300` + lọc
in-memory). Bản mới **đúng 1 cột denormalized** vì biên phải dùng thẳng `end_time`:

```sql
ALTER TABLE workshops ADD COLUMN occupancy_start TIMESTAMP WITH TIME ZONE NOT NULL;
CREATE INDEX idx_workshops_room_occupancy ON workshops (room_id, occupancy_start, end_time);
```

- `occupancy_start = startTime - currentConfigBuffer`, với `currentConfigBuffer` =
  `app.workshop.buffer.before-default-minutes` (nạp qua `WorkshopBufferParameters` ở Application) tại thời điểm lập lịch.
- Biên phải Occupancy = **`end_time`** — không tạo cột `occupancy_end` (dư thừa, `occupancy_end ≡ end_time`).
- **Công thức dùng chung (stateless)**: `CreateWorkshop`, `UpdateWorkshopSchedule` (đổi `start_time`),
  `RescheduleWorkshop` đều tính `occupancy_start = startTime - currentConfigBuffer`. Không đọc/truy vết buffer cũ.
- Workshop **đứng yên**: giữ nguyên `occupancy_start` trong DB — Ops đổi config không ảnh hưởng.
- **Trường suy ra (derived field)**: số phút buffer hiển thị (UI) = `Duration.between(occupancyStart, startTime).toMinutes()`.

### 4.3 Overlap Check (Room Occupancy Invariant) — DB Native Pushdown

Thuật toán kiểm tra trùng phòng (Đặt lịch / Đổi phòng / Xuất bản) **BẮT BUỘC** dựa trên cột `occupancy_start` + `end_time`:

```
Xung đột khi: other.end_time > target.occupancy_start AND other.occupancy_start < target.end_time
```

Vận dụng:

- Quy trình này là **global / set-based rule** → do Application orchestrate + **lock-set-first** (ADR 0005, ADR 0015);
  không inject query hay policy vào Aggregate.
- Predicate **Native/JPQL thuần trên cột** (không trừ `Instant - Integer` trong query), tận dụng composite index:

  ```sql
  WHERE room_id = :roomId
    AND state IN ('PUBLISHED', 'PLANNED')
    AND end_time > :targetOccStart
    AND occupancy_start < :targetOccEnd
  ```

  Trong đó `:targetOccStart`/`:targetOccEnd` là occupancy start & end_time của workshop target (đọc từ chính bản ghi);
  `PublishWorkshop` còn cần loại target khỏi tập kết quả.
- **Không còn** superset widen `+300`, **không còn** `STORAGE_CEILING`, **không còn** filter exact in-memory bằng
  `occupancyStart()/occupancyEnd()` — toàn bộ đẩy xuống DB (DB Query Pushdown, ADR 0016).
- JOOQ/JPQL read-side (`getByRoomAndTimeOverlap`, maintenance impact) dùng cùng predicate trên `OCCUPANCY_START`/`END_TIME`.

### 4.4 Use Case Scope (chốt mới)

| Use Case | Phase | Occupancy Window | Governance |
|---|---|---|---|
| `CreateWorkshop` | DRAFT | `occupancy_start = startTime - currentConfigBuffer`; biên phải = `end_time` | Config hiện hành → snapshot |
| `UpdateWorkshopSchedule` | DRAFT/PLANNED | **Re-tính** theo công thức chung nếu `start_time` đổi | Stateless (config hiện hành) |
| `RescheduleWorkshop` | PUBLISHED | **Re-tính** theo công thức chung cho `newStartTime` | Stateless (config hiện hành) |
| `ChangeWorkshopRoom` | PUBLISHED | Giữ nguyên `occupancy_start` (chỉ đổi room) | — |
| `PublishWorkshop` | → PUBLISHED | overlap check trên `occupancy_start`/`end_time` | lock-set-first |
| ~~ReBuffer~~ | **REMOVED** | — | — |

---

## 5. UI/UX Responsibility (System Transition Buffer)

- Presentation layer (query/view/Controller) render "vùng xám System Transition Buffer" trong lịch/biểu đồ phòng.
- Mục đích: giải thích cho Planner lý do 1 slot không book được (do `occupancy_start` của event khác giữ), **không phải**
  modal reject cứng — UI giữ vai trò giải thích & hướng dẫn.
- Số phút buffer suy ra từ cặp `(occupancy_start, start_time)` (`Duration.between`) — derived field, không phải cột.
- Phần này nằm ở read-side/view DTO, không đụng domain.

---

## 6. Consequences

**Positive**
- Overlap check là predicate **thuần native trên cột** → đơn giản, chính xác, tận dụng index B-Tree, portable H2/Postgres.
- **Stateless**: `Create`/`UpdateSchedule`/`Reschedule` dùng chung 1 pure function — không còn bước truy vết buffer cũ,
  giảm đáng kể code & test tại handler.
- Một cột denormalized duy nhất (`occupancy_start`) — DB tinh gọn, không dư thừa (`end_time` dùng thẳng làm biên phải).
- Không còn superset widen `+300` + lọc in-memory Java → giảm mạnh bề mặt code & test; không còn cơ hội false-positive/
  false-negative do lệch bound.
- Thỏa mãn bên vận hành: đổi buffer default chỉ đổi config, không restart domain logic — trả lời được email "linh hoạt hợp đồng".
- Không còn `BufferJustification`/`ReBuffer` → giảm mạnh bề mặt phức tạp, test, event.
- Workshop đứng yên bất biến → hợp đồng đã ký an toàn khi config đổi.
- Ranh giới Attendance (Epic 3) rõ ràng độc lập buffer.

**Negative**
- Denormalization mang tính dai dẳng dữ liệu: `UpdateSchedule`/`Reschedule`/đổi duration **phải** re-tính & cập nhật
  `occupancy_start` đồng bộ cùng `start_time` trong cùng TX (Application orchestration chịu trách nhiệm — nếu lệch sẽ
  sinh sai overlap).
- **Dời lịch đổi buffer theo config hiện hành**: một workshop từng được tạo với buffer 15 khi reschedule vào lúc config
  đã là 20 sẽ **âm thầm nhận buffer 20** — đây là quyết định Kinh doanh (lịch mới tuân Guardrail hiện hành), cần UI hiển
  thị đúng để Planner nhận biết.
- Không hỗ trợ đàm phán lại buffer trên từng workshop (nếu sau này business cần buffer riêng phải mở task riêng).

---

## 7. Compliance Checklist (Workshop module)

- [ ] `workshops` có đúng 1 cột denormalized `occupancy_start TIMESTAMPTZ NOT NULL` + index
      `idx_workshops_room_occupancy (room_id, occupancy_start, end_time)`. **Không** cột `occupancy_end`,
      **không** `buffer_before_minutes`, **không** `buffer_justification`, `buffer_after_minutes`, `scheduled_occupancy_*`,
      **không** hằng số `STORAGE_CEILING = 300`.
- [ ] `Workshop` domain giữ `startTime`, `endTime`, `occupancyStart`. Bỏ `BufferJustification`; không lưu `occupancyEnd`/buffer.
- [ ] Config chỉ 1 knob `app.workshop.buffer.before-default-minutes` nạp qua `WorkshopBufferParameters` (Application). **Không** `max-minutes`.
- [ ] `Create`/`UpdateSchedule`/`Reschedule` dùng **chung 1 công thức** `occupancy_start = startTime - currentConfigBuffer`
      (stateless — không truy vết buffer cũ); workshop đứng yên giữ nguyên cột.
- [ ] Overlap predicate (JPA lock + JOOQ read) là **Native/JPQL thuần trên cột** `occupancy_start`/`end_time` —
      **không** superset widen, **không** lọc exact in-memory, không phụ thuộc config runtime.
- [ ] Derived buffer hiển thị = `Duration.between(occupancyStart, startTime).toMinutes()` (Java), không phải cột DB.
- [ ] Không còn `ReBufferWorkshopCommand` / `BufferJustification` / event liên quan trong code & spec.
- [ ] UI read-side render "System Transition Buffer" zone (để sau, presentation-layer).

---

> **Ghi chú**: Đây là REVISED v2 theo khuyến nghị chốt hạ từ Bên Nghiệm thu + ratification của Kỹ sư trưởng:
> (1) chuyển từ **Pure Normalization** (lưu `buffer_before_minutes` + superset `+300` + lọc in-memory) sang
> **Selective Denormalization** lưu trực tiếp `occupancy_start`; (2) **bỏ cột `occupancy_end`** vì `occupancy_end ≡ end_time`;
> (3) **Reschedule/UpdateSchedule áp dụng config hiện hành** (stateless — cùng pure function với `Create`). Cơ chế v1
> (hằng số storage ceiling 300, widening superset, filter exact trong memory, cột `buffer_before_minutes`) hoàn toàn được
> thay thế và không còn giá trị triển khai.