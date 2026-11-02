# Current XMMP fork state

**v0.4.2 SOURCE CANDIDATE (2026-11-02).** Based on v0.4.0 CI-green commit `5207ae8f` (run `40609501149`), including v0.4.1 GLOBAL warnings, plus v0.4.2 publication/reception separation, region retry queue and server/client transfer diagnostics. v0.4.2 has not yet passed full NeoForge CI or Minecraft multiplayer tests.

Protocol **8** requires the same version on the dedicated NeoForge server and each client. GLOBAL is disabled by default on freshly configured servers, and receiving GLOBAL stays off by default. No safe old-map import, pixel-level cache revocation or 8–12-player load certification yet.

See [v0.4.2 release](RELEASE_v0.4.2.md), [roadmap](ROADMAP.md) and [test matrix](TEST_MATRIX.md).
