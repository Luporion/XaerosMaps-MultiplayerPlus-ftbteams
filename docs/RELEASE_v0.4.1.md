# v0.4.1 – PvP / Fog-of-War global sharing safety hotfix

Base: v0.4.0, branch `feat/v0.4.0-ingame-ui`, commit `5207ae8f` (CI green).

## Behavior
- Both Terrain Sharing and OP Admin menus show prominent red GLOBAL danger labels.
- Cycling to GLOBAL highlights the setting in red and shows an explicit enemy-team warning.
- Before saving a **new** GLOBAL publication, the user must check the dedicated acknowledgement, else the previous publishing mode is retained. The acknowledgement is not persisted.
- Before an OP newly enables GLOBAL server-wide, the OP must check a separate acknowledgement or the server-wide value stays OFF.
- New worlds default `allowGlobal=false`. Existing world `admin-controls.properties` values remain authoritative; admins must opt in to turn a previously disabled value ON.
- Receiving globally published terrain is NOT equivalent to publishing one's own terrain; receiving global maps defaults to OFF for new client configurations. Existing client configuration values are preserved.
- Disabled global publishing does not delete or invalidate already copied enemy Xaero map data. This hotfix provides warnings and opt-in, **not** pixel-cache revocation.
- No packet schema changes; protocol 7. Same mod ID: replace v0.4.0 on clients and server with v0.4.1. Never install both versions simultaneously.

## Testing
- Source checks in `scripts/verify-v041-warning.py` and existing `VERIFY.cmd` checks.
- Build via the existing `PATCH.cmd` + GitHub Actions monitor. Local full Gradle build may not be possible in this environment.
- Gameplay privacy checks still required for TEAM A vs TEAM B, PvP/Fog-of-War, reconnect and packet authorization.
