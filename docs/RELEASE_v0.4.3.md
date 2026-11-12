# XMMP FTB Teams Fork v0.4.3

Basis ist `feat/v0.4.2-sync-diagnostics` bei `36edbcbd74a57ebb7e5a8c3a2599860393d4d99b`. Ziel ist Minecraft 1.21.1 und NeoForge. Der Rogler Core v0.9.0 kann den Status lesen und das vorhandene XMMP-Einstellungsmenü öffnen. XMMP funktioniert auch eigenständig.

## Freigabe und Neuabgleich

- Der Server prüft FTB-Zuordnung und wirksame Freigaben ungefähr einmal pro Sekunde. Bei einer Änderung verwirft er die alten ausstehenden Downloads. Der Client fordert einen neuen Index an, auch wenn sich die Dimension nicht geändert hat.
- Statistiken und der reine Upload-Schalter starten keinen Download neu. Empfang von Team- und GLOBAL-Daten ist unabhängig vom eigenen Veröffentlichungsmodus. Der Serverstatus gibt die wirksamen Empfangsrechte an.
- Ein Upload behält die bei Annahme geprüfte Veröffentlichung und Sync-Generation. Vor der Veröffentlichung wird erneut geprüft, ob beides noch gilt. Der Client verwirft ausstehende Uploads aus der vorherigen Generation.
- Ein alter Index-Lesejob wird beim Neuabgleich abgebrochen. Der Server validiert die Berechtigung erneut nach dem I/O und vor dem Versand. Der Client übernimmt Indexeinträge und Abschlussmarken nur für den aktuellen Abgleich.

## Budgets

- Maximal zwei Index-Lesejobs gleichzeitig und bis zu 16.384 Einträge je Bereich und Layer. Der Indexleser baut keine unbeschränkte Liste aller Pfade auf.
- Der Server-Dispatcher verarbeitet bis zu 128 Aufgaben je Tick und prüft zwischen Aufgaben ein Zeitbudget von 2 ms. Region-Backfills geben nach 64 Chunkpositionen ab. Der Client verarbeitet bis zu 128 Indexeinträge je Arbeitsabschnitt.
- Die bestehenden Transferfenster, Ratenlimits und Wiederholungen bleiben aktiv. Ein Vergleich mit realer Spielerlast steht aus.

## PvP und Update

GLOBAL kann erkundete Basen und Routen an alle Spieler weitergeben und ist in neuen Welten standardmäßig gesperrt. Bereits im nativen Xaero-Cache gespeicherte fremde Kacheln können nach einem Teamwechsel sichtbar bleiben. Eine vollständige Kartenabnahme erfordert zwei echte Clients und einen widerrufbaren Renderer.

Server und Clients gemeinsam von v0.4.2 auf v0.4.3 aktualisieren. XMMP verwendet weiter Protokoll 8 und die bestehenden Speicherpfade. Für eine Rückkehr die v0.4.2-JARs wieder gemeinsam einsetzen. Persönliche Karten- und Weltdaten können dabei bestehen bleiben.

## Prüfstand

Der NeoForge-Build für Minecraft 1.21.1 und die Core-Suite mit acht JUnit-Tests waren erfolgreich. Ein zusätzlicher Test mit der kompilierten Serversitzung hat privaten Versand mit Teamempfang, den Indexwechsel in derselben Dimension und die Abweisung des alten Index sowie des ausstehenden Uploads geprüft. Der neue Upload wurde gespeichert. Die NeoForge-CI konfiguriert dafür gezielt das 1.21.1-NeoForge-Projekt.
