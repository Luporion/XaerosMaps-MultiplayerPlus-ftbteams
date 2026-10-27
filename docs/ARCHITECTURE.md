# v0.3.0 Architektur – getrennte persönliche Provenienz / mehrere gelesene Scopes

**Entwicklungsstand:** Quellcode-Testkandidat, NeoForge- und Minecraft-Tests ausstehend. Die folgende v0.2.0-Beschreibung bleibt als historische Dokumentation erhalten.

- Server speichert unter `scoped-v2/<private UUID|team UUID|global>/world_map/...`. Uploads werden immer privat und zusätzlich im ausdrücklich gewählten Freigabemodus abgelegt. Native FTB-Team-ID wird ausschließlich auf dem Server ermittelt.
- Der Client empfängt unter `PRIVATE` nur eigene Bereiche, unter `TEAM` bzw. `GLOBAL` zusätzlich berechtigte Team- und global freigegebene Bereiche. `S2CChunkData` trägt in **Protokoll 6** `scopeId` + `syncId`; Queue-Elemente haben dieselben Scope-/Generationsgrenzen.
- Client-Cursor in `scoped-v3/<scope>/<layer>`, getrennte Konflikt-/Revisionsstände. Zusammenführung: eigene native Tiles > PRIVATE > TEAM > GLOBAL. Nach Teamwechsel werden keine neuen unautorisierten Daten angenommen; XAERO-CACHE-WIDERRUF ist **nicht** umgesetzt.
- Persönliche Provenienz wird klientseitig atomar in `personal-provenance-v3.bin` erfasst: `NATIVE` (Xaero MapWriter), `VISITED` (tatsächlich betretener Oberflächen-Chunk), `RECEIVED` (aus geteiltem Server-Scope). Ein Besuch ist ein Ereignis, keine Garantie für neu berechnete native Kartenpixel.
- Datenschutz: An v0.3.0 gemischte bereits vorhandene Xaero-Caches ist retrospektiv keine sichere Herkunftsermittlung möglich. Vor produktiver Geheimhaltung sind zusätzliche Client-Overlay-/Cache-Schichten erforderlich.

---

# v0.2.0 architecture update – scoped-v2 (source candidate)

The old v0.1.0 notes below are retained as historical context, **not the active implementation**. Transport is enabled with protocol `5` in v0.2.0. See [Release/known limits](RELEASE_v0.2.0.md) and [Project status](PROJECT_STATE.md).

- All accepted locally sampled uploads are written to a personal `private-<UUID>` store; if opted into TEAM or GLOBAL, duplicated to the corresponding scoped cohort store.
- The server chooses the effective scope from authenticated player UUID, **live FTB party UUID**, and a requested sharing mode enum. It never accepts a client-supplied team ID as authority.
- Server checks the current scope before region indexes, on region requests, in the upload worker, before and after download token waiting, and before sync completion. Queue entries belong to the currently scoped subscription; stale scope sends are blocked.
- No legacy unscoped map file is opened. Client uses independent `scoped-v2/<scope>` revision cursor stores.
- A client selects **one scope** per connection. TEAM cannot also download GLOBAL. Existing received tiles remain merged into the regular Xaero map; revocation, simultaneous multi-scope rendering, and true historical visitation tracking are **not implemented**.
- The proximity upload check is only a plausibility bound, not proof of exploration against malicious clients.

---

# Rogler XMMP – architecture, evidence and trust boundaries

## Scope of v0.1.0

Based on `alinco8/XaerosMaps-MultiplayerPlus` upstream snapshot `7899261ebce9d04f5abf96e9f9578d69c63e7642` (2026-10-26). This fork keeps the `xmmp` mod ID to reuse Xaero mixins, but differs in its network protocol and disables all legacy terrain transfer. It is a **development foundation**, not the first working shared-map release. Source-level `SharingPolicy` tests are not proof of a NeoForge or multiplayer build.

XMMP upstream data flow:

`MapWriterMixin` → `MapSyncSession` → `ChunkUploader` → `C2SChunkUpload` → `ServerSession` → `ServerRegionStore(world_map/<dim>/<layer>)` → player `Outbound` → `S2CChunkData` → `XaeroController`.

Upstream indexes `.xmsr` map data only by `(dimension, layer)`; every subscriber on that dimension can receive terrain. Authorizing **only broadcasts is insufficient**: region-index responses, new region-sync requests, upload paths, queued packets and cached cursors must all be scoped. Upload data must not enter the old global store before verifying ownership/scope.

### Foundation changes

- `core/sharing/SharingPolicy.kt`: pure immutable PRIVATE/TEAM/GLOBAL policy and per-publication captured party UUID (not derived from current team name). `TERRAIN_TRANSPORT_ENABLED=false` prevents unscoped data transfer across all entry points, no runtime override in 0.1.0.
- `ServerHost.getPartyTeamId(UUID)` null-default API for loaders not yet implemented; `ServerHostImpl` binds a verified optional FTB Teams 2101.x adapter in NeoForge.
- `FtbTeamResolver`: checks `FTBTeamsAPI.api().isManagerLoaded`, `getManager().getTeamForPlayerID`, `isPartyTeam`, `getMembers` and `getId`; fails closed if the mod is absent, unloaded or misbehaving; no secondary Rogler Core team DB. Its integration with your real mod stack still needs verification.
- `ClientPacketHandlers` blocks incoming terrain packets before decoding and `XMMPClient` skips all standalone/minecraft terrain sessions, preventing legacy/fake servers from rewriting client terrain.
- `ServerSession` blocks player state creation, handshakes, dimension index, uploads, background upload/download worker, region requests and ACKs. `ServerPacketHandlers.handle` rejects all terrain packets *before decoding* while disabled.
- Packet protocol is `4`, preventing reuse of the original protocol `3` channel. The old `world/xmmp/world_map` remains untouched and NOT consulted for gameplay; it is NOT silently migrated or reassigned to a team. Existing client-side Xaero data remain unmodified by the fork.
- `syncCaves=false` by default. Matches user requirement to avoid disabling Xaero's normal World Map cave layers (XMMP cave streaming has upstream compatibility limitations).

### v0.2.0 intended storage

Key personal terrain by `(ownerUuid, dimension, layer, region)`, not solely by dimension/layer. Keep personal exploration immutable with respect to changes to sharing mode. Grants use `SharedMapGrant(owner, mode, originalPartyTeam)`; publication cohort is captured when data is first published and never silently transferred to a *new* team. Server must check membership again for every queued delivery. Future revocation requires client overlay separation, including native exploration provenance tracking. Need define a durable grant journal, private tile ownership verification and snapshot/migration version before enabling network transport.

**Never trust:** team IDs, grant owner, dimension and explored status supplied by the client. Obtain UUID and team from NeoForge/FTBTeams on the server. Full ownership validation (actual visitation) is not yet implemented; payload decoding alone is not proof of legitimate exploration.

### Compatibility matrix to verify

Minecraft 1.21.1, NeoForge 21.1.249, FTB Teams 2101.1.11, Xaero World Map 1.45.0, Xaero Minimap 26.4.2, XaeroPlus 2.36.0, Sable Xaero Bridge and Rogler SMP Core 0.8.1. XaeroLib/KotlinForForge/YACL dependencies must resolve at build and runtime. The upstream Gradle configuration previously targeted newer versions; the build now pins the matching NeoForge/Xaero values for the 1.21.1 NeoForge node, but a successful compile is not yet proven.

## Provenance rules for v0.3.0

Separate `selfExplored` from `receivedTiles`. A new self visit must be recorded even when the region is already displayed from a teammate. On revocation, display `selfExplored UNION currently permitted receivedTiles`. Avoid reuploading received data as newly explored tiles. Historical Xaero files already merged without provenance cannot be automatically unmerged; handle pre-fork data separately. Never overwrite or erase native Xaero files during early development.
