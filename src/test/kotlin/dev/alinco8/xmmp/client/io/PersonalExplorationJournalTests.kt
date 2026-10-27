package dev.alinco8.xmmp.client.io

import java.nio.file.Files

fun main() {
    val dir=Files.createTempDirectory("xmmp-personal-provenance-test-")
    val path=dir.resolve("personal.bin")
    var checks=0
    fun verify(ok:Boolean){ checks++; check(ok) { "Personal provenance check $checks" } }
    try {
        var p=PersonalExplorationJournal.open(path)
        verify(p.size()==0)
        p.mark(0,17,29,PersonalExplorationJournal.RECEIVED)
        verify(p.isReceived(0,17,29))
        verify(!p.isOwn(0,17,29))
        // User later visits an already shared tile: personal visit MUST persist.
        p.mark(0,17,29,PersonalExplorationJournal.VISITED)
        verify(p.isOwn(0,17,29))
        verify(!p.isNative(0,17,29))
        p.mark(1,17,29,PersonalExplorationJournal.NATIVE)
        verify(p.isNative(1,17,29))
        verify(!p.isNative(0,17,29))
        p.flush()
        verify(Files.size(path)==12L+2L*13)
        p=PersonalExplorationJournal.open(path)
        verify(p.size()==2)
        verify(p.isOwn(0,17,29)&&p.isReceived(0,17,29))
        verify(p.isNative(1,17,29))
        p.mark(0,-1,Int.MIN_VALUE,PersonalExplorationJournal.NATIVE)
        p.flush()
        verify(PersonalExplorationJournal.open(path).isOwn(0,-1,Int.MIN_VALUE))
        Files.writeString(path,"invalid")
        verify(runCatching {PersonalExplorationJournal.open(path)}.isFailure)
        verify(!Files.readString(path).startsWith("XMP3"))
        println("XMMP v0.3.0 PersonalExplorationJournalTests: $checks checks OK")
    } finally { Files.deleteIfExists(path);Files.deleteIfExists(dir) }
}
