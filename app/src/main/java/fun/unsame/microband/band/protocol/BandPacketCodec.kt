package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandException
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID

data class BandStatus(val raw: UInt) {
    val isError: Boolean get() = raw and 0x8000_0000u != 0u
    val facility: Int get() = ((raw shr 16) and 0x7FFu).toInt()
    val code: Int get() = (raw and 0xFFFFu).toInt()
}

data class BandRawResponse(val payload: ByteArray, val status: BandStatus)

object BandPacketCodec {
    fun commandId(facility: Int, transferless: Boolean, code: Int): Int =
        (facility shl 8) or (if (transferless) 0x80 else 0) or (code and 0x7F)

    fun command(
        facility: Int,
        transferless: Boolean,
        code: Int,
        dataLength: Int,
        arguments: ByteArray = byteArrayOf(),
    ): ByteArray = ByteBuffer.allocate(8 + arguments.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort(BandConstants.COMMAND_MARKER.toShort())
        .put((code and 0x7F or if (transferless) 0x80 else 0).toByte())
        .put(facility.toByte())
        .putInt(dataLength)
        .put(arguments)
        .array()

    fun frame(command: ByteArray): ByteArray {
        require(command.size <= 255) { "Band command frame is limited to 255 bytes" }
        return byteArrayOf(command.size.toByte()) + command
    }

    fun parseStatus(bytes: ByteArray): BandStatus {
        if (bytes.size != 6) throw BandException.InvalidPacket("Status packet must be 6 bytes")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.short.toInt() and 0xFFFF != BandConstants.STATUS_MARKER) {
            throw BandException.InvalidPacket("Missing Band status marker")
        }
        return BandStatus(buffer.int.toUInt())
    }

    fun littleEndianInt(value: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun littleEndianShort(value: Int): ByteArray = ByteBuffer.allocate(2)
        .order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    fun littleEndianLong(value: Long): ByteArray = ByteBuffer.allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    fun readInt(bytes: ByteArray, offset: Int = 0): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    fun readShort(bytes: ByteArray, offset: Int = 0): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    fun readLong(bytes: ByteArray, offset: Int = 0): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long

    fun windowsFileTime(instant: Instant): Long =
        Math.addExact(Math.multiplyExact(instant.epochSecond, 10_000_000L), 116_444_736_000_000_000L) + instant.nano / 100

    fun instantFromWindowsFileTime(value: Long): Instant {
        val unixTicks = value - 116_444_736_000_000_000L
        return Instant.ofEpochSecond(unixTicks / 10_000_000L, (unixTicks % 10_000_000L) * 100L)
    }

    fun guidLittleEndian(uuid: UUID): ByteArray {
        val out = ByteArrayOutputStream(16)
        val data1 = (uuid.mostSignificantBits ushr 32).toInt()
        val data2 = (uuid.mostSignificantBits ushr 16).toInt() and 0xFFFF
        val data3 = uuid.mostSignificantBits.toInt() and 0xFFFF
        out.write(littleEndianInt(data1))
        out.write(littleEndianShort(data2))
        out.write(littleEndianShort(data3))
        for (shift in 56 downTo 0 step 8) out.write((uuid.leastSignificantBits ushr shift).toInt() and 0xFF)
        return out.toByteArray()
    }

    fun ByteArray.hex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}

fun InputStream.readExactBlocking(length: Int): ByteArray {
    require(length >= 0)
    val result = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = read(result, offset, length - offset)
        if (count == -1) throw EOFException("Band disconnected")
        if (count == 0) continue
        offset += count
    }
    return result
}
