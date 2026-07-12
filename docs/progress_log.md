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

## Next
- Room Query Side (read flow).
