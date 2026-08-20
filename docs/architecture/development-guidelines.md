# Development Guidelines — Quick Reference (CQS + Hexagonal + Spring Modulith)

> **Cách dùng:** Tài liệu này là *cheat-sheet* — đọc để nắm công thức chuẩn trước khi code. Mỗi module
> (Room, Workshop, ...) bắt buộc tuân thủ layout vàng bên dưới. Module **Room** hiện là
> *reference implementation* (đã chạy thực tế, 62/62 test xanh): copy pattern từ đó khi tạo module mới.

---

## 1. Cấu trúc thư mục chuẩn (Golden Layout)

```
<module>/
├── contract/                  # Public contracts shared *across* modules (DTOs, integration events)
│                              # These are part of the module's public API (per ADR 0010)
├── RoomExposeAPI.java         # Public Facade interface (cross-module surface)
├── WorkshopExposeAPI.java
└── internal/
    ├── facade/                # ExposeAPIImpl — Module Facade (package-private, per ADR 0010)
    │                          # Coordinates directly with Application ports, no Command/Query Bus
    ├── domain/
    │   ├── model/             # Aggregate Root, Value Objects, state enum — TẤT CẢ ở root (flat)
    │   │   ├── event/         # RoomCreated, RoomRenamedEvent, ... (sealed RoomDomainEvent)
    │   │   └── exception/     # DuplicateRoomCodeException, DuplicateRoomNameException, ...
    │   └── service/           # domain service (nếu cần)
    ├── application/
    │   ├── port/
    │   │   ├── inbound/
    │   │   │   ├── command/   # CreateRoomCommand + nested Result, RenameRoomCommand + nested Result
    │   │   │   └── query/     # GetRoomByIdQuery, GetRoomByNameQuery, view/ (RoomDetailView...)
    │   │   └── outbound/      # RoomRepository (write), RoomReader (read, CQRS bypass)
    │   └── handler/           # *CommandHandler, *QueryHandler (package-private, @Component)
    └── adapter/
        ├── inbound/
        │   ├── http/          # *CommandController, *QueryController, *ExceptionAdvice
        │   └── event/         # Event Bus consumer (tương lai)
        └── outbound/
            └── persistence/
                ├── jpa/        # JpaRoomWriteAdapter (impl RoomRepository) + RoomJpaRepository/Entity
                └── jooq/       # JooqRoomReadAdapter (impl RoomReader) + generated jooq.tables.Rooms
```

**Quy tắc bất di bất dịch (ADR 0001 + 0002 + 0010):**
- `internal/` là package-private. Lớp ngoài `internal/` **không được** import class trong `internal/`
  (trừ `RoomExposeAPI` đã được whitelist). `@ApplicationModule` tự động kiểm tra.
- Types intended for cross-module communication (`contract/`, `*ExposeAPI`) là public API —
  chúng **không** được đặt trong `internal/` (Contract Visibility Rule, ADR 0010).
- The Module Facade (`internal/facade/`) là trusted collaboration, không phải Driving Adapter.
  Nó được phép điều phối trực tiếp Application Ports mà không qua Command/Query Bus (ADR 0010).
- Bất kỳ class nào expose ra ngoài module **phải** là `public` & `final`.
- Một chiều: `internal → outside` được; `outside → internal` không được (trừ API whitelist).

---

## 2. Luồng Ghi (Command) — Command/Write Side

### 2.1 Shared contract (dùng chung, nằm trong Shared Kernel)
> **Strict CQS (ADR 0021):** `Command` là **Marker Interface** (không generic), `CommandHandler.handle`
> trả về **`void`**. Không có `Result` trả về từ command.

```java
public interface Command {}                    // Marker interface, không generic
public interface CommandHandler<C extends Command> {
    void handle(C command);                    // void — không trả dữ liệu
}
public interface CommandBus {
    void execute(Command command);            // void
}
```

### 2.2 Command DTO = record chứa raw input (Strict CQS — ADR 0021)
> **Không còn nested `Result`.** Command chỉ mang **raw input**; validation/normalization do Domain VO
> thực hiện bên trong handler. Handler `void` — Client không nhận payload, tự query (GET) theo `Location`
> khi cần. Với các lệnh tạo mới, id là **Caller-Generated** (adapter sinh `UUID` đẩy vào command).

```java
// port.inbound.command.RenameRoomCommand — chỉ raw input, KHÔNG Result
public record RenameRoomCommand(
        UUID roomId,
        String newName
) implements Command {}

// port.inbound.command.CreateRoomCommand — có trường capacity + code (int) + name (free-form);
// roomId là caller-generated (ADR 0021 Caller-Generated ID)
public record CreateRoomCommand(
        UUID roomId,
        String building,
        int floor,
        int code,
        String name,
        int capacity
) implements Command {}

// port.inbound.command.ChangeRoomCodeCommand — đổi code (int) SILENT, không event
public record ChangeRoomCodeCommand(UUID roomId, int newCode) implements Command {}
```

### 2.3 Out Port — ghi (RoomRepository)
> Write port chỉ còn hợp đồng persistence nguyên thủy (`loadById` + `save`). **Uniqueness KHÔNG còn là
> repository concern** — `existsByCoordinate` / `existsByName` đã bị gỡ bỏ (ADR 0005). Việc chứng minh tính
> duy nhất toàn cục nằm ở Domain Policy (xem §2.6), IO của nó nằm ở adapter `JpaRoomUniquenessPolicy`.
```java
// port.outbound.RoomRepository — write port duy nhất (chỉ load + save)
public interface RoomRepository {
    Optional<Room> loadById(UUID id);
    Room save(Room room);
}
```

### 2.4 Handler (package-private, nằm trong `application/handler`)
> Handler luôn **`void`** (Strict CQS). Không `return` Result — Client query lại nếu cần đọc (OQ-8).

```java
@Transactional
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand> {

    private final RoomRepository roomRepository;

    @Override
    public void handle(RenameRoomCommand command) {
        // 1. Load aggregate (write repository)
        Room room = roomRepository.loadById(command.roomId())
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // 2. RAM guard: RoomName VO tự validate/normalize newName (free-form, chỉ blank-check)
        RoomName candidate = RoomName.of(command.newName());

        // 3. Idempotency: cùng name => no-op, không gate/persist
        if (candidate.equals(room.name())) {
            return;
        }

        // 4. Domain mutation (changeName ghi RoomRenamedEvent) rồi persist.
        //    Tính duy nhất name do AGGREGATE tự check qua uniquenessPolicy (ADR 0005) + race gate ở adapter.
        room.changeName(command.newName(), uniquenessPolicy);
        roomRepository.save(room);
    }
}
```
- Handler `@Transactional`, **package-private**, `@Component` — được gọi qua `CommandBus` (Spring bean
  tìm bằng generic type), không gọi trực tiếp từ ngoài.
- Idempotency nằm trước DB gate để tránh *false-positive self-collision* (đổi sang cùng code cũ).

### 2.5 CommandBus (Shared Kernel — ADR 0006)
`CommandBus` là interface chia sẻ ở `shared.application.cqs.api` (shared kernel) (không còn per-module). `CommandBus.execute(C command)`
được delegate tới `CommandDispatcher` (Coordinator) → `CommandHandlerResolver` (resolve handler qua registry)
→ `CommandPipeline` (chain `CommandBehavior`, mặc định pass-through) → `CommandHandler`. Command handler
được scan **eager tại startup** bởi `CommandHandlerRegistry` (qua `ObjectProvider`, rồi freeze immutable) —
duplicate/missing handler fail fast bằng `DuplicateCommandHandlerException` / `MissingCommandHandlerException`.
```java
RenameRoomCommand.Result result = commandBus.execute(command);
```
> Mỗi module KHÔNG tự định nghĩa `CommandBus`/`SimpleCommandBus` nữa (ADR 0002 §5 đã bị supersede bởi ADR 0006).
> Cross-cutting concern = thêm `CommandBehavior` mới + `ModuleRegistration` matcher, **không** sửa `CommandDispatcher`.

### 2.6 Global Uniqueness = Domain Policy (ADR 0005)

> Set-based invariant (no two rooms share `(building, floor, code)` or `(building, floor, name)`) cannot be
> proven *by* one aggregate, but the **decision + exception belong to the Domain**. So uniqueness is a
> **domain-owned policy interface**, not an `exists*` method on the repository port.

**Domain — `domain/model/policy/RoomUniquenessPolicy.java`** (specifies the invariant, no IO):
```java
public interface RoomUniquenessPolicy {
    boolean isCodeUnique(RoomLocation location, int code);   // no other room owns the coordinate
    boolean isNameUnique(RoomLocation location, RoomName name); // no other room owns the name@location
}
```
The aggregate receives the policy as an argument on every uniqueness-sensitive op and throws
`DuplicateRoomCodeException` / `DuplicateRoomNameException` itself:
```java
// Room.create / changeCode / changeName / relocateTo each take (..., RoomUniquenessPolicy policy)
Room room = Room.create(name, location, code, capacity, uniquenessPolicy);   // checks both, then builds
room.changeName(command.newName(), uniquenessPolicy);                        // idempotency skip, then check
room.changeCode(newCode, uniquenessPolicy);
room.relocateTo(newLocation, uniquenessPolicy);
```
Idempotency (same code / same name / same location) is checked **inside the aggregate, before** the policy
call — avoids false-positive self-collision and needless IO.

**Infrastructure — `adapter/outbound/persistence/jpa/JpaRoomUniquenessPolicy.java`** (runtime IO, in the adapter):
```java
@Component
class JpaRoomUniquenessPolicy implements RoomUniquenessPolicy {
    private final RoomJpaRepository jpaRepository;
    @Override public boolean isCodeUnique(RoomLocation loc, int code) {
        return !jpaRepository.existsByBuildingAndFloorAndCode(loc.building(), loc.floor(), code);
    }
    @Override public boolean isNameUnique(RoomLocation loc, RoomName name) {
        return !jpaRepository.existsByBuildingAndFloorAndName(loc.building(), loc.floor(), name.asString());
    }
}
```

**Handler stays thin** — injects `RoomUniquenessPolicy` (for the `create` path) + `RoomRepository` (load/save),
never evaluates the invariant itself, never calls `exists*`:
```java
@Transactional
@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand> {
    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    @Override
    public void handle(CreateRoomCommand c) {
        RoomName name = RoomName.of(c.name());              // VO self-defense (local invariant)
        RoomLocation location = RoomLocation.of(c.building(), c.floor());
        if (c.code() <= 0) throw new RoomDomainException("code must be > 0");

        // roomId là caller-generated (ADR 0021): adapter sinh UUID đẩy vào command
        Room room = Room.create(RoomId.of(c.roomId()), name, location, c.code(), c.capacity(),
                uniquenessPolicy); // Domain owns invariant
        roomRepository.save(room);
        // void — controller trả 201 + Location: /api/v1/rooms/{c.roomId()}
    }
}
```

**DB unique constraint = authoritative race-proof gate.** `uk_rooms_building_floor_code` +
`uk_rooms_building_floor_name` remain the final integrity authority. `JpaRoomWriteAdapter.save()` still
translates `DataIntegrityViolationException` → `DuplicateRoomCodeException` / `DuplicateRoomNameException`
(matched by constraint name, correct
`Reason`), so the TOCTOU race is still caught. Policy read and DB constraint are **complementary**, not
substitutive. Reconstitution (`Room.reconstruct`) bypasses any uniqueness check.

---

## 3. Luồng Đọc (Query) — Query/Read Side

### 3.1 Shared contract
```java
public interface Query<R> {}
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
```

### 3.2 Query DTO = record (ở `port.inbound.query`) + View (ở `port.inbound.query.view`)
> **Pattern thực chiến:** Query record nhẹ, chỉ chứa tham số. Kết quả trả về là các `View` nằm trong
> sub-package **`view/`** (`RoomDetailView`, `RoomSummaryView`). Một View phục vụ nhiều Query
> (multi-1, global) nên tách riêng để tiến hóa độc lập với write flow (CQRS bypass, không reconstruct domain).

```java
// port.inbound.query.GetRoomByIdQuery
public record GetRoomByIdQuery(UUID roomId) implements Query<RoomDetailView> {}

// port.inbound.query.GetRoomByNameQuery — raw name, handler sẽ parse thành RoomName (RAM self-defense)
public record GetRoomByNameQuery(String roomName) implements Query<RoomSummaryView> {}

// port.inbound.query.view.RoomDetailView — projection đầy đủ (state là String: ACTIVE/MAINTENANCE/DEACTIVATED)
public record RoomDetailView(UUID id, String name, String building, int floor, int capacity, String state) {}

// port.inbound.query.view.RoomSummaryView — projection gọn (subset của Detail)
public record RoomSummaryView(UUID id, String name, String building, int floor) {}
```

### 3.3 Out Port — đọc (RoomReader, CQRS bypass)
> Read-side giữ nguyên là `RoomReader` (CQRS bypass), trả View trực tiếp, không reconstruct domain.
```java
public interface RoomReader {
    Optional<RoomDetailView> getById(UUID id);          // CQRS bypass: trả View trực tiếp
    Optional<RoomSummaryView> getByName(RoomName name); // RoomName là opaque VO, không reverse-parse
}
```

### 3.4 Handler (package-private, @Component, readOnly)
```java
@Transactional(readOnly = true)
@Component
class GetRoomByIdQueryHandler implements QueryHandler<GetRoomByIdQuery, RoomDetailView> {
    private final RoomReader roomReader;
    @Override public RoomDetailView handle(GetRoomByIdQuery q) {
        return roomReader.getById(q.roomId()).orElseThrow(() -> new RoomNotFoundException("id=" + q.roomId()));
    }
}

@Transactional(readOnly = true)
@Component
class GetRoomByNameQueryHandler implements QueryHandler<GetRoomByNameQuery, RoomSummaryView> {
    private final RoomReader roomReader;
    @Override public RoomSummaryView handle(GetRoomByNameQuery q) {
        RoomName name = RoomName.of(q.roomName());  // RAM self-defense; free-form, không parse ngược
        return roomReader.getByName(name).orElseThrow(() -> new RoomNotFoundException("name=" + name.asString()));
    }
}
```

### 3.5 QueryBus (Shared Kernel — ADR 0006)
Tương tự CommandBus, `QueryBus` là interface chia sẻ ở `shared.application.cqs.api` (shared kernel). `QueryBus.execute(Q query)` delegate
tới `QueryDispatcher` → `QueryHandlerResolver` → `QueryHandlerRegistry` resolve `QueryHandler` qua `ObjectProvider`
(lazy, scan tại lần dispatch đầu tiên) → invoke. Query là read-only nên **không có behavior chain** (zero-pipeline).

> **Bất đối xứng Command vs Query (chủ đích — ADR 0006):** Command registry (`CommandHandlerRegistry`) scan
> **eager tại startup** (fail-fast duplicate/missing); Query registry (`QueryHandlerRegistry`) scan **lazy tại
> runtime** (thread-safe, scan-once). Cả 2 đều nhận `ObjectProvider` trong constructor — **không `@Lazy`, không
> proxy** — để xóa vĩnh viễn startup bean cycle khi handler phụ thuộc bus. Query không có pipeline (đọc trực tiếp,
> zero-overhead); pipeline `CommandBehavior` chỉ thuộc Command subsystem (`dispatch/command/pipeline/`).

### 3.6 Shared Kernel CQS — cấu trúc + quy chuẩn đặt tên (ADR 0006)

Cây thư mục chuẩn của shared kernel `shared.application.cqs` (không nằm trong module nào):

```
shared/application/cqs/
├── api/                                  # CQS contracts + bus interfaces (public, import từ module)
│   ├── Command.java / Query.java
│   ├── CommandHandler.java / QueryHandler.java
│   └── CommandBus.java / QueryBus.java
├── config/
│   └── BusConfiguration.java             # @Configuration khai báo toàn bộ bean (2 subsystem đối xứng)
├── exception/
│   ├── DuplicateCommandHandlerException / MissingCommandHandlerException
│   └── DuplicateQueryHandlerException / MissingQueryHandlerException
└── dispatch/
    ├── command/                          # COMMAND SUBSYSTEM — Ghi / Mutate / có Side-effects
    │   ├── CommandDispatcher.java
    │   ├── CommandHandlerRegistry.java   # EAGER scan @startup via ObjectProvider, freeze immutable
    │   ├── CommandHandlerResolver.java
    │   ├── RegistryCommandHandlerResolver.java
    │   └── pipeline/                     # Chain of Responsibility — CHỈ của Command side
    │       ├── BehaviorChain.java
    │       ├── CommandBehavior.java
    │       ├── CommandPipeline.java
    │       ├── CommandPolicyResolver.java
    │       └── CompositeCommandPolicyResolver.java
    └── query/                            # QUERY SUBSYSTEM — Đọc / Projection / Zero-Pipeline
        ├── QueryDispatcher.java
        ├── QueryHandlerRegistry.java     # LAZY scan @runtime via ObjectProvider (first dispatch)
        ├── QueryHandlerResolver.java
        └── RegistryQueryHandlerResolver.java
```

**Bảng quy chuẩn đặt tên (Symmetry Matrix):**

| Chức năng | Command side (Write) | Query side (Read) |
| --- | --- | --- |
| Dispatcher (Coordinator) | `CommandDispatcher` | `QueryDispatcher` |
| Registry (gom handler beans) | `CommandHandlerRegistry` | `QueryHandlerRegistry` |
| Resolver (interface lookup) | `CommandHandlerResolver` | `QueryHandlerResolver` |
| Resolver (impl, registry-backed) | `RegistryCommandHandlerResolver` | `RegistryQueryHandlerResolver` |
| Handler scan | **Eager @startup** — fail-fast duplicate/missing | **Lazy @runtime** — duplicate/missing tại dispatch |
| Pipeline | CÓ — `pipeline/` (`CommandBehavior` chain) | KHÔNG — zero-pipeline, đọc trực tiếp |
| Exception | `Duplicate/MissingCommandHandlerException` | `Duplicate/MissingQueryHandlerException` |

> **Luật đặt tên:** mọi class nội bộ của dispatch engine phải mang tiền tố rõ ràng theo subsystem —
> `Command*` cho write, `Query*` cho read. Không bao giờ để tên chung chung không tiền tố (VD: `HandlerRegistry`
> là tên cũ đã bị thay bằng `CommandHandlerRegistry`). Constructor của cả 2 registry nhận
> `ObjectProvider<CommandHandler<?, ?>>` / `ObjectProvider<QueryHandler<?, ?>>` — gom beans qua Spring, không scan
> thủ công bằng `ListableBeanFactory`.

### 3.7 Driven persistence — tách Command (JPA) / Query (JOOQ), CQRS logical split (ADR 0002)
> Write và read **cùng 1 datasource** (logical split, không tách DB vật lý). Mỗi bên có adapter + mapping riêng.
- **Command side (`persistence/jpa/`):** `JpaRoomWriteAdapter` impl `RoomRepository` (save / loadById /
  existsByCoordinate). Mapping domain ↔ JPA entity nằm ở đây. Flyway là **schema owner duy nhất**.
- **Query side (`persistence/jooq/`):** `JooqRoomReadAdapter` impl `RoomReader` (getById / getByName).
  Dùng `DSLContext` (cùng DataSource) + generated `io.github.ryu200o.eduworkshop.room.jooq.tables.Rooms`
  để query cột phẳng → map trực tiếp vào `Room*View`. **KHÔNG** qua JPA entity, **KHÔNG** reconstruct domain.
- JOOQ table class sinh tự động từ `src/main/resources/db/codegen/rooms_schema.sql` (codegen-only DDL, mirror
  schema cuối cùng) qua `jooq-codegen-maven` (DDLDatabase) ở phase `generate-sources`. JOOQ **chỉ đọc** schema,
  không chạy migration. Khi schema đổi: sửa Flyway migration + cập nhật `rooms_schema.sql`, rồi rebuild.
- Cấu hình: `spring.jooq.sql-dialect=POSTGRES` (H2 test chạy `MODE=PostgreSQL` nên đồng bộ cả 2 môi trường).
- Rủi ro đã biết: 2 bộ mapping song song (entity vs row) — schema đổi phải sửa cả 2. Chấp nhận trade-off nhỏ.

---

### 3.8 Module Facade — Cross-Module API (ADR 0010)

The `*ExposeAPI` + its implementation in `internal/facade/` constitute the **Module Facade**:

- **Not a Driving Adapter:** It is called programmatically by another module's Application layer,
  not by external HTTP/event input.
- **Not an Application Handler:** It bypasses the Command/Query Bus because it's a trusted
  cross-module collaboration, not an external entry point.
- **Coordinates directly with Application Ports:** The Facade may call `RoomReader`,
  `RoomRepository`, or domain services directly — no bus required.

Example — RoomExposeAPI providing snapshot data:

```java
// room/RoomExposeAPI.java (public interface, module root)
public interface RoomExposeAPI {
    Optional<RoomSnapshot> getRoomSnapshot(UUID roomId);
}

// room/contract/RoomSnapshot.java (public DTO, module root/contract/)
public record RoomSnapshot(UUID roomId, String name, Location location) {
    public record Location(String building, int floor) {}
}

// room/internal/facade/RoomExposeAPIImpl.java (package-private implementation)
@Component
class RoomExposeAPIImpl implements RoomExposeAPI {
    private final RoomReader roomReader;

    @Override
    public Optional<RoomSnapshot> getRoomSnapshot(UUID roomId) {
        return roomReader.getById(RoomId.of(roomId))
                .map(view -> new RoomSnapshot(view.id(), view.name(),
                        new RoomSnapshot.Location(view.building(), view.floor())));
    }
}
```

Workshop Application handler consumes the Facade (never imports the impl):

```java
// workshop/internal/application/handler/PlanWorkshopCommandHandler.java
import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;

RoomSnapshot snapshot = roomExposeApi.findRoomSnapshot(roomId)
        .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", roomId));

String locationSnapshot = snapshot.location().building() + "/" + snapshot.location().floor();
RoomReference roomRef = RoomReference.of(snapshot.roomId(), snapshot.name(), locationSnapshot);
```

---

## 4. Inbound HTTP Adapter — tách rõ C/Q + Advice scoped (ADR 0004)

### 4.1 Tách 2 controller, mỗi cái chỉ cầm 1 bus
```java
@RestController
@RequestMapping("/api/v1/rooms")
class RoomCommandController {
    private final CommandBus commandBus;                 // CHỈ CommandBus
    @PostMapping
    ResponseEntity<Void> create(@RequestBody CreateRoomRequest request) {
        UUID roomId = UUID.randomUUID();               // Caller-Generated ID (ADR 0021)
        var command = new CreateRoomCommand(            // var RÕ RÀNG, không new() trong execute()
                roomId, request.building(), request.floor(), request.code(), request.name(), request.capacity());
        commandBus.execute(command);                    // void — KHÔNG nhận Result
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + roomId)).build(); // 201 + Location
    }
    @PutMapping("/{id}/rename")
    ResponseEntity<Void> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newName());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();       // 204 — mutation
    }
    @PutMapping("/{id}/code")
    ResponseEntity<Void> changeCode(@PathVariable UUID id, @RequestBody ChangeRoomCodeRequest request) {
        var command = new ChangeRoomCodeCommand(id, request.newCode());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }
    record CreateRoomRequest(String building, int floor, int code, String name, int capacity) {}
    record RenameRoomRequest(String newName) {}
    record ChangeRoomCodeRequest(int newCode) {}
}

@RestController
@RequestMapping("/api/v1/rooms")
class RoomQueryController {
    private final QueryBus queryBus;                    // CHỈ QueryBus
    @GetMapping("/{id}")
    RoomDetailView getById(@PathVariable UUID id) {
        var query = new GetRoomByIdQuery(id);
        return queryBus.execute(query);
    }
    @GetMapping("/by-name/{name}")
    RoomSummaryView getByName(@PathVariable String name) {
        var query = new GetRoomByNameQuery(name);
        return queryBus.execute(query);
    }
}
```
- **Luật:** controller ghi chỉ `POST/PUT/DELETE` + `CommandBus`; controller đọc chỉ `GET` + `QueryBus`.
  Không trộn lẫn.
- **Luật:** luôn `var command = new XCommand(...)` trước `bus.execute(...)` — dễ breakpoint/debug.
- Request body dùng nested `XxxRequest` record trong controller; Command chỉ nhận raw param.

### 4.2 Centralized Exception Advice (scoped, in-module)
> Nằm trong module Room (`adapter/inbound/http/RoomExceptionAdvice.java`), **không** đẩy lên Shared
> Kernel — giữ encapsulation module (Spring Modulith).

```java
@RestControllerAdvice(assignableTypes = {RoomCommandController.class, RoomQueryController.class})
class RoomExceptionAdvice {
    @ExceptionHandler(RoomNotFoundException.class)   // 404
    public ResponseEntity<ErrorResponse> notFound(RoomNotFoundException e) {...}
    @ExceptionHandler(DuplicateRoomCodeException.class)   // 409
    public ResponseEntity<ErrorResponse> duplicateCode(DuplicateRoomCodeException e) {...}
    @ExceptionHandler(DuplicateRoomNameException.class)   // 409
    public ResponseEntity<ErrorResponse> duplicateName(DuplicateRoomNameException e) {...}
    @ExceptionHandler(RoomDomainException.class)     // 400
    public ResponseEntity<ErrorResponse> badRequest(RoomDomainException e) {...}
}
```
- Dùng `assignableTypes` để advice **chỉ** áp dụng cho 2 controller của module này, không ảnh hưởng module
  khác. Exception nghiệp vụ (`RoomDomainException` và subclass) không rò rỉ ra Shared Kernel.

---

## 5. Domain Modeling — Name free-form, Code int độc lập (nhắc lại, ADR 0003)

- `RoomName` là VO **free-form**: `RoomName.of(String)` chỉ blank-check + normalize (trim/upper), **không**
  chứa tọa độ, **không** parse ngược. Business tự chịu rủi ro đặt tên trùng/định dạng.
- `code` là `int` **độc lập**, chỉ để FE sắp xếp — đổi code (`Room.changeCode(int)`) là **silent**,
  **không** phát event. Validate: dương.
- `RoomLocation` (`building`, `floor`) **immutable** — rename/đổi code không thay đổi tọa độ. Relocation
  (`relocateTo`) giữ nguyên `name` + `code`, chỉ đổi `location`, phát `RoomRelocatedEvent`
  (`oldLocation`/`newLocation`, không mang name).
- Rename (`Room.changeName(String, RoomUniquenessPolicy)`) đổi `name` trực tiếp, phát `RoomRenamedEvent` (chỉ
  `oldName`/`newName`, **không** mang code/location) để module khác (Workshop) phản ứng. Tính duy nhất name do
  **aggregate tự check qua `RoomUniquenessPolicy`** (ADR 0005) + race gate ở `JpaRoomWriteAdapter.save()` đảm bảo.
- Aggregate **ghi nhận event** thay vì publish trực tiếp: `RoomRenamedEvent` (name change) và
  `RoomRelocatedEvent` (location change) là hai record riêng, đều `implements RoomDomainEvent`, thuộc
  sealed `RoomDomainEvent permits ...`. Mỗi event chứa đủ context liên quan (`RoomRenamedEvent`:
  `oldName/newName/occurredAt`; `RoomRelocatedEvent`: `oldLocation/newLocation/occurredAt`) để module khác
  phản ứng sau này mà không cần gọi ngược. Xem `docs/architecture/diagrams/room-workshop-event-reaction.mermaid`.

---

## 7. Checklist trước khi tạo PR

> **Quy ước branch (xem AGENTS.md):** mỗi slice = **1 commit** trên cùng nhánh feature (không tách PR nhỏ cho từng slice). **Chỉ mở PR khi task tính năng mà nhánh đó đại diện đã hoàn thiện** (toàn bộ slices xong) → merge **1 PR duy nhất** vào `main`. Checklist dưới đây áp dụng cho PR duy nhất đó.

- [ ] Mọi class trong `internal/` là package-private (handler, port impl, mapper, facade impl...), chỉ API công khai (`*ExposeAPI`, `contract/*`) là `public`.
- [ ] Contract DTO dùng chung giữa các module nằm ở `contract/` (module root), **không** trong `internal/` (ADR 0010).
- [ ] Module Facade (`internal/facade/`): implementation package-private, gọi trực tiếp Application Ports, không qua Command/Query Bus (ADR 0010).
- [ ] Command là `Marker Interface` (không generic), handler `void handle(C command)`; **không** nested `Result`. Query View nằm trong `port.inbound.query.view`. **Không còn** `XResponse` chung. Tạo mới trả `201 + Location` (Caller-Generated ID); mutation trả `204`.
- [ ] Driving adapter: `RoomCommandController` (CommandBus) + `RoomQueryController` (QueryBus) tách rõ;
      `var x = new XCommand(...)` trước `execute`; request body qua nested `XxxRequest`.
- [ ] Exception nghiệp vụ tập trung ở `*ExceptionAdvice` scoped `assignableTypes`, nằm trong module.
- [ ] `internal/` không bị outside import (chạy build/ArchitectureTest xanh).
- [ ] `./mvnw test` xanh toàn bộ, không regression.
