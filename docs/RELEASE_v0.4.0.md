# v0.4.0 – In-game settings, verified status and server admin controls

**STATUS: SOURCE CANDIDATE, COMPILATION AND DEV-MULTIPLAYER TEST REQUIRED.** Base v0.3.0 commit `6655448c` has green CI (run `40605026637`), but no verified real multiplayer gameplay tests.

## Client

The existing YACL screen now has a **Sharing** category. Select `PRIVATE`, `TEAM` or `GLOBAL` as publication mode. Set separate receive toggles for TEAM and GLOBAL. Saving changes to these client options re-handshakes and requests a new dimension index; no server restart is required. `PRIVATE` continues to receive only personal exploration regardless of checkbox state. When in TEAM/GLOBAL, the checkboxes control the two optional reads independently. TEAM publication with no verified FTB party (or disabled by admin) securely falls back to the player's personal scope, without opening any team data.

**Server Status** is reported by an authenticated server packet. The read-only YACL section reports effective server publishing scope and verified FTB party UUID; it does not derive team membership from the client's own config. An admin-only category appears only after the server confirms the client is OP. Status also includes a session-local snapshot of scoped sources and native/received terrain tiles and received payload KB. These are session counters, not durable global transfer totals. UI labels are provided in English and German.

## Admin

Authorized operators can toggle `allowTeam`, `allowGlobal`, and `allowUploads` in YACL. The server verifies `hasPermission(2)` on receipt regardless of UI state. Default is all enabled (preserves v0.3.0 behaviour), unless an existing file is missing/corrupt properties, then those flags fail closed. Changes are stored in the per-world XMMP data folder as `admin-controls.properties`; a temporary file is replaced atomically when supported. Disabling TEAM or GLOBAL blocks further transfer immediately through per-request, index, upload worker and outbound checks. Enabling requires clients to renegotiate/reconnect; no old Xaero caches are erased.

The server's immutable `xmmp-server.toml` remains the place for upload/download quotas and syncCaves; these are **not** live-editable in v0.4.0. The admin UI only controls the three security-relevant global gates.

## Safety and limitations

- Packet protocol 7. Requires identical client/server version and replaces prior XMMP; never run both JARs.
- Authentication for terrain transfer remains restricted to real in-game Minecraft server transport. Standalone/plugin mode cannot share terrain.
- A player cannot claim an arbitrary team UUID; all FTB party IDs are obtained via server resolver.
- Existing third-party Xaero local caches are **not revocable**. Team changes stop new delivery; they do not erase already revealed map images.
- Full pixel-level standalone provenance / overlays, cave compatibility and real adversarial multiplayer tests still require development. No productive rollout recommendation until these pass.

## Build and tests

The new CI builds NeoForge 1.21.1 and executes `:core:test` and in-module JUnit tests. 57 policy assertions run as Kotlin CLI checks. Additional JUnit checks cover persistent server admin configuration and malformed permissions files. `scripts/verify-v040-settings.py` checks wiring from the YACL GUI to packets and server ACLs. `scripts/test-build-monitor.py` remains intact. Full Gradle build must be checked in GitHub Actions before claiming v0.4.0 binary compatibility.

**DEV checks:** 1) two FTB parties A/B, 2) A selects TEAM and enables receiving; B sees nothing, 3) independently disable team/global downloads and verify, 4) OP disables team/global during active traffic and verify no new transfers, 5) non-OP crafts admin packet and sees no effect, 6) reboot, verify admin settings persist, 7) check own exploration remains visible, 8) cave maps, XaeroPlus and Rogler Core startup and team changes, 9) restore backups after experiments.
