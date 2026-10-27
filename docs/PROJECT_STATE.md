# Aktueller Projektstand – XMMP FTB Teams

**v0.3.0 Source Candidate – 27. Oktober 2026.** Ausgangspunkt: `feat/v0.2.0-scoped-terrain` nach Kotlin-Buildfix `7dd4c2de`. Der zugehörige NeoForge-1.21.1-GitHub-Run **40588269876** war erfolgreich, die JAR `xmmp-0.2.0+1.21.1-neoforge.jar` wurde heruntergeladen. Ein Minecraft-Test von v0.2.0 ist nicht nachgewiesen.

v0.3.0 führt serverautorisierten Mehrfach-Empfang (`PRIVATE` + optional `TEAM` + `GLOBAL`), Scope-/Generation-Tags in Chunkpaketen (Protokoll 6), getrennte Cursor je Bereich, eine priorisierte lokale Kartenkomposition und eine persistente persönliche Provenienz-Datei ein. Physische Besuche und native Xaero-Schreibvorgänge werden separat von empfangenen Bildern erfasst. **V0.3.0 ist nicht NeoForge-kompiliert oder im Spiel verifiziert**; sie benötigt den neuen GitHub-Build und DEV-Tests.

Grenzen: Sichtbare fremde Tiles sind weiterhin in Xaeros lokalem Kartenspeicher vermischt und werden bei Teamwechsel nicht entfernt. Ein physischer Besuch beweist nicht zwingend, dass Xaero die Tile-Pixel neu berechnet hat. Provenienzdateien sind nur für Ereignisse ab Installation verlässlich; ältere verschmolzene Xaero-Daten lassen sich nicht rückwirkend zuordnen. Teamwechsel müssen weiterhin mit Testclients auf Datenabfluss unter hohen Netzwerklasten geprüft werden.

Details: [v0.3.0](RELEASE_v0.3.0.md), [Roadmap](ROADMAP.md), [Testmatrix](TEST_MATRIX.md).

## CI-Hotfix nach v0.3.0-Commit 2e9c137f (28.10.2026)

NeoForge-Kotlin und Java kompilierten im Run `40598049157`, aber `:1.21.1-neoforge:test` fand keine JUnit-Tests. Test-Discovery-Hotfix ergänzt JUnit Jupiter, Launcher, Testwrappers und `:core:test` in GitHub Actions, ohne Gameplay oder Netzwerkprotokoll zu ändern. Nächster Schritt: neuer CI-Lauf und dann DEV-Teamtests.
