# Rogler Xaero Map Sharing Roadmap (fork)

## v0.1.0 – FOUNDATION (source candidate)

- [x] MIT-preserving fork version and metadata, upstream separate from Rogler SMP Core.
- [x] NeoForge 1.21.1 Gradle compile dependencies pinned to server's NeoForge 21.1.249 and Xaero 1.45.0 / 26.4.2.
- [x] Pure sharing policy tests for PRIVATE/TEAM/GLOBAL, old-team grant semantics, and missing-team denial.
- [x] Optional FTB Teams API adapter reading server-authoritative party/team IDs.
- [x] Fail-closed client/server old global terrain uploads/downloads/indexes; packet version 4; old world data preserved untouched.
- [x] New CI workflow for NeoForge JAR and safety tests; scripts for local checks.
- [ ] Full NeoForge CI compilation and a real DEV client/server startup.
- [ ] Confirm XaeroPlus, FTB Teams and Rogler Core coexist in the same DEV profile.

## v0.2.0 – PER-PLAYER STORAGE + PRIVATE/TEAM/GLOBAL (SOURCE CANDIDATE)

- [x] Own/private `.xmsr` copies and separate team/global scoped stores; guard old unattributed shared stores. [ ] Per-tile owner manifest, migrations and revoke metadata.
- [x] Client TOML PRIVATE/TEAM/GLOBAL choice in handshake; server resolves and verifies actual FTB party; not yet exposed in YACL UI.
- [x] Scope authorization integrated into upload, indexes, region requests, queued chunks, ACK and download reads. [ ] Network latency adversarial tests / team-change DEV validation.
- [x] Basic vicinity bound (24 chunks), size/payload checks. [ ] Durable server visit attribution and prevention of fabricated nearby map data.
- [x] Server scope recheck and protocol 5. [ ] Prove no leakage during team changes in DEV, erase local foreign tiles later.
- [x] Fail-closed PRIVATE default. [ ] Server admin policy for disallowing GLOBAL, simultaneous GLOBAL+TEAM subscriptions, UI.

## v0.3.0 – OWN EXPLORATION PROVENANCE

- Keep personally visited areas recorded independently of received/shared tiles, even if same tile had already been displayed.
- Overlay permitted team/global terrain onto the personal native map without reclassifying it as personal exploration.
- Dimension/cave and multiworld scope, cache and cursor isolation; reconnect reconciliation and bounded downloads.
- Optional revocation feasibility prototype, without destructive changes to Xaero data.

## v0.4.0 – CONFIGURATION & ADMIN UI

- YACL private/team/global selection, upload/receive controls, admin rules, team status and sync diagnostics.
- Config reload, audit counters and rate-limit status, localized descriptions; no forced client restart where avoidable.

## v0.5.0 – PERFORMANCE, SECURITY & RELEASE

- Multiplayer simulation (8–12), fast-flight and very large terrain; allocation/network budgets and persistence benchmarks.
- Tests with XaeroPlus and the Sable Xaero Bridge; cave/multiworld/schematic/waypoint interactions.
- Backup, upgrade and rollback tooling; reproduce and triage upstream updates separately.

## v0.6.0 – OPTIONAL SHARING REVOCATION

- Unpublish only tiles that are not personally explored; purge ephemeral shared cache after policy changes or team exit.
- Preserve overlapping exploration and record personal visits that occur *after* receiving a shared tile.
- Never promise rollback of unattributed exploration merged before installing the fork.
