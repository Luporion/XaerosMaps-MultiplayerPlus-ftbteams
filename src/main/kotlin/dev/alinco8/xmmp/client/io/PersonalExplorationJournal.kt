package dev.alinco8.xmmp.client.io

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** v0.3.0: terrain provenance is LOCAL and world/dimension specific.
 * Never treat a downloaded Xaero tile as a native visit. A genuine player visit
 * after receiving a shared tile is separately recorded, even if Xaero never
 * rewrites that tile. This metadata does NOT yet restore revoked bitmap pixels.
 */
class PersonalExplorationJournal(private val path: Path) {
    data class Position(val layer: Int, val x: Int, val z: Int)
    private val flags = ConcurrentHashMap<Position, Int>()
    @Volatile private var dirty = false
    companion object {
        private const val MAGIC = 0x584D5033 // XMP3
        private const val VERSION = 1
        private const val MAX_ENTRIES = 2_000_000
        private const val MAX_FILE_SIZE = 26_000_012L
        const val NATIVE = 1
        const val VISITED = 2
        const val RECEIVED = 4

        fun open(path: Path): PersonalExplorationJournal {
            val store=PersonalExplorationJournal(path)
            if (!Files.exists(path)) return store
            require(Files.size(path) <= MAX_FILE_SIZE) { "Oversized provenance journal" }
            DataInputStream(Files.newInputStream(path).buffered()).use { input ->
                require(input.readInt()==MAGIC && input.readInt()==VERSION) { "Unknown provenance format" }
                val count=input.readInt()
                require(count in 0..MAX_ENTRIES && Files.size(path)==12L+count*13L) { "Corrupt provenance length" }
                repeat(count) {
                    val layer=input.readUnsignedByte(); val x=input.readInt();val z=input.readInt();val bits=input.readInt()
                    require(layer in 0..1 && bits in 1..7) { "Invalid provenance record" }
                    require(store.flags.putIfAbsent(Position(layer,x,z),bits)==null) { "Duplicate provenance record" }
                }
            }
            return store
        }
    }
    fun mark(layer: Int,x:Int,z:Int,tag:Int) {
        require(layer in 0..1 && tag in listOf(NATIVE,VISITED,RECEIVED))
        val pos=Position(layer,x,z)
        if (flags.size>=MAX_ENTRIES && !flags.containsKey(pos)) return
        synchronized(flags) {
            val current=flags[pos]?:0
            val next=current or tag
            if(current!=next) { flags[pos]=next;dirty=true }
        }
    }
    fun isOwn(layer:Int,x:Int,z:Int)= ((flags[Position(layer,x,z)]?:0) and (NATIVE or VISITED))!=0
    fun isNative(layer:Int,x:Int,z:Int)= ((flags[Position(layer,x,z)]?:0) and NATIVE)!=0
    fun isReceived(layer:Int,x:Int,z:Int)= ((flags[Position(layer,x,z)]?:0) and RECEIVED)!=0
    fun size()=flags.size

    @Synchronized fun flush() {
        val snapshot=synchronized(flags){
            if(!dirty)return
            dirty=false
            flags.entries.toList()
        }
        Files.createDirectories(path.parent)
        val temp=Files.createTempFile(path.parent,".xmmp-personal-",".tmp")
        try {
            DataOutputStream(Files.newOutputStream(temp).buffered()).use { out ->
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(snapshot.size)
                snapshot.forEach { (key,bits)->
                    out.writeByte(key.layer);out.writeInt(key.x);out.writeInt(key.z);out.writeInt(bits)
                }
            }
            try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
            catch (_:java.nio.file.AtomicMoveNotSupportedException){
                Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (ex: Exception) {
            dirty=true
            throw ex
        } finally { Files.deleteIfExists(temp) }
    }
}
