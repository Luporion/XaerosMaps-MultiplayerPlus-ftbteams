#!/usr/bin/env python3
"""v0.4.2 source contract checks, NOT a gameplay replacement."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
def read(p): return (r/p).read_text('utf8')
s=read('core/src/main/kotlin/dev/alinco8/xmmp/core/server/ServerSession.kt')
p=read('core/src/main/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicy.kt')
c=read('src/main/kotlin/dev/alinco8/xmmp/client/sync/ChunkDownloader.kt')
m=read('src/main/kotlin/dev/alinco8/xmmp/client/MapSyncSession.kt')
assert 'validPublication(' in s and 'validScope(' in s
assert 'SharingPolicy.canReceive(' in s and 'validPublication(uploaderId,state)' in s
assert 'if (mode == SharingMode.PRIVATE) return listOf(own)' not in p
assert 'if (mode == SharingMode.PRIVATE) return listOf(mine)' not in p
assert 'onRegionCompleted(' in c and 'runRetryLoop()' in c
assert 'RegionRetryPolicy.MAX_ATTEMPTS' in c
assert 'downloader.reset()' in m
assert 'downloader.onRegionCompleted(' in m
assert 'state.uploadsStored.incrementAndGet()' in s
assert 'playerState.regionThrottled.incrementAndGet()' in s
assert 'downloadPackets.get()' in s and 'sendSharingStatus(id, state)' in s
assert 'PACKET_VERSION = "8"' in read('core/src/main/kotlin/dev/alinco8/xmmp/core/XMMPShared.kt')
assert 'version=0.4.3' in read('gradle.properties')
print('XMMP v0.4.2 publication/reception, retry, diagnostics source contract OK')
