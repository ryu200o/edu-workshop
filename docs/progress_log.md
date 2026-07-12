# Progress Log (tracked)

Public, version-controlled milestone log. Concise entries; newest at the bottom.

## Room module
- **Domain core** — Rich Domain `Room` aggregate + `RoomLocation` / `RoomName` value objects
  (self-normalizing, self-defending); pure Java, no framework.
- **Application packaging** — golden Application layout (`port/in/{command,query}`, `port/out`,
  flat `handler`, `event`, `mapper`); ADR 0002 (reject `domain/spi`, package-private handlers,
  CQRS bypass, per-module buses).
- **CQS foundation** — shared kernel `shared/cqs` (`Command`, `CommandHandler`); `shared` is an
  OPEN Spring Modulith module.
- **Use Case: Create Room (in-RAM)** — full write vertical slice
  `CreateRoomCommand → CommandBus → CreateRoomCommandHandler`, enforcing the multi-tier duplicate
  guard (RAM local invariants → DB global check → persist). Backed by an in-memory adapter
  (`ConcurrentHashMap`) implementing `RoomExistencePort` + `RoomStateGateway`. Handlers/bus are
  package-private. 36/36 tests green.
- **Read side: CQS Query slice (in-RAM)** — shared kernel `Query` / `QueryHandler`; Room
  `QueryBus`, `GetRoomByIdQuery` / `GetRoomByNameQuery`, `RoomResponse` projection, and a
  consumer-driven read-only `RoomQueryPort`. CQRS bypass: the in-memory adapter maps
  `Room → RoomResponse` entirely in infrastructure; handlers are package-private and
  `@Transactional(readOnly = true)`. Malformed names are blocked in RAM before hitting the port.
  Full CQS read/write vertical slice for Room complete on RAM. 42/42 tests green.

- **Persistence: real JPA adapter + Flyway (H2/PostgreSQL)** — Flyway `V1__create_rooms.sql`
  (`rooms` aligned to the aggregate: `id, name, building, floor, code, capacity, state, timestamps`;
  composite unique `(building, floor, code)`). `JpaRoomAdapter` (+ `RoomJpaEntity`, `RoomJpaRepository`,
  all package-private) implements the write/existence/read ports and replaces the in-memory adapter;
  the global-uniqueness gate uses the composite key (`existsByBuildingAndFloorAndCode`). Integration
  tested end-to-end via the ports over H2 (PostgreSQL mode) with `ddl-auto=none` (Flyway owns schema).
  Module scaffold aligned to `driven/persistence`. 47/47 tests green.

## Next
- Driving adapter (REST controller / module_api) exposing the Room use cases.
