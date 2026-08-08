# ADR 0018: Occupancy Contract as a Business Contract — Lean Model (Single-Sided Buffer)

* **Status**: ACCEPTED
* **Date**: 2026-08-08
* **Deciders**: Solution Architect (SA), Lead Engineer
* **Technical Domain**: `Workshop` Aggregate, Room Scheduling, Time Modeling (Teaching / Occupancy / Presence Windows), Buffer Time Policy, Cross-Module Contracts (`workshop` → `room` / `facilityops` / `registration`)
* **Related**: ADR 0001 (Room static vs temporal state), ADR 0005 (Application orchestration of global rules), ADR 0007 (Selective Snapshotting), ADR 0008 (Planning vs Reservation), ADR 0015 (Concurrency), ADR 0016 (Port/ExposeAPI naming & DB pushdown), ADR 0017 (Task-Tailored Views), Spec v2 (`.llm/epic1_extension_gap_time_spec.md`)

---

## 1. Context & Problem Statement

Khi xếp lịch hai bài học **sát giờ nhau** trong **cùng một phòng** mà không có khoảng nghỉ (Gap Time), vận hành
thực tế gặp hai điểm gãy:

1. **Va chạm không gian** — học viên lớp trước chưa kịp ra, lớp sau đã tràn vào; giảng viên không có thời gian chuẩn bị/vệ sinh.
2. **Sai lệch khung điểm danh (Epic 3)** — khung giờ chuẩn bị/mở cửa bị đè lên khung giờ kết thúc của bài trước.

Hệ thống hiện chỉ có **một** khái niệm thời gian (`start_time` / `end_time` — khung giờ giảng dạy). Mọi thuật toán
trùng phòng (lock-set-first khi Publish/Reschedule/ChangeRoom), eviction bảo trì, và impact analysis đều so sánh trên
khung giảng dạy — trong khi phòng thực tế bị **giữ (occupancy)** rộng hơn khung giảng dạy một khoảng chuẩn bị.

Đây là một **Business Contract**: khái niệm miền có thời hạn, có lịch sử quyết định, và có khả năng đàm phán lại —
không phải chỉ là một cặp cột thời gian kỹ thuật.

### Revision: Lean Model (Single-Sided Buffer)

Bản sửa đổi này **rút gọn** bản nháp cũ (double-sided buffer `B_before` + `B_after`, có cột denormalized
`scheduled_occupancy_*`) thành **single-sided buffer**:

| | Bản nháp cũ (bỏ) | **LEAN (chốt)** |
|---|---|---|
| Buffer | `B_before` + `B_after` | **chỉ** `B_before` (chuẩn bị trước giờ: setup vệ sinh, chuẩn bị) |
| Occupancy Window | `[S - B_before, E + B_after]` | `[S - B_before, E]` (trùng end giảng dạy) |
| Lưu trữ | 4 cột (`buffer_after_minutes`, `scheduled_occupancy_start`, `scheduled_occupancy_end`, ...) | **2 cột** (`buffer_before_minutes`, `buffer_justification`) |
| Scheduled Occupancy | cột denormalized | **tính runtime** tại Application/Persistence |

Lý do Lean: buffer *sau* giờ học (dọn dẹp) chưa có nhu cầu nghiệp vụ ở Epic 1; cột denormalized tạo nguy cơ
sai lệch trạng thái (rủi ro khớp với ADR 0001 — trạng thái temporal được tính runtime, không persist). Khi Epic 3
cần cleanup thực sự, mới mở rộng single-sided → double-sided.

---

## 2. Decision Drivers

- **Không persist trạng thái temporal** (ADR 0001): occupancy window `[S-B, E]` là hàm của (start_time, end_time, buffer) — luôn tính được, không cần lưu.
- **Đơn giản**: 2 cột thay vì 4; một khái niệm buffer thay vì hai; migration nhẹ.
- **Buffer là Business Contract**: như trên, có lý do đàm phán (`BufferJustification`) và được điều chỉnh bằng use case riêng (`ReBufferWorkshop`).
- **Buffer policy là Operational Policy thuộc Application** (giới hạn max nạp từ config, đổi cap không cần migration) — phù hợp ADR 0005 (Application orchestration).
- **Khả năng mở rộng**: single → double sided kỹ thuật là foreseeable, khi Epic 3 làm Actual Presence/cleanup thật.

---

## 3. Decision Outline

### 3.1 Lean Occupancy Contract (Single-Sided)

| Thành phần | Mô tả | Lưu trữ |
|---|---|---|
| Room | phòng bị chiếm dụng | logical `room_id` + snapshot (ADR 0007) |
| Teaching Window | `[S, E]` | `start_time`, `end_time` (đã có) |
| Scheduled Occupancy Window | `[S - B_before, E]` | **tính runtime**, không lưu cột |
| Buffer | `B_before` (phút) | `buffer_before_minutes` |
| Contract Terms | Lý do đàm phán | `buffer_justification` |

**Công thức:**
```
occupancyStart() = start_time - buffer_before_minutes
occupancyEnd()   = end_time   (trùng teaching end)
```

### 3.2 BufferJustification — Contract Terms

`BufferJustification` chỉ kích hoạt khi Planner **đàm phán lại buffer**:

- `buffer_before_minutes` khác default (`15`) khi tạo → cần `justification`.
- `ReBufferWorkshop` (PUBLISHED) → luôn bắt buộc `justification` khác `"DEFAULT"`.
- `RescheduleWorkshop` / `UpdateWorkshopSchedule` → **không đổi buffer**, không cần `justification`.

### 3.3 Operational Policy vs Domain Invariant

| Ràng buộc | Layer | Loại |
|---|---|---|
| `buffer_before_minutes >= 0` | Domain VO `WorkshopBuffer` | local invariant |
| `buffer_before_minutes <= max` | **Application** → `InvalidBufferSizeException` (400/422) | Operational Policy (đổi cap không cần migration) |

Max nạp từ `app.workshop.buffer.max-minutes` (mặc định 60); default nạp từ `app.workshop.buffer.before-default-minutes` (mặc định 15).

### 3.4 Use Case Scope

| Use Case | Phase | Buffer | Justification |
|---|---|---|---|
| `CreateWorkshop` | DRAFT | Optional (default 15) | Optional (default "DEFAULT") |
| `UpdateWorkshopSchedule` | DRAFT/PLANNED | Giữ nguyên | Không cần |
| `ReBufferWorkshop` (mới) | PUBLISHED | **Bắt buộc thay đổi** | **Bắt buộc** |
| `RescheduleWorkshop` | PUBLISHED | Giữ nguyên | Không cần |
| `ChangeWorkshopRoom` | PUBLISHED | Giữ nguyên | Không cần |
| `AdjustWorkshopCapacity` | PUBLISHED | Không liên quan | Không cần |
| `plan()` | DRAFT→PLANNED | Giữ nguyên | Không cần |

### 3.5 Room Overlap Invariant

Thuật toán kiểm tra trùng phòng (Đặt lịch / Đổi phòng / Xuất bản / ReBuffer) **BẮT BUỘC** dựa trên
**Scheduled Occupancy Window** `[S - B_before, E]`. Hai bài A, B cùng phòng xung đột khi:
```
(S_A - B_before_A) < E_B AND E_A > (S_B - B_before_B)
```
Quy trình "kiểm tra overlap" là **global rule** — do Application orchestrate + lock-set-first (ADR 0005, ADR 0015),
không inject vào Aggregate.

---

## 4. Consequences

**Positive**
- Một khái niệm buffer duy nhất, không nhầm lẫn giữa trước/sau; migration nhẹ (chỉ +2 cột).
- Không còn cột temporal denormalized → loại bỏ rủi ro out-of-sync, giảm bề mặt concurrency (ADR- 0001).
- Cap Policy nạp được, thay đổi không cần migration (ADR 0005).

**Negative**
- Overlap predicate buộc tính runtime (`[S-B, E]`) ở JPQL/JOOQ — cần truyền occupancy window vào query (`queryStart`/`queryEnd`) từ Handler thay vì đọc trong SQL.
- `ReBufferWorkshop` PUBLISHED phải lock-set-first trên occupancy window mới để không trùng lịch (ADR 0015).

---

## 5. Compliance Checklist (Workshop module)

- [ ] `workshops` có 2 cột mới: `buffer_before_minutes INT NOT NULL DEFAULT 15`, `buffer_justification TEXT NULL DEFAULT 'DEFAULT'`; **không** `buffer_after_minutes` / `scheduled_occupancy_*`.
- [ ] Overlap predicate (JPA lock-set-first + JOOQ read) tính `[S-B, E]` từ `(start_time, end_time, buffer_before_minutes)`.
- [ ] `WorkshopBuffer` (single-sided, invariant ≥ 0) + `BufferJustification` (không blank).
- [ ] `ReBufferWorkshopCommand` + Handler: validate cap, justification bắt buộc, conflict check trên occupancy window mới.
- [ ] Cap max chạy trong Application trên Attribute (`maxMinutes`), domain không hardcode.

---

> **Nhận xét**: Trạng thái `ACCEPTED` bản sửa này coi Lean Model là nền tảng để Spec v2 (Epic 1 Extension — Gap
> Time) implement; các cột cũ nếu có chưa cần dọn dẹp ngay nhưng được khuyến nghị trong cùng PR.