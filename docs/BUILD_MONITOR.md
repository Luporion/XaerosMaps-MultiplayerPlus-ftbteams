# GitHub Actions Terminalmonitor (XMMP-Fork)

Dieser Fork verwendet den aus **Rogler SMP Core** abgeleiteten Actions-Monitor.
Die Terminalanzeige schreibt Statuswechsel als normale Zeilen (Scrollback bleibt
sichtbar), verwendet eine animierte Statuszeile, zeigt Job-/Step-Änderungen und lädt
nach einem erfolgreichen NeoForge-Build das GitHub-Artefakt herunter.

## Bereits gepusht: jetzt starten

Im Repository `E:\Entwicklung\XaerosMaps-MultiplayerPlus-ftbteams`:

```powershell
.\WATCH.cmd
```

- Standard: aktueller Branch und `git rev-parse HEAD` (exakter Commit).
- GitHub-Repository wird **explizit** als `Luporion/XaerosMaps-MultiplayerPlus-ftbteams` an alle `gh`-Befehle übergeben. Daher ist **kein** `gh repo set-default` notwendig.
- Der Monitor beobachtet den **Push-Workflow** `Rogler XMMP v<Version> NeoForge Build`, nicht die Upstream-Release-Workflows.
- Vorzeitiges Scheitern: rote Meldung und `gh run view --log-failed` für den passenden Build.
- Nach Erfolg: `artifacts/run-<RunID>-<SHA8>/` mit `.jar` und `build-monitor.json` (SHA-256, Run-ID, Commit, `RuntimeVerified=false`). Erneuter Aufruf prüft den Cache und lädt nicht erneut herunter.
- Kein Löschen/Überschreiben bestehender Artefakte, auch bei korrumpiertem Cache. Fehlende oder abgelaufene Artefakte verursachen einen deutlichen Fehler.
- Ohne interaktives Terminal: `-NoAnimation`; bei reinem Statuscheck: `-NoDownload`.

## Nach einem neuen Update-Patch

**Nur auf einem Feature-Branch, nicht auf `main`:**

```powershell
.\PATCH.cmd 'M:\Downloads\XaerosMaps-MultiplayerPlus-ftbteams-v0.2.0.patch'
```

Nach `git am` und `VERIFY.cmd` führt `PATCH.cmd` standardmäßig `PUSH-AND-WATCH.cmd` aus: Push zu `origin`, Actions-Monitor starten, JAR herunterladen. Mit `--no-push` wird der automatische Push/Monitor explizit unterdrückt.

**Wenn das Update schon angewendet und committed ist:** Das alte Patchfile **nicht erneut** anwenden!

```powershell
.\PUSH-AND-WATCH.cmd
# oder nach einem vorhandenen Push nur
.\WATCH.cmd
```

## Weitere Optionen

```powershell
# Benutzerdefiniertes Intervall / ohne Animation
pwsh -NoProfile -File .\scripts\watch-actions.ps1 -PollSeconds 10 -NoAnimation

# Nur Erfolg/Fehler überwachen; JAR nicht herunterladen
.\WATCH.cmd -NoDownload

# Nur einen bestimmten bereits gepushten Commit beobachten
.\WATCH.cmd -Commit '0123456789abcdef0123456789abcdef01234567' -Branch 'feat/v0.1.0-ftbteams-foundation'
```

## Voraussetzungen und Fehlerbehebung

- Windows PowerShell 7 (`pwsh`), Git und GitHub CLI (`gh`); `gh auth status` muss erfolgreich sein.
- `gh repo set-default Luporion/XaerosMaps-MultiplayerPlus-ftbteams` behebt auch den manuellen Fehler `No default remote repository has been set`, ist aber für **diesen Monitor** nicht erforderlich.
- Kein Fund eines Runs: Git-Push des beobachteten Commits überprüfen, nach spätestens drei Minuten wird mit Meldung beendet.
- `gh`-Fehler: Authentifizierung/Netzwerk prüfen; drei aufeinanderfolgende fehlgeschlagene Abfragen beenden den Monitor.
- `git am`-Fehler: *nicht* mehrfach patchen. `git status` untersuchen und gegebenenfalls bewusst `git am --abort` benutzen.
- Ein grüner Build beweist **nicht**, dass die Minecraft-Mod mit Xaero / NeoForge zur Laufzeit funktioniert. Testprofil nutzen.

## Tests und Grenzen

`python scripts/test-build-monitor.py` prüft die Anbindung, wichtige Schutzregeln und die Repo-/Workflow-Fixtures. Der NeoForge-CI-Workflow prüft zusätzlich die PowerShell-Syntax auf dem GitHub-Runner. Die echte PowerShell-7-Ausführung mit GitHub CLI sowie der tatsächliche Artefaktdownload müssen auf Windows beobachtet werden. Keine Minecraft-Quellen wurden geändert.
