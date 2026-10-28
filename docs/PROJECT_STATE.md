# Current XMMP fork state

**v0.4.0 SOURCE CANDIDATE (2026-10-28).** Base v0.3.0+JUnit buildfix commit `6655448c` passed GitHub Actions run `40605026637` and produced `xmmp-0.3.0+1.21.1-neoforge.jar`; there is no confirmed Minecraft multiplayer testing.

v0.4.0 introduces in-game YACL publication/reception controls, server-verified party/status packet, OP-only admin UI, server-side admin permissions for TEAM/GLOBAL/uploads, durable fail-closed admin settings, and protocol `7`. New build and actual DEV multiplayer/privacy tests are required.

**Unresolved:** old Xaero map images cannot be erased on team change, full pixel-owning overlays and in-game privacy verification. Do not deploy to production yet.

Details: [v0.4.0 release](RELEASE_v0.4.0.md), [roadmap](ROADMAP.md), [test matrix](TEST_MATRIX.md).
