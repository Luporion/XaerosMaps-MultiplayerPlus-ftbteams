# v0.2.0 required DEV regression matrix

**These checks remain unverified in Minecraft.** First perform CI compile, then test with 2 distinct FTB parties and OP.

| Test | Required result |
|---|---|
| A1 TEAM discovers terrain, A2 TEAM same party | A2 sees terrain and shares one party scope |
| B1 TEAM different party / OP | No A team region index or chunk packets |
| GLOBAL clients | Common global scope; TEAM-only clients are not global subscribers yet |
| PRIVATE A1 | Own server private cache, no broadcast |
| FTB Teams absent / no party | TEAM fails closed |
| Team changes during queues, relay or download ACKs | Old scope denied before packet send |
| Fake scope ID in C2SRegionSync | Rejected, no file reads of requested scope |
| Old XMMP protocol 4 | Not admitted into protocol 5 |
| Upload > 262144 bytes, distant/fake dimension | Rejected |
| Legacy world_map files | Never read or used as current-team data |
| Client restart, Nether, cave mode, XaeroPlus | Test persistence/compatibility |
| Team leave after tiles previously received | **Known limitation**: local Xaero cache retains downloaded terrain |
| Fast-flight 8–12 players | Monitor MSPT, memory, rate-limiter and disk writes |

---

# DEV acceptance test matrix

| ID | Scenario | Expected result | v0.1.0 |
|---|---|---|---|
| F01 | Vanilla Xaero minimap and World Map with fork installed | Local exploration still works, no upstream XMMP beside fork | Not yet tested |
| F02 | `xmmp` + FTB Teams 2101.1.11 + Rogler SMP Core 0.8.1 | Minecraft server starts and two clients can join | Not yet tested |
| F03 | Client with old protocol 3 | No authorized terrain sync; no old global data exposed | Source-gated, in-game pending |
| F04 | A1 explores while B1 is in another party | B1 receives ZERO new map terrain from fork | Source-gated, in-game pending |
| F05 | Old `world/xmmp/world_map` from upstream present | No migration and no old map broadcast | Source-gated, in-game pending |
| F06 | FTB Teams absent or manager unavailable | Optional resolver returns null, no team privilege | Source policy tested; integration pending |
| F07 | No PartyTeam (personal team only) | No party access | Source policy tested; integration pending |
| F08 | Xaero World Map caves with `syncCaves=false` | Native cave layers remain enabled | In-game pending |
| F09 | XaeroPlus 2.36.0 / Sable Xaero Bridge | No mixin conflict or startup crash | In-game pending |
| F10 | Chunk validation / network abuse | Unknown/malformed terrain packet cannot reach handlers | Static guard tested; fuzz pending |
| F11 | Core grant/identity policy | PRIVATE/TEAM/GLOBAL, team change, no party | 17 pure Kotlin checks pass |
| F12 | Gradle 9.8.2 + Java 25 runner, toolchain Java 21 | `:1.21.1-neoforge:build` and artifact pass | GitHub Actions pending |

Start with all data backed up. Never deploy this v0.1.0 foundation as a functional sharing mod; sync is intentionally disabled.

## v0.3.0 – Zusätzliche Abnahme (alles im Spiel noch offen)

| Test | Erwartung |
|---|---|
| TEAM A und TEAM B; A1 erkundet | A2 sieht Teamgebiet, B1 erhält keine Team-A-Index-/Datenpakete |
| A1 markiert GLOBAL; B1 ist TEAM | B1 sieht globales Gelände zusätzlich zur eigenen/Teamkarte |
| A2 besucht eine vorher von A1 geteilte Kachel | Eigene `VISITED`-Provenienz wird gesetzt, unabhängig von Xaeros Cachezustand |
| Native Xaero MapWriter schreibt dieselbe Kachel | `NATIVE` wird gesetzt; späterer Team-/Global-Download überschreibt dieses Tile nicht |
| Zwei Freigabebereiche liefern dieselben XYZ | Private Karte gewinnt vor Team; Team gewinnt vor Global; Cursor/Revisions-Keys kollidieren nicht |
| FTB-Teamwechsel während >128 Downloads | Keine neuen Team-A-Pakete nach Revalidierung; bereits lokal angezeigte Daten bleiben bis späterem Widerruf bestehen |
| Client-/Serverneustart | Persönliches Journal behält native/visited/received-Markierungen; Scope-Cursor werden unter `scoped-v3` separat geführt |
| Andere Dimension / Höhlenlayer | Keine Kreuzvermischung; nicht synchronisierte FULL_CAVE-Ebene bleibt unangetastet |
| Falsches Scope/SyncId-Paket | Client verwirft alte oder unbekannte Chunkdaten |
| 8–12 Spieler / schnelle Jetpacks | Kein ungeprüfter Chunk-Upload, keine Outbound-Queue ohne Limit; TPS/Memory/Traffic messen |
| Altes XMMP v5 verbindet sich mit v6 | Handshake-Protokoll inkompatibel; keine unautorisierten Terrain-Transfers |

## v0.4.0 admin/UI regression matrix
- [ ] YACL mode can be cycled through exactly PRIVATE/TEAM/GLOBAL, persisted and re-handshaken
- [ ] TEAM and GLOBAL receiving independently switch off server-authorized index/downloads
- [ ] PRIVATE never gets external scopes even when checkboxes enabled
- [ ] Non-OP YACL hides admin controls; forged admin packet leaves server settings unchanged
- [ ] OP changes team/global/upload switches while players are actively downloading; no new sensitive payloads
- [ ] Server restart keeps chosen admin settings; malformed saved file does not enable permissions
- [ ] When re-enabled, a new handshake/reconnect starts permitted sync
- [ ] Previously received Xaero map remains visible after revocation (known and disclosed limitation)
- [ ] Build NeoForge 21.1.249, Xaero World Map 1.45.0, Minimap 26.4.2, XaeroPlus, Rogler Core; verify join, cave mode, memory and multiplayer privacy
