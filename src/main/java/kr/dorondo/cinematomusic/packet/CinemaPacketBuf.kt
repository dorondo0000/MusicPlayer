package kr.dorondo.cinematomusic.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets

class CinemaPacketBuf(private val buf: ByteBuf = Unpooled.buffer()) {
    
    fun writeInt(value: Int) {
        buf.writeInt(value)
    }
    
    fun writeLong(value: Long) {
        buf.writeLong(value)
    }
    
    fun writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(bytes.size)
        buf.writeBytes(bytes)
    }

    fun writeVarInt(value: Int) {
        var remaining = value
        while ((remaining and -128) != 0) {
            buf.writeByte((remaining and 127) or 128)
            remaining = remaining ushr 7
        }
        buf.writeByte(remaining)
    }

    fun writeFloat(value: Float) {
        buf.writeFloat(value)
    }
    
    fun writeBoolean(value: Boolean) {
        buf.writeBoolean(value)
    }
    
    fun array(): ByteArray {
        val bytes = ByteArray(buf.readableBytes())
        buf.getBytes(buf.readerIndex(), bytes)
        return bytes
    }
}
