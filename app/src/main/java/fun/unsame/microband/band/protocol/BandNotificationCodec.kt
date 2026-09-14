package com.unsame.microband.band.protocol

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

/** Encodes the Band 2 (Envoy) protobuf form of a generic notification dialog. */
object BandNotificationCodec {
    const val GENERIC_DIALOG_MESSAGE_TYPE = 104
    const val MESSAGING_MESSAGE_TYPE = 101
    val SMS_TILE_ID: UUID = UUID.fromString("b4edbc35-027b-4d10-a797-1099cd2ad98a")
    val CALLS_TILE_ID: UUID = UUID.fromString("22b1c099-f2be-4bac-8ed8-2d6b0b3c25d1")

    enum class CallType(val wireValue: Int) {
        Incoming(1),
        Answered(2),
        Missed(3),
        Hangup(4),
        Voicemail(5),
    }

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

    /** Creates a native Messages-tile entry using the Band 2 Envoy protobuf schema. */
    fun sms(sender: String, body: String, timestamp: Instant, callId: Int = 0, replyAvailable: Boolean = true): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeMessageField(0x0A, fileTime(timestamp))
        output.writeMessageField(0x12, guid(SMS_TILE_ID))
        if (callId != 0) output.writeVarIntField(0x18, callId)
        output.writeUtf8Field(0x2A, sender, 40)
        output.writeUtf8Field(0x32, body, 320)
        output.writeVarIntField(0x38, 2)
        if (!replyAvailable) output.writeVarIntField(0x50, 4)
        // Values used by the original Health app for the SMS presentation and reply model.
        output.writeVarIntField(0x68, 27)
        output.writeVarIntField(0x70, 16)
        return output.toByteArray()
    }

    fun cortana(status: Int, message: String): ByteArray {
        val encoded = message.take(160).toByteArray(Charsets.UTF_16LE).copyOf(320)
        return BandPacketCodec.littleEndianShort(status) +
            BandPacketCodec.littleEndianShort(320) + byteArrayOf(0, 0) + encoded
    }

    /** Creates or updates a native Calls-tile entry. */
    fun call(caller: String, callId: Int, timestamp: Instant, type: CallType): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeMessageField(0x0A, fileTime(timestamp))
        output.writeMessageField(0x12, guid(CALLS_TILE_ID))
        output.writeVarIntField(0x18, callId)
        output.writeUtf8Field(0x32, caller, 40)
        output.writeVarIntField(0x58, type.wireValue)
        if (type == CallType.Incoming) {
            output.writeVarIntField(0x68, 17)
            output.writeVarIntField(0x70, 24)
        }
        return output.toByteArray()
    }

    private fun guid(tileId: UUID): ByteArray = ByteArrayOutputStream().apply {
        write(0x0A)
        writeLengthDelimited(BandPacketCodec.guidLittleEndian(tileId))
    }.toByteArray()

    private fun fileTime(timestamp: Instant): ByteArray {
        val value = BandPacketCodec.windowsFileTime(timestamp)
        return ByteArrayOutputStream(10).apply {
            write(0x0D)
            write(BandPacketCodec.littleEndianInt(value.toInt()))
            write(0x15)
            write(BandPacketCodec.littleEndianInt((value ushr 32).toInt()))
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeUtf8Field(tag: Int, value: String, maxBytes: Int) {
        val bytes = truncateUtf8(value.trim(), maxBytes)
        if (bytes.isEmpty()) return
        write(tag)
        writeLengthDelimited(bytes)
    }

    private fun ByteArrayOutputStream.writeMessageField(tag: Int, bytes: ByteArray) {
        write(tag)
        writeLengthDelimited(bytes)
    }

    private fun ByteArrayOutputStream.writeVarIntField(tag: Int, value: Int) {
        write(tag)
        writeVarInt(value)
    }

    private fun ByteArrayOutputStream.writeLengthDelimited(bytes: ByteArray) {
        writeVarInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value.toUInt()
        do {
            var next = (remaining and 0x7Fu).toInt()
            remaining = remaining shr 7
            if (remaining != 0u) next = next or 0x80
            write(next)
        } while (remaining != 0u)
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
