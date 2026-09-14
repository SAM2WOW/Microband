package com.unsame.microband.band.protocol

import com.unsame.microband.band.protocol.BandPacketCodec.hex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BandNotificationCodecTest {
    @Test
    fun genericDialogMatchesBand2ProtobufLayout() {
        val packet = BandNotificationCodec.genericDialog("A", "B")

        assertEquals(
            "0A120A1035BCEDB47B02104DA7971099CD2AD98A1201411A01422001",
            packet.hex(),
        )
        assertEquals("1C006800", BandNotificationCodec.commandArguments(packet.size).hex())
    }

    @Test
    fun utf8TruncationDoesNotSplitCodePoints() {
        assertEquals("abc", BandNotificationCodec.truncateUtf8("abcdef", 3).decodeToString())
        assertEquals("😀", BandNotificationCodec.truncateUtf8("😀x", 4).decodeToString())
    }

    @Test
    fun smsUsesNativeMessagesTileAndMessagingEnvelope() {
        val packet = BandNotificationCodec.sms("Alex", "Hello", Instant.EPOCH)

        assertEquals(true, packet.hex().contains("35BCEDB47B02104DA7971099CD2AD98A"))
        assertEquals(
            BandPacketCodec.littleEndianShort(packet.size).hex() + "6500",
            BandNotificationCodec.commandArguments(packet.size, BandNotificationCodec.MESSAGING_MESSAGE_TYPE).hex(),
        )
    }

    @Test
    fun smsCanSuppressUnavailableAndroidReply() {
        val packet = BandNotificationCodec.sms("Alex", "Hello", Instant.EPOCH, replyAvailable = false)
        assertEquals(true, packet.toList().windowed(2).any { it == listOf(0x50.toByte(), 0x04.toByte()) })
    }

    @Test
    fun callUsesNativeCallsTileAndCallState() {
        val packet = BandNotificationCodec.call("Alex", 42, Instant.EPOCH, BandNotificationCodec.CallType.Missed)
        val hex = packet.hex()

        assertEquals(true, hex.contains("99C0B122BEF2AC4B8ED82D6B0B3C25D1"))
        assertEquals(true, hex.endsWith("5803"))
    }
}
