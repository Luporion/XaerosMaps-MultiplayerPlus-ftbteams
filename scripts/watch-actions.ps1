#Requires -Version 5.1
# GitHub Actions terminal monitor for the Rogler XMMP fork.
# Adapted from the Rogler SMP Core monitor; keeps scrollback and downloads the
# successful build artifact only after its identity and commit are verified.
[CmdletBinding()]
param(
    [ValidatePattern('^[0-9a-fA-F]{40}$')]
    [string]$Commit = '',
    [string]$Branch = '',
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repo = 'Luporion/XaerosMaps-MultiplayerPlus-ftbteams',
    [ValidateRange(2, 60)]
    [int]$PollSeconds = 5,
    [ValidateRange(1, 240)]
    [int]$TimeoutMinutes = 75,
    [ValidateRange(1, 30)]
    [int]$DiscoveryMinutes = 3,
    [string]$OutputDir = 'artifacts',
    [switch]$NoAnimation,
    [switch]$NoDownload
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:LiveLineLength = 0
$script:SpinnerIndex = 0
$script:SpinnerFrames = @([char]0x25D0, [char]0x25D3, [char]0x25D1, [char]0x25D2)
$script:CurrentStep = 'Workflow wird gesucht'
$script:UseAnimation = -not $NoAnimation
try {
    if ([Console]::IsOutputRedirected -or $Host.Name -ne 'ConsoleHost') { $script:UseAnimation = $false }
} catch { $script:UseAnimation = $false }

function Clear-LiveLine {
    if ($script:LiveLineLength -le 0) { return }
    Write-Host -NoNewline ("`r" + (' ' * $script:LiveLineLength) + "`r")
    $script:LiveLineLength = 0
}
function Write-Status {
    param([string]$Message, [string]$Color = 'Gray')
    Clear-LiveLine
    Write-Host ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message) -ForegroundColor $Color
}
function ConvertTo-GitHubUtc {
    param([object]$Timestamp)
    if ($null -eq $Timestamp) { return $null }
    try {
        if ($Timestamp -is [datetimeoffset]) {
            $value = [datetimeoffset]$Timestamp
        } elseif ($Timestamp -is [datetime]) {
            $date = [datetime]$Timestamp
            if ($date.Kind -eq [DateTimeKind]::Unspecified) {
                $date = [datetime]::SpecifyKind($date, [DateTimeKind]::Utc)
            }
            $value = [datetimeoffset]$date
        } elseif (-not [string]::IsNullOrWhiteSpace([string]$Timestamp)) {
            $value = [datetimeoffset]::Parse([string]$Timestamp,
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::AssumeUniversal)
        } else { return $null }
        if ($value.Year -lt 2000) { return $null }
        return $value.ToUniversalTime()
    } catch { return $null }
}
function Get-ElapsedText {
    param([object]$Run)
    if ($null -eq $Run) { return '' }
    $start = ConvertTo-GitHubUtc -Timestamp $Run.startedAt
    if ($null -eq $start) { $start = ConvertTo-GitHubUtc -Timestamp $Run.createdAt }
    if ($null -eq $start) { return '' }
    $elapsed = [datetimeoffset]::UtcNow - $start
    if ($elapsed.Ticks -lt 0) { $elapsed = [timespan]::Zero }
    if ($elapsed.TotalHours -ge 1) {
        return ('{0}h {1:00}m {2:00}s' -f [math]::Floor($elapsed.TotalHours), $elapsed.Minutes, $elapsed.Seconds)
    }
    return ('{0}m {1:00}s' -f [math]::Floor($elapsed.TotalMinutes), $elapsed.Seconds)
}
function Get-DisplayState {
    param([object]$Run)
    if ($null -eq $Run) { return @{ Text = 'wartet auf Workflow'; Compact = 'wartet'; Color = 'DarkGray' } }
    if ($Run.status -eq 'completed') {
        if ($Run.conclusion -eq 'success') { return @{ Text = 'erfolgreich'; Compact = 'OK'; Color = 'Green' } }
        return @{ Text = "fehlgeschlagen ($($Run.conclusion))"; Compact = 'FEHLER'; Color = 'Red' }
    }
    if ($Run.status -eq 'in_progress') {
        $elapsed = Get-ElapsedText -Run $Run
        return @{ Text = "laeuft seit $elapsed"; Compact = "laeuft $elapsed"; Color = 'Yellow' }
    }
    return @{ Text = "wartet ($($Run.status))"; Compact = 'wartet'; Color = 'DarkGray' }
}
function Show-LiveLine {
    param([object]$Run)
    if (-not $script:UseAnimation) { return }
    $state = Get-DisplayState -Run $Run
    $spinner = [string]$script:SpinnerFrames[$script:SpinnerIndex % $script:SpinnerFrames.Count]
    $script:SpinnerIndex++
    $width = 110
    try { $width = [math]::Min(110, [math]::Max(1, $Host.UI.RawUI.WindowSize.Width - 2)) }
    catch { $width = 80 }
    $parts = @(
        @{ Text = ('[{0}] ' -f (Get-Date -Format 'HH:mm:ss')); Color = 'DarkGray' },
        @{ Text = "$spinner "; Color = 'Yellow' },
        @{ Text = 'NeoForge: '; Color = 'Gray' },
        @{ Text = [string]$state.Compact; Color = [string]$state.Color },
        @{ Text = ' | '; Color = 'DarkGray' },
        @{ Text = [string]$script:CurrentStep; Color = 'Cyan' }
    )
    Write-Host -NoNewline "`r"
    $written = 0
    foreach ($part in $parts) {
        $remaining = $width - $written
        if ($remaining -le 0) { break }
        $fragment = [string]$part.Text
        if ($fragment.Length -gt $remaining) { $fragment = $fragment.Substring(0, $remaining) }
        if ($fragment.Length) {
            Write-Host -NoNewline $fragment -ForegroundColor $part.Color
            $written += $fragment.Length
        }
    }
    if ($script:LiveLineLength -gt $written) {
        Write-Host -NoNewline (' ' * ($script:LiveLineLength - $written))
    }
    $script:LiveLineLength = [math]::Max($written, $script:LiveLineLength)
}
function Wait-NextPoll {
    param([object]$Run)
    if (-not $script:UseAnimation) { Start-Sleep -Seconds $PollSeconds; return }
    for ($i = 0; $i -lt $PollSeconds; $i++) {
        Show-LiveLine -Run $Run
        Start-Sleep -Seconds 1
    }
    Clear-LiveLine
}
function Invoke-GhJson {
    param([string[]]$Arguments)
    $response = & gh @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ('GitHub CLI Fehler (Exit {0}): gh {1}' -f $LASTEXITCODE, ($Arguments -join ' '))
    }
    $json = ($response -join "`n").Trim()
    if (-not $json) { throw 'GitHub CLI lieferte kein JSON.' }
    try { return ConvertFrom-Json -InputObject $json }
    catch { throw "GitHub CLI lieferte ungueltiges JSON: $($_.Exception.Message)" }
}
function Find-Run {
    $items = @(Invoke-GhJson -Arguments @(
        'run', 'list', '-R', $Repo, '--commit', $Commit, '--event', 'push', '--limit', '100',
        '--json', 'databaseId,name,workflowName,status,conclusion,headSha,headBranch,event,attempt,url,createdAt,startedAt'
    ))
    $matches = @($items | Where-Object {
        $_.headSha -eq $Commit -and $_.headBranch -eq $Branch -and $_.event -eq 'push' -and
        ($_.workflowName -match '^Rogler XMMP\b.*\bNeoForge Build$' -or
         $_.name -match '^Rogler XMMP\b.*\bNeoForge Build$')
    })
    return ($matches | Sort-Object -Property databaseId -Descending | Select-Object -First 1)
}
function Show-ChangedSteps {
    param([object]$Run, [hashtable]$Seen)
    if ($null -eq $Run) { return }
    $details = Invoke-GhJson -Arguments @('run', 'view', [string]$Run.databaseId,
        '-R', $Repo, '--json', 'jobs')
    $script:CurrentStep = 'Job wird vorbereitet'
    foreach ($job in @($details.jobs)) {
        if ($null -eq $job) { continue }
        $jobKey = "job:$($job.databaseId)"
        $jobState = "$($job.status)/$($job.conclusion)"
        if ($Seen[$jobKey] -ne $jobState) {
            if ($job.status -eq 'completed') {
                $color = if ($job.conclusion -eq 'success') { 'Green' } else { 'Red' }
                Write-Status "Job '$($job.name)': $($job.conclusion)" $color
            } elseif ($job.status -eq 'in_progress') {
                Write-Status "Job '$($job.name)' gestartet" 'Yellow'
            }
            $Seen[$jobKey] = $jobState
        }
        foreach ($step in @($job.steps)) {
            if ($null -eq $step) { continue }
            $stepKey = "step:$($job.databaseId):$($step.number)"
            $stepState = "$($step.status)/$($step.conclusion)"
            if ($step.status -eq 'in_progress') {
                $script:CurrentStep = [string]$step.name
            }
            if ($Seen[$stepKey] -ne $stepState) {
                if ($step.status -eq 'in_progress') {
                    Write-Status "  ... $($step.name)" 'Yellow'
                } elseif ($step.status -eq 'completed') {
                    $color = if ($step.conclusion -eq 'success') { 'Green' } elseif ($step.conclusion -eq 'skipped') { 'DarkGray' } else { 'Red' }
                    Write-Status "  [$($step.conclusion)] $($step.name)" $color
                }
                $Seen[$stepKey] = $stepState
            }
        }
    }
}
function Show-RunFailure {
    param([object]$Run)
    Write-Status "NeoForge-Build fehlgeschlagen: Run $($Run.databaseId) ($($Run.conclusion))." 'Red'
    if ($Run.url) { Write-Status "Details: $($Run.url)" 'DarkGray' }
    & gh run view ([string]$Run.databaseId) -R $Repo --log-failed
    if ($LASTEXITCODE -ne 0) {
        Write-Status 'Fehlerlogs nicht abrufbar; zeige Run-Zusammenfassung.' 'Yellow'
        & gh run view ([string]$Run.databaseId) -R $Repo
    }
}
function Get-Artifact {
    param([long]$RunId)
    $deadline = (Get-Date).AddMinutes(2)
    $announced = $false
    do {
        $response = Invoke-GhJson -Arguments @('api', ('repos/{0}/actions/runs/{1}/artifacts?per_page=100' -f $Repo, $RunId))
        $artifacts = @($response.artifacts | Where-Object {
            $_.expired -eq $false -and $_.name -match '^rogler-xmmp-[A-Za-z0-9_.+\-]+-unverified-runtime$'
        })
        if ($artifacts.Count -gt 1) { throw 'Mehrere passende Build-Artefakte: kein automatischer Download.' }
        if ($artifacts.Count -eq 1) { return $artifacts[0] }
        if (-not $announced) { Write-Status 'Warte auf GitHub-Artefakt ...' 'Yellow'; $announced = $true }
        Start-Sleep -Seconds $PollSeconds
    } while ((Get-Date) -lt $deadline)
    throw 'Kein passendes, nicht abgelaufenes XMMP-JAR-Artefakt gefunden.'
}
function Get-ValidJars {
    param([string]$Directory)
    return @(Get-ChildItem -LiteralPath $Directory -Recurse -File -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '(?i)-(sources|javadoc|dev)\.jar$' })
}
function Test-CachedDownload {
    param([string]$Target, [object]$Artifact, [long]$RunId)
    $manifestPath = Join-Path $Target 'build-monitor.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { return $false }
    try {
        $saved = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ([long]$saved.RunId -ne $RunId -or [long]$saved.ArtifactId -ne [long]$Artifact.id -or
            $saved.Commit -ne $Commit -or $saved.Branch -ne $Branch) { return $false }
        $jars = @(Get-ValidJars -Directory $Target)
        if ($jars.Count -eq 0 -or @($saved.Jars).Count -ne $jars.Count) { return $false }
        foreach ($item in @($saved.Jars)) {
            $file = Join-Path $Target ([string]$item.File)
            if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $false }
            if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.SHA256) { return $false }
        }
        return $true
    } catch { return $false }
}

try {
    foreach ($tool in @('git','gh')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "'$tool' ist nicht installiert oder nicht im PATH."
        }
    }
    if (-not $Commit) {
        $Commit = ((& git -C $repoRoot rev-parse --verify HEAD) -join "`n").Trim()
        if ($LASTEXITCODE -ne 0 -or $Commit -notmatch '^[0-9a-fA-F]{40}$') {
            throw 'Git HEAD konnte nicht ermittelt werden.'
        }
    }
    if (-not $Branch) {
        $Branch = ((& git -C $repoRoot branch --show-current) -join "`n").Trim()
        if ($LASTEXITCODE -ne 0 -or -not $Branch) { throw 'Kein aktiver Branch: -Branch angeben.' }
    }
    # Explicit -R / GitHub API path everywhere: no "gh repo set-default" required.
    $null = & gh api ('repos/{0}' -f $Repo) --jq '.full_name'
    if ($LASTEXITCODE -ne 0) { throw "Repository $Repo nicht erreichbar. 'gh auth login' pruefen." }
    $outputRoot = if ([IO.Path]::IsPathRooted($OutputDir)) {
        [IO.Path]::GetFullPath($OutputDir)
    } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDir)) }
    $sha = $Commit.Substring(0,8)
    Write-Status "XMMP-Monitor | $Repo | Branch $Branch | Commit $sha" 'Cyan'
    Write-Status "Abfrage alle $PollSeconds s; GitHub-Schritte werden fortlaufend angezeigt, Terminalverlauf bleibt erhalten." 'DarkGray'
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    $discoveryDeadline = (Get-Date).AddMinutes($DiscoveryMinutes)
    $lastState = $null
    $seenSteps = @{}
    $run = $null
    $errors = 0
    while ((Get-Date) -lt $deadline) {
        try {
            $run = Find-Run
            $errors = 0
        } catch {
            $errors++
            Write-Status "GitHub-Abfrage fehlgeschlagen ($errors/3): $($_.Exception.Message)" 'Yellow'
            if ($errors -ge 3) { throw }
            Start-Sleep -Seconds $PollSeconds
            continue
        }
        $state = if ($null -eq $run) { 'none' } else { "$($run.databaseId)/$($run.attempt)/$($run.status)/$($run.conclusion)" }
        if ($state -ne $lastState) {
            $display = Get-DisplayState -Run $run
            $runId = if ($null -eq $run) { '-' } else { [string]$run.databaseId }
            Write-Status "NeoForge [Run $runId]: $($display.Text)" $display.Color
            $lastState = $state
        }
        if ($null -ne $run) {
            try { Show-ChangedSteps -Run $run -Seen $seenSteps }
            catch { Write-Status "Schrittdetails voruebergehend nicht abrufbar: $($_.Exception.Message)" 'Yellow' }
            if ($run.status -eq 'completed') {
                if ($run.conclusion -ne 'success') {
                    Show-RunFailure -Run $run
                    throw 'GitHub-NeoForge-Workflow nicht erfolgreich.'
                }
                break
            }
        }
        if ($null -eq $run -and (Get-Date) -ge $discoveryDeadline) {
            throw "Kein Push-Workflow fuer Commit $sha auf Branch '$Branch' gefunden. Wurde der Commit gepusht?"
        }
        Wait-NextPoll -Run $run
    }
    if ($null -eq $run -or $run.status -ne 'completed' -or $run.conclusion -ne 'success') {
        throw "Timeout: Kein erfolgreicher NeoForge-Build innerhalb von $TimeoutMinutes Minuten."
    }
    Write-Status "NeoForge erfolgreich, Run $($run.databaseId) fuer Commit $sha." 'Green'
    if ($NoDownload) { Write-Status 'Download auf Wunsch uebersprungen (-NoDownload).' 'Yellow'; exit 0 }
    $runId = [long]$run.databaseId
    $artifact = Get-Artifact -RunId $runId
    $destination = Join-Path $outputRoot ("run-{0}-{1}" -f $runId,$sha)
    if (Test-Path -LiteralPath $destination) {
        if (Test-CachedDownload -Target $destination -Artifact $artifact -RunId $runId) {
            Write-Status "Artifact schon geprueft vorhanden: $destination" 'Green'
            exit 0
        }
        throw "Downloadziel existiert, ist aber nicht verifiziert: $destination (nicht ueberschrieben)."
    }
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $staging = Join-Path $outputRoot ('.download-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $staging | Out-Null
    try {
        Write-Status "Lade '$($artifact.name)' aus Run $runId ..." 'Cyan'
        & gh run download ([string]$runId) -R $Repo --name ([string]$artifact.name) --dir $staging
        if ($LASTEXITCODE -ne 0) { throw 'GitHub-Artefakt konnte nicht heruntergeladen werden.' }
        $jars = @(Get-ValidJars -Directory $staging)
        if ($jars.Count -eq 0) { throw 'Artifact enthaelt keine installierbare Haupt-JAR.' }
        $manifest = [ordered]@{
            Repository = $Repo; Branch = $Branch; Commit = $Commit
            RunId = $runId; ArtifactId = [long]$artifact.id; Artifact = [string]$artifact.name
            DownloadedUtc = (Get-Date).ToUniversalTime().ToString('o')
            RuntimeVerified = $false
            Jars = @($jars | ForEach-Object {
                [ordered]@{
                    File = $_.FullName.Substring($staging.Length).TrimStart([char[]]@('\','/'))
                    SHA256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
                }
            })
        }
        $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staging 'build-monitor.json') -Encoding UTF8
        if (Test-Path -LiteralPath $destination) { throw 'Zielordner wurde parallel angelegt.' }
        Move-Item -LiteralPath $staging -Destination $destination
        Write-Status "JAR-Download abgeschlossen: $destination" 'Green'
        foreach ($jar in $jars) { Write-Status "JAR: $($jar.Name)" 'Green' }
        Write-Status 'CI erfolgreich, Minecraft-DEV-/Runtime-Test weiterhin erforderlich.' 'Yellow'
    } finally {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
    }
} catch {
    Write-Status "ABBRUCH: $($_.Exception.Message)" 'Red'
    exit 1
} finally { Clear-LiveLine }
