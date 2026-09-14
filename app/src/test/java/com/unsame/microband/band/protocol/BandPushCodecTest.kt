package com.unsame.microband.band.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandPushCodecTest {
    @Test
    fun decodesEnvoyReply() {
        val text = "On my way"
        val encoded = text.toByteArray(Charsets.UTF_16LE)
        val payload = ByteArray(348)
        BandPacketCodec.littleEndianInt(12345).copyInto(payload, 20)
        BandPacketCodec.littleEndianShort(encoded.size).copyInto(payload, 24)
        encoded.copyInto(payload, 26)

        assertEquals(BandReply(12345, text), BandPushCodec.decodeReply(payload))
    }

    @Test
    fun rejectsEmptyAndTruncatedReplies() {
        assertNull(BandPushCodec.decodeReply(ByteArray(12)))
        assertNull(BandPushCodec.decodeReply(ByteArray(348)))
    }
}
