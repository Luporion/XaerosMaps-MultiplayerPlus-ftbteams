#!/usr/bin/env python3
"""Focused source guard for v0.4.1 PvP warning and consent, NOT a runtime GUI test."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
read=lambda p:(r/p).read_text('utf-8')
u=read('src/main/kotlin/dev/alinco8/xmmp/config/ConfigScreen.kt')
c=read('src/main/kotlin/dev/alinco8/xmmp/config/XMMPConfig.kt')
s=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/SharingAdminControls.kt')
for token in ('LabelOption.createBuilder()', 'globalPublicationAcknowledged', 'adminGlobalAcknowledged',
              'GlobalSharingConsent.publishingMode(', 'GlobalSharingConsent.globalServerEnabled(',
              'mode == "GLOBAL"', 'withStyle(ChatFormatting.RED'):
    assert token in u, token
assert 'var receiveGlobalTerrain = false' in c
assert 'val allowGlobal: Boolean = false' in s
for name in ('en_us.yml','de_de.yml'):
    t=read('src/main/resources/assets/xmmp/lang/'+name)
    for token in ('globalRiskAcknowledged:', 'globalDanger:', 'irreversible:', 'warning:'):
        assert token in t, (name, token)
assert 'version=0.4.3' in read('gradle.properties')
assert 'fun publishingMode(' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/GlobalSharingConsent.kt')
assert 'fun globalServerEnabled(' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/GlobalSharingConsent.kt')
print('XMMP v0.4.1 PvP/Fog-of-War warning + explicit GLOBAL consent source checks OK')
