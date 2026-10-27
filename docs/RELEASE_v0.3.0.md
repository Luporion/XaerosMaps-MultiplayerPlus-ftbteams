# XMMP FTB Teams v0.3.0 – Multi-Scope & Personal Exploration Provenance

**SOURCE CANDIDATE · NeoForge-CI und Minecraft-DEV-/Multiplayer-Test offen.** Ausgangsstand ist das erfolgreiche v0.2.0 Kotlin-Buildfix-Commit `7dd4c2de` (Run `40588269876`). **Nicht** auf produktiven TeamWars-Server installieren, bevor die neue JAR und 2–4 getrennte Teams/OP getestet sind.

## Neue Funktionen

- Serverberechtigter Download mehrerer Quellen: `PRIVATE` liest nur das eigene Spielerarchiv; `TEAM` und `GLOBAL` lesen persönliche, verifizierte FTB-Team- (falls vorhanden) und globale Daten. Upload-Publikation bleibt unverändert auf dem ausgewählten Modus plus private Kopie. Gegner dürfen keine Team-A-Daten abrufen.
- Netzwerkprotokoll **6**: Jedes Chunkpaket enthält Herkunfts-Scope und Session-Generation. Outbound-Queues und Async-Download-Lesezugriffe prüfen die verifizierte aktuelle Spielerteamzugehörigkeit erneut. Alte v5-JAR darf nicht mit v6 gemischt betrieben werden.
- Client-Cursor nun unter `scoped-v3/<scope>/<layer>` mit getrenntem Revisionsstand und Fehlschlägen; getrennte Kachelprioritäten vermeiden zufälliges Überschreiben durch verspätete Downloads. Priorität: eigene PRIVAT-Daten, dann TEAM, dann GLOBAL. Native Xaero-Bilder gewinnen stets gegenüber fremden Empfangspaketen.
- `PersonalExplorationJournal` pro Xaero-Welt/Dimension und Multiworld unter `personal-provenance-v3.bin`: native Tile-Schreibvorgänge, eigene physisch betretene Oberflächen-Chunks und aus geteilten Scopes empfangene Tiles werden mit **unterschiedlichen Bits** und atomarem Austausch gespeichert. Eine selbst betretene Kachel bleibt als eigene Besuchsinformation markiert, auch wenn vorher dieselbe Kachel von anderen angezeigt wurde.
- Standard bleibt `terrainSharingMode = "PRIVATE"` (Client TOML). Änderungen benötigen einen Reconnect. Keine neuen Minecraft-Pflichtmods; FTB Teams ist nur auf dem Server für echtes Teamsharing notwendig.

## Nicht implementiert / Grenzen

- Das Journal speichert **Erkundungsherkunft und Visits, nicht eine unabhängige zweite Pixelkarte**. Bereits von Xaero lokal gespeicherte fremde Pixel werden bei Teamwechsel nicht automatisch ausgeblendet. Nachträgliche Attribution bereits vor v0.3.0 gemischter Daten ist unmöglich.
- Ein persönlicher Besuch setzt einen Marker, garantiert aber nicht, dass Xaero eine bereits von einem Teammitglied heruntergeladene Tile neu berechnet. Für späteren Widerruf benötigt es eine native Neu-Erfassung/einen getrennten Local-Terrain-Cache.
- Ein fehlerhaftes Journal blockiert absichtlich die neue Sync-Session statt still die Herkunft zu vergessen. Backup vor DEV-Tests.
- Gleichzeitiger Mehrfach-Download erhöht Traffic. Der Server begrenzt Indizes/Uploads/Queues weiterhin, benötigt aber reale Stresstests mit 8–12 Spielern.

## Abnahme

1. GitHub-Actions-NeoForge-Build, `VERIFY.cmd`, Kotlin-Policytests, Journaltests und Workflow-Syntax grün.
2. DEV-Sicherung der Welt und privaten Xaero-Karten vor Installation (Client und Server **v0.3.0**, keinesfalls v0.2.0 mischen).
3. TEAM A (2 Spieler), TEAM B (1 Spieler), OP in B. Sichtbarkeit einer Team-A-Tile bei A2, **keine** Daten bei B1; GLOBAL-Tile für beide, eigene PRIVATE-Kacheln für den Eigentümer.
4. Zwei Bereiche schreiben dieselben XYZ: private Pixel bleiben erhalten; TEAM gewinnt vor GLOBAL. Nach Logout/Restart Cursor und Journal prüfen.
5. A2 besucht zuerst von A1 geteilte Tile – `VISITED` muss unabhängig von `RECEIVED` bestehen. Native MapWriter-Schreibvorgänge zusätzlich mit `NATIVE` markieren.
6. Noch während großer Downloads FTB-Team verlassen/wechseln, Dimension wechseln oder neu verbinden; alte v6 `syncId`/Scope-Pakete ablehnen. Bereits sichtbare Fremdpixel verbleiben und sind keine produktive Geheimhaltung nach Teamwechsel.
7. Xaero World Map 1.45.0, Minimap 26.4.2, XaeroPlus 2.36.0, Sable Xaero Bridge und Rogler SMP Core gemeinsam testen.

## Installation

Auf `feat/v0.2.0-scoped-terrain` nach dem Kotlin-Buildfix `7dd4c2de` neuen Branch `feat/v0.3.0-provenance` anlegen. Patch genau einmal über `PATCH.cmd` anwenden. Das Skript pusht und zeigt anschließend den GitHub-Actions-Build; erst nach grünem Build in DEV testen.
