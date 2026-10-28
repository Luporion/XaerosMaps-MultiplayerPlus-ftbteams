#!/usr/bin/env python3
"""Static source-level privacy checks; not a substitute for Gradle or real multiplayer."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
read=lambda p:(r/p).read_text('utf8')
s=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/ServerSession.kt')
p=read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt')
c=read('src/main/kotlin/dev/alinco8/xmmp/client/ClientSession.kt')
ms=read('src/main/kotlin/dev/alinco8/xmmp/client/MapSyncSession.kt')
flow=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/Outbound.kt')
codec=read('core/src/main/kotlin/dev/alinco8/xmmp/core/network/packet/S2CChunkData.kt')
journal=read('src/main/kotlin/dev/alinco8/xmmp/client/io/PersonalExplorationJournal.kt')
assert 'readableScopes(identity: SharingIdentity, mode: SharingMode)' in p
assert 'canReceive(identity: SharingIdentity' in p
assert 'SharingPolicy.readableScopes(identity,playerState.sharingMode,' in s
assert 'SharingPolicy.canReceive(identity, state.sharingMode, claimed,' in s
assert 'state.activeScope != effectiveScope(playerId, state)' in s
assert 'validScope(playerId,state,item.scopeId)' in s
assert 'validScope(playerId,playerState,scopeId)' in s
assert 'getRegionStore(item.scopeId,dimension,item.layer)' in s
assert 'ChunkKey.fromLocal(regionPos, chunkX, chunkZ),scopeId,syncId' in s
assert 'scopeId: String, val syncId: Int' in flow
assert 'val scopeId: String' in codec and 'val syncId: Int' in codec
assert 'boundedTilePayload' in codec
assert 'scopeId != privateScopeId && personalJournal.isNative' in ms
assert 'PersonalExplorationJournal.VISITED' in ms
assert 'PersonalExplorationJournal.RECEIVED' in ms
assert 'Pair<String,ChunkKey>' in ms and 'Pair<String,RegionKey>' in ms
assert 'val appliedPriority = ConcurrentHashMap<ChunkKey,Int>()' in ms
assert 'cursorBase = base.resolve("scoped-v3")' in ms
assert 'personalJournal.flush()' in ms
assert 'scopeId !in activeScopes' in ms and 'packetSyncId != syncId' in ms
assert 'session.resetGeneration()' in c and 'onPersonalVisit(position.x,position.z)' in c
assert 'private const val MAX_ENTRIES' in journal and 'ATOMIC_MOVE' in journal
assert 'PACKET_VERSION = "7"' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/XMMPShared.kt')
assert 'PACKET_VERSION = "7"' in read('src/main/kotlin/dev/alinco8/xmmp/XMMP.kt')
assert 'version=0.4.0' in read('gradle.properties')
assert 'version = "0.4.0"' in read('stonecutter.properties.toml')
assert 'hasAuthenticatedPlayers' in s
print('XMMP v0.4.0 scope ACL / provenance / packet guards: source checks OK')
