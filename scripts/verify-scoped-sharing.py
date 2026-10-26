#!/usr/bin/env python3
"""Static privacy contract tests for v0.2.0; no claim of a full NeoForge build."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
read=lambda p:(r/p).read_text('utf-8')
s=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/ServerSession.kt')
p=read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt')
c=read('src/main/kotlin/dev/alinco8/xmmp/client/ClientSession.kt')
ms=read('src/main/kotlin/dev/alinco8/xmmp/client/MapSyncSession.kt')
codec=read('core/src/main/kotlin/dev/alinco8/xmmp/core/network/XMMPStreamCodec.kt')
assert 'TERRAIN_TRANSPORT_ENABLED = true' in p
assert '!standalone && host.hasAuthenticatedPlayers' in s
assert 'Standalone sharing disabled: authenticated NeoForge server required' in read('src/main/kotlin/dev/alinco8/xmmp/client/XMMPClient.kt')
assert 'override val hasAuthenticatedPlayers: Boolean get() = true' in read('src/main/kotlin/dev/alinco8/xmmp/server/ServerHostImpl.kt')
assert 'scope(identity: SharingIdentity, mode: SharingMode)' in p
assert 'private-${identity.playerId}' in p and 'team-$it' in p
assert 'isValidScope' in p
assert 'scoped-v2' in s and '.resolve("world_map")' in s
assert 'worldDataDir\n            .resolve("world_map")' not in s
assert 'getRegionStore(scopeId,dimension' in s
assert 'getRegionStore(privateScope,dimension' in s
assert 'validScope(playerId,receiver,scopeId)' in s
assert 'validScope(playerId,playerState,scopeId)' in s
assert 'validScope(playerId,state,scopeId)' in s
assert 'state.outbound.clear()' in s
assert 'host.getPartyTeamId(playerId)' in s
assert 'host.canObserveChunk' in s
assert 'host.validateChunkBytes' in s
assert 'privateScope="private-$uploaderId"' in s
assert 'scopeId=effectiveScope(playerId,playerState)' in s
assert 'getRegionStore(scopeId,dimension,item.layer)' in s
assert 'playerState.outbound.enqueueChunk' in s and 'return@launch // no Done' in s
assert 'state.activeScope==claimed' not in s or 'claimed == effectiveScope(playerId,state)' in s
assert 'readByteArray(262_144)' in codec
assert 'Map size outside safe bounds' in codec
assert 'terrainSharingMode = "PRIVATE"' in read('src/main/kotlin/dev/alinco8/xmmp/config/XMMPConfig.kt')
assert 'sharingMode:' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/network/packet/C2SHandshake.kt')
for fn in ['C2SRegionSync','S2CRegionIndex','S2CRegionSyncDone']:
 assert 'scopeId' in read(f'core/src/main/kotlin/dev/alinco8/xmmp/core/network/packet/{fn}.kt')
assert 'cursorBase = base.resolve("scoped-v2")' in ms and 'scopeId' in ms
assert 'scopeId' in read('src/main/kotlin/dev/alinco8/xmmp/client/sync/ChunkDownloader.kt')
assert 'scopeId' in c
assert 'PACKET_VERSION = "5"' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/XMMPShared.kt')
assert 'PACKET_VERSION = "5"' in read('src/main/kotlin/dev/alinco8/xmmp/XMMP.kt')
assert 'version=0.2.0' in read('gradle.properties')
assert 'version = "0.2.0"' in read('stonecutter.properties.toml')
print('XMMP v0.2.0 scoped data, per-player private provenance, team authorization, cursors and payload bounds: static checks OK')
