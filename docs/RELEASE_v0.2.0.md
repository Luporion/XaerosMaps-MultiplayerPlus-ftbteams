# XMMP FTB Teams Fork v0.2.0 – Scoped Terrain Sharing

**Status: SOURCE CANDIDATE.** Based on v0.1.0 + Build Monitor `fad251f7` source. **No complete Gradle/NeoForge build or client/server Minecraft integration test has been performed in this environment.** GitHub Actions is the next mandatory gate.

## Implemented

- Transport is enabled only on the authenticated in-game Minecraft server adapter. Standalone and external plugin hosts (which cannot authenticate player UUID under the current host contract) stay disabled by default.
- New XMMP protocol `5` (both client and server must use this fork/version); legacy 0.1.0 `4` network packets are not used.
- An explicit local client setting `terrainSharingMode = "PRIVATE" | "TEAM" | "GLOBAL"` is transmitted as a mode ordinal at handshake, **without transmitting or trusting a team UUID**. Default `PRIVATE`; change requires reconnect. No new UI yet (scheduled v0.4.0).
- A dedicated `scoped-v2/` server hierarchy replaces the old unscoped `world_map/` store; no automatic import of historical unattributed global data.
- Every accepted locally recorded chunk is saved in `private-<player UUID>`; if the player's selected mode is `TEAM` or `GLOBAL`, it is also written to `team-<server-verified FTB party UUID>` or `global` respectively. Personal data remains independently stored regardless of publication.
- FTB Teams 2101.1.11 party UUIDs resolved server-side by FTB's `isPartyTeam`, `getMembers`, `getId` APIs. Missing FTB party denies TEAM (fail-closed).
- Authorized **single-scope subscription**: PRIVATE downloads your personal scope, TEAM downloads the current party scope, GLOBAL downloads the global scope. Indexes, region requests, queued chunk data, upload worker, and final send recheck the actual current server-side team and dimension.
- Client cursor paths are separate per scope under `scoped-v2/<scope>/...`, so a previously synced region in one team cannot be reused to suppress downloads in another scope. Last-issued revision indexes and downloaded chunks still write into ordinary Xaero's World Map tiles.
- Bounded tile payload decoding (262,144 bytes) and map index (16,384 regions), bounded outbound queues and upload validation with a current-player proximity check (24 Minecraft chunks), plus the existing payload codec validation. These checks mitigate simple spoofed coordinates but **cannot prove historic exploration against a malicious client**.
- The optional v0.1.0 FTB resolver continues to fail closed when absent or malfunctioning. No new Rogler SMP Core dependency, nor changes to FTB claims/teams or world data.

## Critical limitations / safeguards

- **PRIVATE/TEAM/GLOBAL is the single selected scope for both publishing and receiving.** A player selecting TEAM does not additionally receive GLOBAL in this v0.2.0 build; all recipients who wish to see global terrain must also select GLOBAL. This is a first functional scoped sharing version, **not** the final simultaneous global+team overlay.
- The native Xaero cache still merges received tiles. When leaving a team or changing settings, the server refuses new disallowed data, **but already downloaded foreign terrain remains in Xaero until cleared manually**. Original personal and foreign exploration cannot yet be safely separated in client cache; do not deploy in a setting requiring immediate revocation. This is intentionally deferred to v0.3.x/0.6.x.
- Source's own exploration upload is triggered by Xaero's `MapWriter` block-writing hook; no durable per-player visited-chunk proof / anti-spoof registry exists. The server's position check is an approximation, not cryptographic evidence. Already explored historic chunks outside player proximity cannot be bulk uploaded in this build.
- Shared team-scope chunks are merged within that team for performance; per-player private copies remain separate, but group-bucket tiles currently lack per-pixel owner lineage. Do not promise retroactive selective unsharing or ownership inference for pre-existing group data.
- When two players publish the same tile, the latest accepted scope write wins. Server revisions guard syncing but this is not a pixel-wise map merge.
- A team change while still connected can suspend that session until a reconnect or dimension synchronization. Permission checks block stale queued outbound packets but cannot erase information already transmitted.
- `GLOBAL` is a deliberate potentially public publication mode. Players can select it themselves; there is not yet an admin global-block switch. Backups and permission review before use.
- Build currently targets NeoForge 21.1.249 and Xaero WorldMap 1.45.0 / Minimap 26.4.2 as configured by the fork. XaeroPlus and Sable Xaero Bridge gameplay coexistence remains untested.

## Client configuration

Edit `config/xmmp.toml` (the existing YACL/TOML XMMP config) **before joining**, on every receiving/sending client:

```toml
terrainSharingMode = "TEAM" # PRIVATE (default) | TEAM | GLOBAL
```

Select `TEAM` for a party-only collaborative map or `GLOBAL` for a common server map. `PRIVATE` does not publish to others. Players in no FTB party cannot use TEAM. Restart client or reconnect after changing mode. Everyone needs the same new fork JAR and protocol 5.

## Essential two-party DEV tests before server rollout

1. Build source in Actions (`WATCH.cmd`), check produced JAR version and no older `xmmp` JAR left in mods directories. Do not interpret policy-only tests as runtime proof.
2. Client A1 (TEAM) explores within 24 chunks of its server position; A2 (same FTB party, TEAM) downloads the region; B1 (different FTB party, TEAM) receives **no region indexes or chunks** from party A, including when OP.
3. Verify each player also writes a server-side `scoped-v2/private-<UUID>/...` region and A's party store under `scoped-v2/team-<UUID>/...`. Legacy unscoped `world_map/` must not be read by the new protocol.
4. TEAM→GLOBAL opt-in and reconnect: players selecting GLOBAL may see global chunks; TEAM-only client does **not** see GLOBAL in this version. PRIVATE cannot share with others.
5. Change FTB party while a region sync is in flight: no new chunks after permission loss; stale region/queue requests must be rejected. Relaunch/reconnect before testing new team subscription.
6. Nether/Overworld/cave layer tests, XaeroPlus + Sable Bridge, restart, fast-flight 8–12-player load/CPU/memory/tile rates and invalid/malformed packet tests. Existing client Xaero foreign tiles remain locally cached (known limitation).

## Rollback

Backup full world, old XMMP `world_map/` directory, clients' Xaero maps, and new `scoped-v2/` before DEV. Do not overwrite the old XMMP data with scoped storage. Install the *same* previous jar on all clients/server to roll back; no automatic migration is provided. Preserve scoped data separately for later upgrades.
