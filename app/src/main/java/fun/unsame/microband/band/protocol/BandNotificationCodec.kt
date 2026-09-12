package com.unsame.microband.band.protocol

import java.io.ByteArrayOutputStream
import java.util.UUID

/** Encodes the Band 2 (Envoy) protobuf form of a generic notification dialog. */
object BandNotificationCodec {
    const val GENERIC_DIALOG_MESSAGE_TYPE = 104
    val SMS_TILE_ID: UUID = UUID.fromString("b4edbc35-027b-4d10-a797-1099cd2ad98a")

    fun genericDialog(
        title: String,
        body: String,
        forceDialog: Boolean = true,
        tileId: UUID = SMS_TILE_ID,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val guid = ByteArrayOutputStream().apply {
            write(0x0A)
            writeLengthDelimited(BandPacketCodec.guidLittleEndian(tileId))
        }.toByteArray()
        output.write(0x0A)
        output.writeLengthDelimited(guid)
        output.writeUtf8Field(0x12, title, 40)
        output.writeUtf8Field(0x1A, body, 320)
        if (forceDialog) {
            output.write(0x20)
            output.write(1)
        }
        return output.toByteArray()
    }

    fun commandArguments(payloadSize: Int, messageType: Int = GENERIC_DIALOG_MESSAGE_TYPE): ByteArray {
        require(payloadSize in 0..0xFFFF)
        return BandPacketCodec.littleEndianShort(payloadSize) + BandPacketCodec.littleEndianShort(messageType)
    }

    private fun ByteArrayOutputStream.writeUtf8Field(tag: Int, value: String, maxBytes: Int) {
        val bytes = truncateUtf8(value.trim(), maxBytes)
        if (bytes.isEmpty()) return
        write(tag)
        writeLengthDelimited(bytes)
    }

    private fun ByteArrayOutputStream.writeLengthDelimited(bytes: ByteArray) {
        writeVarInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value
        do {
            var next = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) next = next or 0x80
            write(next)
        } while (remaining != 0)
    }

    internal fun truncateUtf8(value: String, maxBytes: Int): ByteArray {
        if (value.isEmpty()) return byteArrayOf()
        val output = ByteArrayOutputStream()
        value.codePoints().forEach { codePoint ->
            val encoded = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            if (output.size() + encoded.size <= maxBytes) output.write(encoded)
        }
        return output.toByteArray()
    }
}
