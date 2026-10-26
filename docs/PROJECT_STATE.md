# Current status – XMMP FTB Teams Fork

**v0.2.0 source candidate, 27 October 2026.** [Release/risks](RELEASE_v0.2.0.md) · [Roadmap](ROADMAP.md) · [Test matrix](TEST_MATRIX.md).

- Last confirmed full NeoForge CI build: **v0.1.0**, Run `40417466736`, commit `fad251f7`; JAR `xmmp-0.1.0+1.21.1-neoforge.jar` downloaded. A Minecraft runtime test was not confirmed in the uploaded terminal log.
- v0.2.0: scoped storage, server-authoritative TEAM UUID, separate private copies, per-scope cursors, v5 packets, conservative upload checks and queue reauthorization implemented **as source**.
- Automated local checks: 40 Kotlin policy assertions; scoped structural security checks; existing Build Monitor tests. Full Gradle wrapper build blocked because `services.gradle.org` cannot be resolved in this environment. **No proof of a successful NeoForge compile or gameplay behavior for v0.2.0.**
- v0.2.0 does not offer simultaneous TEAM+GLOBAL reception, revocation of downloaded tiles, durable personal visit events for overwritten map tiles, or strict prevention of malicious near-player fabricated uploads. These are not claimed as finished features.
- Immediate next gate: GitHub Actions on `feat/v0.2.0-scoped-terrain`, then DEV 2-party/OP test and client Xaero cache review before deployment.
