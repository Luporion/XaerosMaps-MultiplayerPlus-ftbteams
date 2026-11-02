# XMMP FTB-Teams v0.4.2 – Diagnostik und Synchronisierungsfix

**Status: SOURCE CANDIDATE.** Built from the **v0.4.0 CI-green branch** (`5207ae8f`) and **includes the v0.4.1 GLOBAL PvP/Fog-of-War warning hotfix**. Full NeoForge compilation and dedicated-client/server runtime tests are NOT confirmed for v0.4.2. Do not install on the live competitive world yet. Always back up world/Xaero map data.

## Corrected permissions

* Publishing and receiving are independent. `Publish PRIVATE` with `Receive team exploration = on` may download the **server-authenticated FTB party** map without uploading private exploration to it. GLOBAL reception is separately gated and defaults off.
* `validPublication` is separate from `validScope` (receiving authorization). TEAM publishing no longer fails simply because the player disabled reception of TEAM terrain. Neither gives access to a different party or another user's PRIVATE storage.
* Authorization is rechecked before downloading, including for queued data, and team IDs come from the dedicated server's FTB API.
* Existing GLOBAL publishing warnings, player confirmation, OP confirmation and new-world default GLOBAL=off from v0.4.1 are preserved. **Received Xaero map pixels cannot yet be revoked** after a team switch.

## Reliable region requests

* Requests are tracked by dimension/session, layer, scope and region. They are rate-limited, deduplicated and retried until `S2CRegionSyncDone` is processed after map writes and the scoped cursor is advanced. Exponential backoff from 15 to 180 seconds; a maximum of seven attempts and 16,384 pending regions per active client map session.
* Changing map generations clears old pending requests. Retrying does not bypass team ACL or the server-side request limiter.
* Requests that still fail after seven attempts are reported as `expired` in the client diagnostics. Server rate-limit messages can still occur; they should no longer cause a permanently silent omission during an otherwise connected session.
* No automatic initial import of old Xaero cache files: native ownership cannot safely be established for data recorded before the fork.

## Diagnostics and load

* The in-game **XMMP Status** tab shows server-confirmed effective publishing scope, receipt permissions and per-session server metrics: `uploads stored`, `uploads rejected`, `tile packets sent`, `bytes sent`, `region requests`, `regions throttled`.
* Client counters show native tile writes, received tiles/bytes and queued/retried/expired region requests. The server reports these every 20 seconds; reopen the status menu to see a new snapshot.
* Metrics count accepted/stored uploads and packets sent, **not client rendering confirmation**. A server rejection may still ACK the upload for flow-control; the client does not automatically reupload such a rejected tile.
* Upload batch capped at 32 and payload validation is delegated to a bounded worker invocation on `Dispatchers.Default`; no claims of measured TPS improvement without an A/B test.
* Protocol **8**: only v0.4.2 clients and server may connect for XMMP sharing. Remove older XMMP jars on both sides. The fork remains a single `xmmp` mod ID.

## Remaining limitations

* Server-side accepted uploads depend on native Xaero tile generation. Older personal Xaero terrain is **not** automatically imported, and previously received tiles are **not** deleted at team exit. Fog-of-War privacy is not fully reversible.
* A player moving more than 24 chunks from a tile before upload validation can have an upload rejected. It is counted, but is not automatically resent in this version.
* No confirmed 8–12-player performance benchmarking, XaeroPlus/Rogler SMP Core compatibility tests or true multi-client PvP privacy tests yet.
* Validate with a DEV copy before merging into main or deploying to the production SMP server.

## Install and CI

This combined patch is generated against the **clean v0.4.0 branch** `feat/v0.4.0-ingame-ui` at `5207ae8f`. If v0.4.1 was already applied locally, don't apply the combined patch twice; use a clean branch from v0.4.0. Run `PATCH.cmd` on a new feature branch, which invokes local source checks, pushes and watches GitHub Actions. A green GitHub Actions compile/test run is the minimum deployment gate; runtime still requires a separate multiplayer test.
