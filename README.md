# Xaero's Maps: Multiplayer+ – FTB Teams Fork v0.4.0 (DEV Source Candidate)

**Build target:** Minecraft 1.21.1, NeoForge 21.1.249. Last **verified successful CI build** is v0.3.0 (GitHub Actions run `40605026637`, commit `6655448c`). **v0.4.0 is not yet CI-compiled or tested inside Minecraft.** Do not deploy to the live competitive server yet.

- **In-game settings (YACL):** choose which terrain you publish (`PRIVATE`, `TEAM`, `GLOBAL`); separately toggle permitted team and global reception. Saving changes renegotiates permissions and restarts the map sync without a server restart. `PRIVATE` is strictly personal, including received data.
- **Server-confirmed status:** publishing scope, verified FTB party UUID (if any), and allowed reception. The team ID is obtained on the server, never asserted by a client.
- **Admin controls:** only players whose **server-side OP permission >= 2** is verified may see the admin YACL category. They can enable/disable team sharing, global sharing and new uploads. The server checks permission AGAIN on every request. Settings persist per world under `world/xmmp/admin-controls.properties` (exact world path depends on server installation), via atomic file replacement.
- **Security:** Scope membership checked before every sensitive async transfer. Disabled team/global sharing immediately blocks further sending. Clients should reconnect after changing admin switches; re-enabling requires a new sync handshake. Client requests are not an authorization token.
- **Protocol 7:** v0.4.0 must be installed on BOTH server and clients. It replaces the old `xmmp` jar (do not run side-by-side). Standalone/plugin transports remain disabled for authenticated terrain sharing.
- **Limits:** No safe retroactive removal of already received Xaero terrain on team exit; cave sync remains disabled by default; no complete pixel-level personal overlay yet. Backup Xaero data and test privacy with two adversarial teams in DEV before productive deployment.

**Build:** `VERIFY.cmd` for source/monitor checks; use `PATCH.cmd` to apply the v0.4.0 patch, push and watch GitHub Actions. Downloaded JAR is under `artifacts/` after the Actions build and tests succeed. [v0.4.0 release notes](docs/RELEASE_v0.4.0.md), [roadmap](docs/ROADMAP.md), [test matrix](docs/TEST_MATRIX.md).

**Upstream:** fork of [Alinco8/XaerosMaps-MultiplayerPlus](https://github.com/Alinco8/XaerosMaps-MultiplayerPlus), MIT license and attribution preserved.

---

# Original upstream documentation

> The section below is preserved **only for attribution and upstream reference**. Its standalone/plugin setup, old all-player terrain sync, and default settings do **not** describe this v0.2.0 fork. The scoped v5 protocol currently enables terrain sharing only with an authenticated Minecraft server adapter. Please follow our [v0.2.0 notes](docs/RELEASE_v0.2.0.md).

# Xaero's Maps: Multiplayer+

[![wakatime](https://wakatime.com/badge/user/e0eb8abf-071e-4f83-bc68-6f100cbe60db/project/642dbe81-8cf7-433e-be4e-5bb47ddbfce6.svg)](https://wakatime.com/@e0eb8abf-071e-4f83-bc68-6f100cbe60db/projects/cmtsadragl)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1525919?logo=curseforge&style=flat)](https://www.curseforge.com/minecraft/mc-mods/xaeros-maps-multiplayer-plus)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/stTaMuWa?logo=modrinth&style=flat)](https://modrinth.com/mod/stTaMuWa)

## 🗺️ Overview

This mod extends the multiplayer features of Xaero's World Map and Minimap

## 🤔 F.A.Q.

### The cave layer feature on the World Map doesn't work!

This happens when the mod's cave map syncing is enabled (it is by default). While it's on, Xaero's
World Map cave layers are disabled. The Minimap isn't affected. If you want the World Map cave
layers back, you'll have to give up cave syncing: set `syncCaves` to `false` in
`config/xmmp-server.toml`

## 🚀 Features

### World map syncing with other players

(Player icons are a built-in Xaero's Minimap feature. Set `everyone_tracks_everyone` to `true` in
`config/xaero/lib/common.cfg`)

![world_map_syncing.webp](https://github.com/alinco8/XaerosMaps-MultiplayerPlus/blob/main/assets/features/world_map_syncing.webp?raw=true)

### Plugin support

If you can't run mods on your server, you can still sync maps using a plugin:

1. Download xmmp-plugin.jar from the
   [latest GitHub Release](https://github.com/alinco8/XaerosMaps-MultiplayerPlus/releases/latest)
2. Put it in the server's plugins folder

### Standalone mode

If you can't install mods or plugins, or the server isn't yours, you can use standalone mode.

Host setup:

1. Download xmmp-standalone.jar from the
   [latest GitHub Release](https://github.com/alinco8/XaerosMaps-MultiplayerPlus/releases/latest)
2. Move it to a suitable folder (it creates a run folder, so I'd avoid running it straight from
   your Downloads folder)
3. Start it with something like `java -jar <downloaded file name>` (explaining how to run a jar is
   out of scope here, so please look it up)
4. Open the port (default 25580) and share your IP and port with everyone else

Player setup (host included):

1. Join the world
2. Go to Mods menu → Xaero's Maps: Multiplayer+ → World Specific → Server Address and enter the
   server's IP and port (this won't show up until you've joined a world)
3. Rejoin the world

### Planned

- Make the Xaero's World Map / Minimap features that require a server work with standalone / plugin
  too (e.g. player icons on the map)
- Server-side map generation (accurate and safe, and can generate areas that haven't been loaded
  yet)
- Waypoint set sync (additions/deletions automatically synced between players)
- Direct waypoint sharing (via in-game chat, or copy-paste for sharing outside Minecraft)
- Server-managed waypoint sets

Suggestions for other features are very welcome!

## ⚙️ Configuration

Many of this mod's settings can be customized. You can change them in the in-game settings screen
(powered by YACL) or by editing the config file directly

## 🫶 Support

If you enjoy the mod, you can support its development by getting a server through the banner below
or by donating on [Ko-fi](https://ko-fi.com/alinco8)!

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/46b5e2aa-795e-4034-b94d-2247b5b3c7bf.webp)](https://short.alinco8.dev/bh)
