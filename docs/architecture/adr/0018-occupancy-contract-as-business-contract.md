# ADR 0018: Occupancy Contract as a Business Contract

* **Status**: ACCEPTED
* **Date**: 2026-08-07
* **Deciders**: Solution Architect (SA), Lead Engineer
* **Technical Domain**: `Workshop` Aggregate, Room Scheduling, Time Modeling (Teaching / Occupancy / Presence Windows), Buffer Time Policy, Cross-Module Contracts (`workshop` $\rightarrow$ `room` / `facilityops` / `registration`)
* **Related**: ADR 0001 (Room static vs temporal state), ADR 0005 (Application orchestration of global rules), ADR 0007 (Selective Snapshotting), ADR 0008 (Planning vs Reservation), ADR 0015 (Concurrency), ADR 0016 (Port/ExposeAPI naming & DB pushdown), ADR 0017 (Task-Tailored Views), `.llm/epic1_extension_gap_time_spec.md`

---

## 1. Context & Problem Statement

Trong vận hành thực tế, xếp lịch hai bài học **sát giờ nhau** trong cùng một phòng mà không có khoảng nghỉ
(Gap Time) gây ra hai điểm gãy nghiệp vụ: (1) **va chạm không gian** — học viên lớp trước chưa kịp ra, lớp sau
đã tràn vào, giảng viên không có thời gian chuẩn bị/vệ sinh; (2) **sai lệch khung điểm danh (Epic 3)** — khung
giờ chuẩn bị/mở cửa bị đè lên khung giờ kết thúc của bài trước.

Khảo sát codebase cho thấy hệ thống chỉ có **một** khái niệm thời gian duy nhất (`start_time` / `end_time` — khung
giờ giảng dạy). Mọi thuật toán trùng phòng (lock-set-first khi Publish/Reschedule/ChangeRoom), eviction bảo trì,
và impact analysis đều so sánh trên khung giảng dạy — trong khi thực tế phòng bị **giữ (occupancy)** rộng hơn
khung giảng dạy một khoảng chuẩn bị trước và dọn dẹp sau.

ADR này khai báo nền tảng kiến trúc cho việc **nâng Occupancy lên thành một Business Contract** — một khái niệm
miền có thời hạn, có lịch sử quyết định, và có khả năng đàm phán lại — thay vì chỉ là một cặp cột thời gian
kỹ thuật. Nó là tài liệu nguồn cho Spec Epic 1 Extension (Buffer Time & Room Occupancy Window).

---

## 2. Decision Drivers

* **Tách bạch trách nhiệm thời gian (Separation of Time Concerns)**: Teaching (học thuật), Scheduled Occupancy
  (điều phối cơ sở vật chất), và Actual Presence (điểm danh) phục vụ ba Business Capability khác nhau; trộn chúng
  vào một cặp cột là nguồn gốc của các lỗi lịch trình.
* **Tính lịch sử của quyết định nghiệp vụ**: Lịch đã ký kết là một cam kết. Việc "thay đổi chính sách phòng trong
  tương lai tự động làm biến dạng lịch đã chốt" phá hủy tính ổn định mà Planner dựa vào.
* **Aggregate purity (ADR 0005)**: Các ràng buộc set-based (trùng phòng) và policy (min/max buffer) thuộc
  Application; aggregate giữ local invariant và derived behavior.
* **Độ khả chuyển DDL (H2/PostgreSQL)**: Overlap predicate phải đơn giản, không dựa vào hàm interval khác nhau
  giữa các DB (ADR 0012, ADR 0015).
* **Điểm mở rộng (Extension Points)**: Model phải cho phép bổ sung `reasonCode`, `approvedBy`, `policyRef` sau này
  mà không phá vỡ hợp đồng.

---

## 3. Decision Outline

### Principle 1 — Occupancy Contract as a Unified Model

`Occupancy Contract` là một **khái niệm miền (domain concept)** — không phải một cặp cột kỹ thuật — bao gồm:

| Thành phần | Mô tả | Ghi chú |
|---|---|---|
| **Room** | Phòng bị chiếm dụng | logical `room_id` + snapshot (ADR 0007) |
| **Teaching Window** | `[S, E]` — bài học diễn ra với học viên | `start_time`, `end_time` |
| **Scheduled Occupancy Window** | `[S - B_before, E + B_after]` — phòng bị giữ (setup/cleanup) | cột `scheduled_occupancy_*` |
| **Effective Buffer Snapshot** | `B_before`, `B_after` đã được quyết định (phút) | `buffer_before_minutes`, `buffer_after_minutes` |
| **Reservation Strength** | derived method: `PLANNED` $\rightarrow$ `SOFT`, `PUBLISHED` $\rightarrow$ `HARD` | chưa phải VO riêng (xem §3.5) |
| **Contract Terms** | các điều khoản thỏa thuận giữa Planner và Facility | gồm `AdjustmentJustification`, ... |

**Hệ quả kiến trúc:**

* Aggregator duy nhất sở hữu contract là **`Workshop`** (module `workshop`). Không module nào khác tự ý thay đổi nó.
* Overlap predicate (lock-set-first, eviction, read impact) đều so sánh trên **Scheduled Occupancy Window** — không
  dùng Teaching Window đơn thuần.
* Room giữ nguyên tinh thần ADR 0001: không lưu trạng thái temporal availability; availability vẫn được tính runtime.

### Principle 2 — Snapshot & Business History Protection

`Occupancy Contract` được **snapshot tại thời điểm lập lịch** (`Plan` / `Schedule` / `Reschedule`). Snapshot này:

1. **Bảo vệ tính ổn định dữ liệu** (data stability): read side không cần cross-module JOIN để render lịch.
2. **Bảo vệ tính lịch sử của quyết định nghiệp vụ** (decision history): giá trị buffer, room snapshot, và
   teaching window ghi lại **quyết định đã ký kết ngày hôm đó**. Khi `Room Policy` thay đổi trong tương lai
   (đổi room capacity, đổi policy buffer), contract **KHÔNG tự động biến đổi** — nó chỉ thay đổi qua một
   hành động đàm phán lại tường minh (xem Principle 4).

**Hệ quả kiến trúc:**

* Không có cơ chế "recompute on policy change". Nếu Policy đổi và muốn áp dụng, hệ thống phải dẫn tới một
  `RescheduleWorkshop` (hoặc hành động tương đương) — giữ vết kiểm toán.
* Cột derived (`scheduled_occupancy_*`) được ghi **một cách nhất quán tại mọi mutation lịch** — đúng tinh thần
  selective denormalization (ADR 0007).

### Principle 3 — Triple-Window & Business Capabilities

Phân biệt **3 cửa sổ thời gian** gắn liền với **3 Business Capabilities khác nhau**:

| Cửa sổ | Định nghĩa | Business Capability | Ghi chú lưu trữ |
|---|---|---|---|
| **Teaching Window** | `[S, E]` | Academic Planning (lập kế hoạch giảng dạy; start/complete guard, điểm danh) | `start_time`, `end_time` |
| **Scheduled Occupancy Window** | `[S - B_before, E + B_after]` | Facility Coordination (điều phối phòng; overlap, eviction, maintenance impact) | `scheduled_occupancy_start`, `scheduled_occupancy_end` |
| **Actual Presence Window** | khung giờ thực tế học viên có mặt (Epic 3) | Attendance / check-in | chưa có; Epic 3 |

**Hệ quả kiến trúc:**

* Mỗi luồng logic dùng đúng cửa sổ của capability nó phục vụ — không bao giờ nhầm lẫn.
* `start()` / `complete()` guard và `RescheduleDeadline` vẫn dùng **Teaching Window** (cảm nhận của người dùng).
* Overlap / eviction / impact dùng **Scheduled Occupancy Window**.
* Epic 3 (Actual Presence) sẽ dựng khung điểm danh từ `occupancyStart()` như tiền đề, chưa triển khai ở đây.

### Principle 4 — Contract Immutability (Negotiation, not Metadata Editing)

Sau khi `PUBLISHED`, **mọi thay đổi** đối với các điều khoản của `Occupancy Contract`:

* đổi teaching window (`reschedule`), đổi room (`changeRoom`), đổi capacity (`adjustCapacity`),
* đổi buffer (`B_before`/`B_after` — nếu cho phép ở PUBLISHED),

đều được coi là một hành vi **đàm phán lại hợp đồng** (`RescheduleWorkshop`), **không phải chỉnh sửa metadata**.
Mỗi lần đàm phán lại:
* phải chạy lại **Room Overlap Invariant** trên Scheduled Occupancy Window mới (lock-set-first, ADR 0015);
* phải ghi **`AdjustmentJustification`** (lý do do Planner cung cấp) vào Contract Terms;
* phải phát sinh domain/integration event để các bên liên quan (Registration snapshot, maintenance eviction) đồng bộ.

**Hệ quả kiến trúc:**

* Trạng thái mặc định khuyến nghị: **buffer immutable ở `PUBLISHED`** (đổi buffer phải qua `reschedule` với conflict
  check) — tránh đàm phán lại ngầm không có kiểm toán.
* Không tồn tại "edit metadata" ẩn; mọi thay đổi điều khoản đều có vết (event + justification).

### 3.5 Reservation Strength — derived method (chưa thành VO)

`reservationStrength()` giữ dưới dạng **derived method trên `Workshop`**:

```java
public ReservationStrength reservationStrength() {
    return switch (state) {
        case PLANNED   -> ReservationStrength.SOFT;
        case PUBLISHED -> ReservationStrength.HARD;
        default        -> throw new IllegalStateException("No reservation strength outside PLANNED/PUBLISHED: " + state);
    };
}
```

* `SOFT` (planning, non-exclusive — ADR 0008) vs `HARD` (reservation, exclusive).
* **Chưa nâng cấp thành VO riêng** cho đến khi xuất hiện nghiệp vụ phức tạp hơn (ví dụ: priority tiers, overbook).
* Ghi chú: `ReservationStrength` có thể khai báo như enum trong `Workshop` (hoặc package cùng aggregate).

---

## 4. Technical & Database Impacts

1. **Schema (`workshops`, V16 mới — tham chiếu Spec v2 §6):**
   * `buffer_before_minutes INT NOT NULL DEFAULT 0`
   * `buffer_after_minutes INT NOT NULL DEFAULT 0`
   * `scheduled_occupancy_start TIMESTAMPTZ`
   * `scheduled_occupancy_end TIMESTAMPTZ`
   * CHECK: `scheduled_occupancy_end IS NULL OR scheduled_occupancy_end > scheduled_occupancy_start`
   * Index: composite `(room_id, scheduled_occupancy_start, scheduled_occupancy_end)` cho overlap scan.
2. **JPA entity / JOOQ codegen:** thêm mapping cho buffer + 2 cột occupancy.
3. **Overlap JPQL (2 query)** và **JOOQ read** chuyển predicate sang `scheduled_occupancy_*`.
4. **Application config:** `app.workshop.buffer.*` (default + min/max) — **Operational Policy**, không phải
   Domain Invariant (xem Spec v2 §5.1).
5. **Domain:** VO `WorkshopBuffer`, `AdjustmentJustification`, field `buffer` + derived `occupancyStart()/occupancyEnd()/reservationStrength()` trên `Workshop`.

---

## 5. Consequences & Trade-offs

### Positive

* **Đúng bản chất nghiệp vụ**: Occupancy là hợp đồng (có điều khoản, có lịch sử, có đàm phán lại), không phải
  cặp cột kỹ thuật — dễ trao đổi với business, dễ kiểm toán.
* **Ổn định cam kết**: Snapshot bảo vệ lịch đã chốt khỏi thay đổi policy trong tương lai — Planner tin cậy.
* **Tách capability rõ ràng**: mỗi cửa sổ phục vụ đúng mục đích, tránh tái phạm lỗi "trùng phòng tính theo
  teaching window".
* **Điểm mở rộng**: `AdjustmentJustification` cho phép bổ sung `reasonCode/approvedBy/policyRef` sau.

### Negative / Trade-offs

* **Nhiều cột hơn + migration**: chấp nhận vì đánh đổi lấy DB pushdown và tính rõ ràng.
* **Phức tạp hoá ban đầu**: khái niệm hợp đồng + 3 cửa sổ + snapshot đòi hỏi discipline khi thi công.
* **Đàm phán lại có chi phí**: mọi thay đổi POST-PUBLISH đều chạy lại overlap + ghi justification + phát event —
  đúng nhưng nặng hơn "edit metadata".

---

## 6. Compliance Checklist

1. Overlap/eviction/impact đều so sánh trên `scheduled_occupancy_*`, không dùng teaching đơn thuần.
2. Contract chỉ được sở hữu bởi `Workshop`; thay đổi post-PUBLISH đều qua hành vi đàm phán lại (không edit metadata).
3. Buffer default/min/max là `app.workshop.buffer.*` (Operational Policy); VO chỉ giữ invariant không âm.
4. 3 cửa sổ gắn đúng 3 capability; start/complete/reschedule-deadline dùng Teaching, overlap/evict dùng Occupancy.
5. `AdjustmentJustification` thay thế string `reason` trong Contract Terms.

---

*Bổ sung cho ADR 0008 (Planning vs Reservation) và ADR 0007 (Snapshotting); nền tảng cho Spec Epic 1 Extension v2.
Không supersede ADR nào.*
