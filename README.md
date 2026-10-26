# Rogler Xaero Map Sharing – v0.1.0 Foundation

> **SECURITY STATUS: Terrain sharing is deliberately DISABLED in v0.1.0.** Do not use this release to enable team exploration yet. The legacy global map store is not safe for competitive teams. This fork preserves Xaero's native personal map; it does not exchange map tiles until scoped stores/authorization are complete.

Fork of [Alinco8/XaerosMaps-MultiplayerPlus](https://github.com/Alinco8/XaerosMaps-MultiplayerPlus), MIT license and upstream attribution preserved. **Mod ID stays `xmmp`. Install this fork INSTEAD of the upstream XMMP JAR, never alongside it.**

- Target: Minecraft **1.21.1**, NeoForge **21.1.249**, Xaero World Map **1.45.0**, Xaero Minimap **26.4.2** (run-time compatibility with other mods such as XaeroPlus must still be tested).
- `SharingPolicy.kt`: server-authoritative private/team/global authorization and captured publication-party semantics for a later release.
- `FtbTeamResolver.kt`: optional FTB Teams `2101.1.11` API adapter. Missing team or mod means no team privileges. No direct dependency on Rogler SMP Core.
- Protocol **4** (formerly 3) and fail-closed inbound/outbound terrain gates; prevents loading legacy global terrain from the `xmmp` world folder.
- `syncCaves` defaults to **false** so normal Xaero cave world maps remain available once sharing is reintroduced.
- `docs/ROADMAP.md`, `docs/TEST_MATRIX.md`, `docs/ARCHITECTURE.md` describe the staged development and privacy safeguards.

**Build / verify:** Run `VERIFY.cmd` (Windows) or `python3 scripts/verify-foundation.py`, then check the GitHub Actions workflow `Rogler XMMP v0.1.0 NeoForge Build`. This local workspace cannot currently resolve Gradle distribution downloads, so **a successful NeoForge build is not yet demonstrated**. Do not deploy to production before CI and a separate test client/server pass.

---

# Original upstream documentation

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
