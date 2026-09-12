package com.unsame.microband.band.protocol

import com.unsame.microband.band.protocol.BandPacketCodec.hex
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
