#!/usr/bin/env python3
"""Offline structural and fixture checks for the Windows GitHub Actions monitor.

This is NOT a PowerShell runtime test: it verifies source integration and the
current workflow/artifact contract. Integration requires pwsh + gh on Windows.
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
MONITOR = (ROOT / 'scripts/watch-actions.ps1').read_text(encoding='utf-8')
WORKFLOW = (ROOT / '.github/workflows/rogler-neoforge-build.yml').read_text(encoding='utf-8')
PATCH = (ROOT / 'PATCH.cmd').read_text(encoding='utf-8')
WATCH = (ROOT / 'WATCH.cmd').read_text(encoding='utf-8')
PUSH = (ROOT / 'PUSH-AND-WATCH.cmd').read_text(encoding='utf-8')
README = (ROOT / 'docs/BUILD_MONITOR.md').read_text(encoding='utf-8')

repo = 'Luporion/XaerosMaps-MultiplayerPlus-ftbteams'
assert f"[string]$Repo = '{repo}'" in MONITOR
assert "'run', 'list', '-R', $Repo" in MONITOR
assert "'run', 'view', [string]$Run.databaseId" in MONITOR
assert "& gh run view ([string]$Run.databaseId) -R $Repo --log-failed" in MONITOR
assert "& gh run download ([string]$runId) -R $Repo" in MONITOR
assert "& gh api ('repos/{0}' -f $Repo)" in MONITOR
assert '$_.headSha -eq $Commit' in MONITOR and '$_.headBranch -eq $Branch' in MONITOR
assert '$_.event -eq \'push\'' in MONITOR
assert 'Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256' in MONITOR
assert 'Test-CachedDownload' in MONITOR and 'RuntimeVerified = $false' in MONITOR
assert 'Show-ChangedSteps' in MONITOR and 'Show-LiveLine' in MONITOR
assert 'Clear-Host' not in MONITOR and 'gh run watch' not in MONITOR
assert 'NoAnimation' in MONITOR and 'NoDownload' in MONITOR
assert "--json', 'jobs'" in MONITOR
assert 'OutputDir' in MONITOR
assert 'rogler-xmmp-' in MONITOR
assert re.search(r'^name: Rogler XMMP v\d+\.\d+\.\d+ NeoForge Build$', WORKFLOW, re.M)
assert re.search(r'^          name: rogler-xmmp-\d+\.\d+\.\d+-unverified-runtime$', WORKFLOW, re.M)
assert 'PUSH-AND-WATCH.cmd' in PATCH
assert 'git apply --check' in PATCH and 'VERIFY.cmd' in PATCH
assert 'WATCH.cmd' in PUSH and 'git push -u origin' in PUSH
assert 'Luporion/XaerosMaps-MultiplayerPlus-ftbteams' in PUSH
assert 'watch-actions.ps1' in WATCH and 'pwsh -NoProfile' in WATCH
assert 'gh repo set-default' in README
assert '/artifacts/' in (ROOT / '.gitignore').read_text(encoding='utf-8')

# Simulate a GitHub API result list and the exact filtering conditions that the
# monitor must enforce. This checks the expected workflow naming and SHA/branch
# contract without depending on an authenticated GitHub CLI.
commit = 'f' * 40
fixture = json.loads(json.dumps([
    {'headSha': commit, 'headBranch': 'feat/v0.1.0-ftbteams-foundation', 'event': 'push',
     'workflowName': 'Rogler XMMP v0.1.0 NeoForge Build', 'databaseId': 123},
    {'headSha': '0' * 40, 'headBranch': 'feat/v0.1.0-ftbteams-foundation', 'event': 'push',
     'workflowName': 'Rogler XMMP v0.1.0 NeoForge Build', 'databaseId': 124},
    {'headSha': commit, 'headBranch': 'main', 'event': 'push',
     'workflowName': 'Rogler XMMP v0.1.0 NeoForge Build', 'databaseId': 125},
    {'headSha': commit, 'headBranch': 'feat/v0.1.0-ftbteams-foundation', 'event': 'push',
     'workflowName': 'Release', 'databaseId': 126},
]))
workflow_pattern = re.compile(r'^Rogler XMMP\b.*\bNeoForge Build$')
selected = [r for r in fixture if r['headSha'] == commit
            and r['headBranch'] == 'feat/v0.1.0-ftbteams-foundation'
            and r['event'] == 'push' and workflow_pattern.match(r['workflowName'])]
assert [r['databaseId'] for r in selected] == [123]
assert workflow_pattern.match('Rogler XMMP v0.2.0 NeoForge Build')
print('XMMP Actions monitor: repo-scoped query, workflow match, privacy of artifacts, scripts and fixture checks OK')
