# Development Guidelines — Quick Reference (CQS + Hexagonal + Spring Modulith)

> **Cách dùng:** Tài liệu này là *cheat-sheet* — đọc để nắm công thức chuẩn trước khi code. Mỗi module
> (Room, Workshop, ...) bắt buộc tuân thủ layout vàng bên dưới. Module **Room** hiện là
> *reference implementation* (đã chạy thực tế, 87/87 test xanh): copy pattern từ đó khi tạo module mới.

---

## 1. Cấu trúc thư mục chuẩn (Golden Layout)

```
<module>/
├── internal/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── entity/        # Room, Workshop... (aggregate root, package-private)
│   │   │   ├── vo/            # RoomCode, RoomName, RoomLocation (immutable, tự validate)
│   │   │   └── event/         # RoomRenamedEvent, RoomCreatedEvent (sealed RoomDomainEvent)
│   │   ├── service/           # domain service (nếu cần)
│   │   └── policy/            # business rule / spec
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── command/   # CreateRoomCommand + nested Result, RenameRoomCommand + nested Result
│   │   │   │   └── query/     # GetRoomByIdQuery, GetRoomByNameQuery, view/ (RoomDetailView...)
│   │   │   └── out/           # RoomStateGateway, RoomQueryPort
│   │   └── handler/           # *CommandHandler, *QueryHandler (package-private, @Component)
│   └── adapter/
│       ├── driving/
│       │   ├── http/          # *CommandController, *QueryController, *ExceptionAdvice
│       │   └── event/         # Event Bus consumer (tương lai)
│       └── driven/            # jpa/ (Jpa*Adapter), persistence entity/mapper
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
        String newCode
) implements Command<RenameRoomCommand.Result> {

    // Kết quả ghi nhẹ: chỉ mang trường bị ảnh hưởng trực tiếp (id, old/new code, name tái tính, thời điểm)
    public record Result(UUID id, String oldCode, String newCode, String name, Instant updatedAt) {}
}

// port.in.command.CreateRoomCommand — lưu ý có trường capacity
public record CreateRoomCommand(String building, int floor, int capacity, String roomCode)
        implements Command<CreateRoomCommand.Result> {
    public record Result(UUID id, String name) {}
}
```

### 2.3 Out Port (State) — ghi
```java
// port.out.RoomStateGateway — write port, trả domain entity
public interface RoomStateGateway {
    Optional<Room> loadById(UUID id);
    Room save(Room room);
}
```
> **Global invariant (trùng tọa độ) KHÔNG còn là out-port.** Nó là một **Domain Policy interface**
> (`domain/model/policy/RoomUniquenessPolicy`) do Domain sở hữu, impl bởi driven adapter, và được thực thi
> qua **Domain Factory** (`RoomFactory.create`) lúc tạo hoặc qua **aggregate method** (`room.changeCode(...,
> policy)`) lúc đổi identity. Xem ADR 0005. `RoomExistencePort` đã nghỉ hưu (tránh 2 port cùng mục đích).

### 2.4 Handler (package-private, nằm trong `application/handler`)
```java
// TẠO MỚI — Handler CHỈ gọi Domain Factory gateway, KHÔNG chạm Policy hay RoomName
@Transactional
@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, CreateRoomCommand.Result> {
    private final RoomFactory roomFactory;        // Domain gateway: check uniqueness + Room.create
    private final RoomStateGateway gateway;

    @Override
    public CreateRoomCommand.Result handle(CreateRoomCommand command) {
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        Room room = roomFactory.create(location, command.roomCode(), command.capacity());
        Room saved = gateway.save(room);
        return new CreateRoomCommand.Result(saved.id(), saved.name().asString());
    }
}

// ĐỔI IDENTITY (rename) — Handler nạp aggregate, truyền Policy vào aggregate method (KHÔNG gọi trực tiếp)
@Transactional
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {
    private final RoomStateGateway gateway;
    private final RoomUniquenessPolicy policy;    // Domain interface, truyền vào aggregate method

    @Override
    public RenameRoomCommand.Result handle(RenameRoomCommand command) {
        Room room = gateway.loadById(command.roomId())
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));
        String oldCode = room.name().code();
        room.changeCode(command.newCode(), policy);   // idempotency + ensureUniqueOrThrow nằm trong aggregate
        Room saved = gateway.save(room);
        return new RenameRoomCommand.Result(saved.id(), oldCode, saved.name().code(),
                saved.name().asString(), saved.updatedAt());
    }
}
```
- Handler `@Transactional`, **package-private**, `@Component` — được gọi qua `CommandBus`, không gọi trực tiếp.
- Application **mỏng**: không dựng `RoomName`, không `if (existsBy...)`, không gọi `policy` trực tiếp ở create
  (Factory lo); ở rename/relocate chỉ **truyền** `policy` vào aggregate method.
- `RoomFactory` là chốt chặn duy nhất cho uniqueness lúc tạo; `room.changeCode/relocateTo(policy)` là chốt chặn
  cho đổi identity. Cùng một `RoomUniquenessPolicy` → không bao giờ "quên" check. Xem ADR 0005.

### 2.5 CommandBus (giữ nguyên, Shared Kernel)
`CommandBus.execute(C command)` resolve handler qua `ResolvableType` (generic chính xác). Gọi:
```java
RenameRoomCommand.Result result = commandBus.execute(command);
```

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

### 3.3 Out Port (Query Port) — đọc
```java
public interface RoomQueryPort {
    Optional<RoomDetailView> findById(UUID id);          // CQRS bypass: trả View trực tiếp
    Optional<RoomSummaryView> findByName(RoomName name); // RoomName là opaque VO, không reverse-parse
}
```

### 3.4 Handler (package-private, @Component, readOnly)
```java
@Transactional(readOnly = true)
@Component
class GetRoomByIdQueryHandler implements QueryHandler<GetRoomByIdQuery, RoomDetailView> {
    private final RoomQueryPort port;
    @Override public RoomDetailView handle(GetRoomByIdQuery q) {
        return port.findById(q.roomId()).orElseThrow(() -> new RoomNotFoundException("id=" + q.roomId()));
    }
}

@Transactional(readOnly = true)
@Component
class GetRoomByNameQueryHandler implements QueryHandler<GetRoomByNameQuery, RoomSummaryView> {
    private final RoomQueryPort port;
    @Override public RoomSummaryView handle(GetRoomByNameQuery q) {
        RoomName name = RoomName.ofRaw(q.roomName());  // RAM self-defense; opaque, không parse ngược
        return port.findByName(name).orElseThrow(() -> new RoomNotFoundException("name=" + name.asString()));
    }
}
```

### 3.5 QueryBus — tương tự CommandBus, resolve qua `ResolvableType`.

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
                request.building(), request.floor(), request.capacity(), request.roomCode());
        CreateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }
    @PutMapping("/{id}/rename")
    ResponseEntity<RenameRoomCommand.Result> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newCode());
        return ResponseEntity.ok(commandBus.execute(command));
    }
    record CreateRoomRequest(String building, int floor, int capacity, String roomCode) {}
    record RenameRoomRequest(String newCode) {}
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
    @ExceptionHandler(DuplicateRoomException.class)   // 409
    public ResponseEntity<ErrorResponse> duplicate(DuplicateRoomException e) {...}
    @ExceptionHandler(RoomDomainException.class)     // 400
    public ResponseEntity<ErrorResponse> badRequest(RoomDomainException e) {...}
}
```
- Dùng `assignableTypes` để advice **chỉ** áp dụng cho 2 controller của module này, không ảnh hưởng module
  khác. Exception nghiệp vụ (`RoomDomainException` và subclass) không rò rỉ ra Shared Kernel.

---

## 5. Domain Modeling — Value Object một chiều (nhắc lại, ADR 0003)

- `RoomName` được **sinh ra TỪ** `RoomLocation` + `RoomCode` (`RoomName.of(location, code)`). Nguyên tắc
  một chiều: chỉ `coordinate → name`. Chuỗi `name` raw (vd `"F.02LAB"`) chỉ để **hiển thị** và để
  **match chính xác** khi truy vấn (`findByName(RoomName)` so khớp chuỗi, **không** parse ngược thành
  tọa độ). Khi đổi `code`, gọi `location` cũ + `code` mới để `RoomName.of` tái tính `name`.
- `RoomLocation` (`building`, `floor`) **immutable** — rename không thay đổi tọa độ. Relocation
  (`relocateTo`) là use case khác, tương lai (phát sinh `RoomRenamedEvent(LOCATION_CHANGED)`).
- Aggregate **ghi nhận event** thay vì publish trực tiếp: `RoomRenamedEvent` (record, `implements
  RoomDomainEvent`, thuộc sealed `RoomDomainEvent permits ...`). Event chứa đủ context cũ/mới
  (`oldCode/newCode`, `reason`, `location`, `occurredAt`) để module khác (Workshop) phản ứng sau này mà
  không cần gọi ngược. Xem `docs/architecture/diagrams/room-workshop-event-reaction.mermaid`.
- `changeCode(String)` trong `Room`: preserve building/floor, tái tính name, **idempotent** khi code
  không đổi (no-op, không event/không bump `updatedAt`), **reject** room `DEACTIVATED`
  (`IllegalRoomStateException`). Validate code qua `RoomName`/`RoomCode` VO.
- **Global invariant (trùng tọa độ) → Domain Policy + Factory (ADR 0005):** uniqueness là set-based invariant,
  Aggregate không tự chứng minh được → do `RoomUniquenessPolicy` (Domain interface, impl ở adapter) làm trọng tài.
  Tạo mới qua `RoomFactory.create(location, code, capacity)` (Factory gọi `policy.ensureUniqueOrThrow` rồi
  `Room.create` tự dẫn xuất name). Đổi identity qua `room.changeCode(newCode, policy)` /
  `room.relocateTo(newLocation, policy)` — aggregate vẫn là người quyết định (Rich). DB `uk_rooms_building_floor_code`
  là chốt thẩm quyền chống race (TOCTOU); Factory check chỉ là fail-fast/UX.
- **Reconstitution:** `Room.reconstruct(...)` dùng khi nạp từ DB — **không** qua `RoomFactory`, **không** check
  uniqueness, **không** phát event. Tách biệt hoàn toàn với đường tạo mới.

---

## 6. Checklist trước khi tạo PR

- [ ] Mọi class trong `internal/` là package-private (handler, port impl, mapper...), chỉ API công khai là `public final`.
- [ ] Command có nested `Result`; Query View nằm trong `port.in.query.view`. **Không còn** `XResponse` chung.
- [ ] Driving adapter: `RoomCommandController` (CommandBus) + `RoomQueryController` (QueryBus) tách rõ;
      `var x = new XCommand(...)` trước `execute`; request body qua nested `XxxRequest`.
- [ ] Exception nghiệp vụ tập trung ở `*ExceptionAdvice` scoped `assignableTypes`, nằm trong module.
- [ ] `internal/` không bị outside import (chạy build/ArchitectureTest xanh).
- [ ] Không đổi schema nếu dùng chung unique index `(building, floor, code)` làm DB gate.
- [ ] `./mvnw test` xanh toàn bộ, không regression.
