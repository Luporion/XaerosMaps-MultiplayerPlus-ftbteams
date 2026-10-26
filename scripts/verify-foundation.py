#!/usr/bin/env python3
"""v0.1.0 fail-closed structural regression. Does not replace a NeoForge build."""
from pathlib import Path
import subprocess
import os

ROOT = Path(__file__).resolve().parents[1]
read=lambda p:(ROOT/p).read_text(encoding='utf-8')
policy=read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt')
session=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/ServerSession.kt')
server_handlers=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/network/ServerPacketHandlers.kt')
resolver=read('src/main/kotlin/dev/alinco8/xmmp/server/sharing/FtbTeamResolver.kt')
host=read('src/main/kotlin/dev/alinco8/xmmp/server/ServerHostImpl.kt')
assert 'TERRAIN_TRANSPORT_ENABLED = false' in policy
assert 'captureGrant' in policy and 'originalPartyTeam' in policy
for method in ['onPlayerJoin','onPlayerChannelsReady','onHandshake','onDimensionSync','onChunkUpload',
               'runUploadWorker','runDownloadWorker','onRegionSync','onDownloadAck']:
    prefix=session.split('fun '+method+'(',1)
    if len(prefix)==1:prefix=session.split('suspend fun '+method+'(',1)
    assert len(prefix)==2,method
    assert 'if (!terrainTransportEnabled) return' in prefix[1][:190],method
assert 'if (!session.terrainTransportEnabled) return' in server_handlers
assert 'if (!SharingPolicy.TERRAIN_TRANSPORT_ENABLED) return' in read('src/main/kotlin/dev/alinco8/xmmp/client/network/ClientPacketHandlers.kt')
assert 'if (!SharingPolicy.TERRAIN_TRANSPORT_ENABLED) return' in read('src/main/kotlin/dev/alinco8/xmmp/client/XMMPClient.kt')
assert 'getTeamForPlayerID' in resolver and 'isPartyTeam' in resolver and 'getMembers' in resolver
assert 'FTBTeamsAPI' in resolver and 'getPartyTeamId' in host
assert 'PACKET_VERSION = "4"' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/XMMPShared.kt')
assert 'PACKET_VERSION = "4"' in read('src/main/kotlin/dev/alinco8/xmmp/XMMP.kt')
assert 'val syncCaves: Boolean = false' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/config/ServerConfig.kt')
assert 'var checkUpdate = false' in read('src/main/kotlin/dev/alinco8/xmmp/config/XMMPConfig.kt')
assert 'version=0.1.0' in read('gradle.properties')
assert '21.1.249' in read('stonecutter.properties.toml')
assert '0.1.0' in read('stonecutter.properties.toml')
assert 'github.repository == \'Alinco8/XaerosMaps-MultiplayerPlus\'' in read('.github/workflows/release.yml')

jar=os.environ.get('FTB_TEAMS_JAR')
if jar:
    signatures=subprocess.check_output(['javap','-classpath',jar,'dev.ftb.mods.ftbteams.api.TeamManager'],text=True)
    for method in ['getTeamForPlayerID','getTeamByID','arePlayersInSameTeam']:
        assert method in signatures,method
    print('FTB Teams 2101.x API signatures: OK')
print('Rogler XMMP 0.1.0: fail-closed terrain gates, party resolution, fork isolation, version and cave defaults OK')
