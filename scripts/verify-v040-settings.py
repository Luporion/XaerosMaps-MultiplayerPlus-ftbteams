#!/usr/bin/env python3
"""Static v0.4.0 contract assertions; CI compile/JUnit are separate gates."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
read=lambda p:(r/p).read_text('utf8')
a=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/ServerSession.kt')
h=read('core/src/main/kotlin/dev/alinco8/xmmp/core/network/packet/C2SAdminSettings.kt')
s=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/SharingAdminControls.kt')
p=read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt')
u=read('src/main/kotlin/dev/alinco8/xmmp/config/ConfigScreen.kt')
c=read('src/main/kotlin/dev/alinco8/xmmp/client/ClientSession.kt')
assert 'host.isMapAdministrator(playerId)' in a
assert 'if (!terrainTransportEnabled || !host.isMapAdministrator(playerId)) return' in a
assert 'state.receiveTeam=packet.receiveTeam' in a and 'state.receiveGlobal=packet.receiveGlobal' in a
assert 'SharingPolicy.canReceive(identity, state.sharingMode, claimed,' in a
assert 'filter(adminControls::allows)' in a
assert 'adminControls.settings.allowUploads' in a
assert 'StandardCopyOption.ATOMIC_MOVE' in s
assert 'SharingAdminSettings(false, false, false)' in s
assert 'data class C2SAdminSettings' in h
assert 'receiveTeam: Boolean, receiveGlobal: Boolean' in p
assert 'onSharingPreferencesSaved()' in c
assert 'syncDiagnostics()' in c
assert 'CyclingListControllerBuilder.create(option)' in u
assert 'status?.admin == true' in u
assert 'PACKET_VERSION = "7"' in read('src/main/kotlin/dev/alinco8/xmmp/XMMP.kt')
assert 'version=0.4.0' in read('gradle.properties')
assert 'runSharingAdminControlsChecks()' in read('core/src/test/kotlin/dev/alinco8/xmmp/core/server/SharingAdminControlsGradleTest.kt')
assert 'de_de.yml' in '\n'.join(str(x.name) for x in (r/'src/main/resources/assets/xmmp/lang').glob('*.yml'))
print('XMMP v0.4.0 YACL, admin authorization, ACL, persistence and status checks OK')
