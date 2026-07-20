# Development Guidelines — Quick Reference (CQS + Hexagonal + Spring Modulith)

> **Cách dùng:** Tài liệu này là *cheat-sheet* — đọc để nắm công thức chuẩn trước khi code. Mỗi module
> (Room, Workshop, ...) bắt buộc tuân thủ layout vàng bên dưới. Module **Room** hiện là
> *reference implementation* (đã chạy thực tế, 62/62 test xanh): copy pattern từ đó khi tạo module mới.

---

## 1. Cấu trúc thư mục chuẩn (Golden Layout)

```
<module>/
├── internal/
│   ├── domain/
│   │   ├── model/             # Aggregate Root, Value Objects, state enum — TẤT CẢ ở root (flat)
│   │   │   ├── event/         # RoomCreated, RoomRenamedEvent, ... (sealed RoomDomainEvent)
│   │   │   └── exception/     # DuplicateRoomCodeException, DuplicateRoomNameException, RoomDomainException, IllegalRoomStateException, ...
│   │   └── service/           # domain service (nếu cần)
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── command/   # CreateRoomCommand + nested Result, RenameRoomCommand + nested Result
│   │   │   │   └── query/     # GetRoomByIdQuery, GetRoomByNameQuery, view/ (RoomDetailView...)
│   │   │   └── out/           # RoomRepository (write), RoomReader (read, CQRS bypass)
│   │   └── handler/           # *CommandHandler, *QueryHandler (package-private, @Component)
│   └── adapter/
│       ├── driving/
│       │   ├── http/          # *CommandController, *QueryController, *ExceptionAdvice
│       │   └── event/         # Event Bus consumer (tương lai)
│       └── driven/
│           └── persistence/
│               ├── jpa/        # JpaRoomWriteAdapter (impl RoomRepository, C) + RoomJpaRepository/Entity
│               └── jooq/       # JooqRoomReadAdapter (impl RoomReader, Q) + generated jooq.tables.Rooms
└── RoomExposeAPI.java         # public API (cross-module surface, để trống nếu chưa công bố)
```

**Quy tắc bất di bất dịch (ADR 0001 + 0002):**
- `internal/` là package-private. Lớp ngoài `internal/` **không được** import class trong `internal/`
  (trừ `RoomExposeAPI` đã được whitelist). `@ApplicationModule` tự động kiểm tra.
- Bất kỳ class nào expose ra ngoài module **phải** là `public` & `final`.
- Một chiều: `internal → outside` được; `outside → internal` không được (trừ API whitelist).

---

## 2. Luồng Ghi (Command) — Command/Write Side

### 2.1 Shared contract (dùng chung, nằm trong Shared Kernel)
```java
public interface Command<R> {}
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
```

### 2.2 Command DTO = record + nested `Result` (Hybrid CQS — ADR 0004)
> **Pattern thực chiến:** `Result` là `public static record` **nested trong** Command (không tạo file
> `*Result.java` riêng). Một Command ↔ một Result (1-1), giữ contract trong cùng 1 file.

```java
// port.in.command.RenameRoomCommand — chỉ chứa raw input, validation để ở domain VO bên trong handler
public record RenameRoomCommand(
        UUID roomId,
        String newName
) implements Command<RenameRoomCommand.Result> {

    // Kết quả ghi nhẹ: chỉ mang trường bị ảnh hưởng trực tiếp (id, old/new name, thời điểm)
    public record Result(UUID id, String oldName, String newName, Instant updatedAt) {}
}

// port.in.command.CreateRoomCommand — lưu ý có trường capacity + code (int) + name (free-form)
public record CreateRoomCommand(String building, int floor, int code, String name, int capacity)
        implements Command<CreateRoomCommand.Result> {
    public record Result(UUID id, String name) {}
}

// port.in.command.ChangeRoomCodeCommand — đổi code (int) SILENT, không event
public record ChangeRoomCodeCommand(UUID roomId, int newCode)
        implements Command<ChangeRoomCodeCommand.Result> {
    public record Result(UUID id, int oldCode, int newCode, Instant updatedAt) {}
}
```

### 2.3 Out Port — ghi (RoomRepository)
> Write port chỉ còn hợp đồng persistence nguyên thủy (`loadById` + `save`). **Uniqueness KHÔNG còn là
> repository concern** — `existsByCoordinate` / `existsByName` đã bị gỡ bỏ (ADR 0005). Việc chứng minh tính
> duy nhất toàn cục nằm ở Domain Policy (xem §2.6), IO của nó nằm ở adapter `JpaRoomUniquenessPolicy`.
```java
// port.out.RoomRepository — write port duy nhất (chỉ load + save)
public interface RoomRepository {
    Optional<Room> loadById(UUID id);
    Room save(Room room);
}
```

### 2.4 Handler (package-private, nằm trong `application/handler`)
```java
@Transactional
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {

    private final RoomRepository roomRepository;

    @Override
    public RenameRoomCommand.Result handle(RenameRoomCommand command) {
        // 1. Load aggregate (write repository)
        Room room = roomRepository.loadById(command.roomId())
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // 2. RAM guard: RoomName VO tự validate/normalize newName (free-form, chỉ blank-check)
        RoomName candidate = RoomName.of(command.newName());

        // 3. Idempotency: cùng name => no-op, không gate/persist
        if (candidate.equals(room.name())) {
            return toResult(room, room.name().asString());
        }

        // 4. Domain mutation (changeName ghi RoomRenamedEvent) rồi persist.
        //    Tính duy nhất name do AGGREGATE tự check qua uniquenessPolicy (ADR 0005) + race gate ở adapter.
        String oldName = room.name().asString();
        room.changeName(command.newName(), uniquenessPolicy);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldName);
    }

    private static RenameRoomCommand.Result toResult(Room room, String oldName) {
        return new RenameRoomCommand.Result(
                room.id(), oldName, room.name().asString(), room.updatedAt());
    }
}
```
- Handler `@Transactional`, **package-private**, `@Component` — được gọi qua `CommandBus` (Spring bean
  tìm bằng generic type), không gọi trực tiếp từ ngoài.
- Idempotency nằm trước DB gate để tránh *false-positive self-collision* (đổi sang cùng code cũ).

### 2.5 CommandBus (Shared Kernel — ADR 0006)
`CommandBus` là interface chia sẻ ở `shared.application.cqs.api` (shared kernel) (không còn per-module). `CommandBus.execute(C command)`
được delegate tới `CommandDispatcher` (Coordinator) → `HandlerResolver` (resolve handler qua `ResolvableType`)
→ `CommandPipeline` (chain `CommandBehavior`, mặc định pass-through) → `CommandHandler`. Duplicate/missing
handler fail fast bằng `DuplicateCommandHandlerException` / `MissingCommandHandlerException`.
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

**Infrastructure — `adapter/driven/persistence/jpa/JpaRoomUniquenessPolicy.java`** (runtime IO, in the adapter):
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
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, CreateRoomCommand.Result> {
    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    @Override
    public CreateRoomCommand.Result handle(CreateRoomCommand c) {
        RoomName name = RoomName.of(c.name());              // VO self-defense (local invariant)
        RoomLocation location = RoomLocation.of(c.building(), c.floor());
        if (c.code() <= 0) throw new RoomDomainException("code must be > 0");

        Room room = Room.create(name, location, c.code(), c.capacity(), uniquenessPolicy); // Domain owns invariant
        Room saved = roomRepository.save(room);
        return new CreateRoomCommand.Result(saved.id().value(), saved.name().asString());
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

### 3.2 Query DTO = record (ở `port.in.query`) + View (ở `port.in.query.view`)
> **Pattern thực chiến:** Query record nhẹ, chỉ chứa tham số. Kết quả trả về là các `View` nằm trong
> sub-package **`view/`** (`RoomDetailView`, `RoomSummaryView`). Một View phục vụ nhiều Query
> (multi-1, global) nên tách riêng để tiến hóa độc lập với write flow (CQRS bypass, không reconstruct domain).

```java
// port.in.query.GetRoomByIdQuery
public record GetRoomByIdQuery(UUID roomId) implements Query<RoomDetailView> {}

// port.in.query.GetRoomByNameQuery — raw name, handler sẽ parse thành RoomName (RAM self-defense)
public record GetRoomByNameQuery(String roomName) implements Query<RoomSummaryView> {}

// port.in.query.view.RoomDetailView — projection đầy đủ (state là String: ACTIVE/MAINTENANCE/DEACTIVATED)
public record RoomDetailView(UUID id, String name, String building, int floor, int capacity, String state) {}

// port.in.query.view.RoomSummaryView — projection gọn (subset của Detail)
public record RoomSummaryView(UUID id, String name, String building, int floor) {}
```

### 3.3 Out Port — đọc (RoomReader, CQRS bypass)
> Read-side giữ nguyên là `RoomReader` (CQRS bypass), trả View trực tiếp, không reconstruct domain.
```java
public interface RoomReader {
    Optional<RoomDetailView> findById(UUID id);          // CQRS bypass: trả View trực tiếp
    Optional<RoomSummaryView> findByName(RoomName name); // RoomName là opaque VO, không reverse-parse
}
```

### 3.4 Handler (package-private, @Component, readOnly)
```java
@Transactional(readOnly = true)
@Component
class GetRoomByIdQueryHandler implements QueryHandler<GetRoomByIdQuery, RoomDetailView> {
    private final RoomReader roomReader;
    @Override public RoomDetailView handle(GetRoomByIdQuery q) {
        return roomReader.findById(q.roomId()).orElseThrow(() -> new RoomNotFoundException("id=" + q.roomId()));
    }
}

@Transactional(readOnly = true)
@Component
class GetRoomByNameQueryHandler implements QueryHandler<GetRoomByNameQuery, RoomSummaryView> {
    private final RoomReader roomReader;
    @Override public RoomSummaryView handle(GetRoomByNameQuery q) {
        RoomName name = RoomName.of(q.roomName());  // RAM self-defense; free-form, không parse ngược
        return roomReader.findByName(name).orElseThrow(() -> new RoomNotFoundException("name=" + name.asString()));
    }
}
```

### 3.5 QueryBus (Shared Kernel — ADR 0006)
Tương tự CommandBus, `QueryBus` là interface chia sẻ ở `shared.application.cqs.api` (shared kernel). `QueryBus.execute(Q query)` delegate
tới `QueryDispatcher` → `HandlerRegistry` resolve `QueryHandler` qua `ResolvableType` → invoke. Query là
read-only nên không có behavior chain.

### 3.6 Driven persistence — tách Command (JPA) / Query (JOOQ), CQRS logical split (ADR 0002)
> Write và read **cùng 1 datasource** (logical split, không tách DB vật lý). Mỗi bên có adapter + mapping riêng.
- **Command side (`persistence/jpa/`):** `JpaRoomWriteAdapter` impl `RoomRepository` (save / loadById /
  existsByCoordinate). Mapping domain ↔ JPA entity nằm ở đây. Flyway là **schema owner duy nhất**.
- **Query side (`persistence/jooq/`):** `JooqRoomReadAdapter` impl `RoomReader` (findById / findByName).
  Dùng `DSLContext` (cùng DataSource) + generated `io.github.ryu200o.eduworkshop.room.jooq.tables.Rooms`
  để query cột phẳng → map trực tiếp vào `Room*View`. **KHÔNG** qua JPA entity, **KHÔNG** reconstruct domain.
- JOOQ table class sinh tự động từ `src/main/resources/db/codegen/rooms_schema.sql` (codegen-only DDL, mirror
  schema cuối cùng) qua `jooq-codegen-maven` (DDLDatabase) ở phase `generate-sources`. JOOQ **chỉ đọc** schema,
  không chạy migration. Khi schema đổi: sửa Flyway migration + cập nhật `rooms_schema.sql`, rồi rebuild.
- Cấu hình: `spring.jooq.sql-dialect=POSTGRES` (H2 test chạy `MODE=PostgreSQL` nên đồng bộ cả 2 môi trường).
- Rủi ro đã biết: 2 bộ mapping song song (entity vs row) — schema đổi phải sửa cả 2. Chấp nhận trade-off nhỏ.

---

## 4. Driving HTTP Adapter — tách rõ C/Q + Advice scoped (ADR 0004)

### 4.1 Tách 2 controller, mỗi cái chỉ cầm 1 bus
```java
@RestController
@RequestMapping("/api/v1/rooms")
class RoomCommandController {
    private final CommandBus commandBus;                 // CHỈ CommandBus
    @PostMapping
    ResponseEntity<CreateRoomCommand.Result> create(@RequestBody CreateRoomRequest request) {
        var command = new CreateRoomCommand(            // var RÕ RÀNG, không new() trong execute()
                request.building(), request.floor(), request.code(), request.name(), request.capacity());
        CreateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }
    @PutMapping("/{id}/rename")
    ResponseEntity<RenameRoomCommand.Result> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newName());
        return ResponseEntity.ok(commandBus.execute(command));
    }
    @PutMapping("/{id}/code")
    ResponseEntity<ChangeRoomCodeCommand.Result> changeCode(@PathVariable UUID id, @RequestBody ChangeRoomCodeRequest request) {
        var command = new ChangeRoomCodeCommand(id, request.newCode());
        return ResponseEntity.ok(commandBus.execute(command));
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
> Nằm trong module Room (`adapter/driving/http/RoomExceptionAdvice.java`), **không** đẩy lên Shared
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

## 6. Checklist trước khi tạo PR

- [ ] Mọi class trong `internal/` là package-private (handler, port impl, mapper...), chỉ API công khai là `public final`.
- [ ] Command có nested `Result`; Query View nằm trong `port.in.query.view`. **Không còn** `XResponse` chung.
- [ ] Driving adapter: `RoomCommandController` (CommandBus) + `RoomQueryController` (QueryBus) tách rõ;
      `var x = new XCommand(...)` trước `execute`; request body qua nested `XxxRequest`.
- [ ] Exception nghiệp vụ tập trung ở `*ExceptionAdvice` scoped `assignableTypes`, nằm trong module.
- [ ] `internal/` không bị outside import (chạy build/ArchitectureTest xanh).
- [ ] Unique index `(building, floor, code)` (code INT) + `uk_rooms_building_floor_name` làm DB gate.
- [ ] Global uniqueness nằm trong **Domain** (`RoomUniquenessPolicy` + aggregate tự throw
  `DuplicateRoomCodeException` / `DuplicateRoomNameException`); `RoomRepository` KHÔNG có `exists*`; handler "gầy" (chỉ truyền policy vào aggregate).
- [ ] `./mvnw test` xanh toàn bộ, không regression.
