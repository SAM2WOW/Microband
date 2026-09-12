package com.unsame.microband.band.protocol

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BandTimeZoneCodecTest {
    @Test
    fun `encodes current Pacific daylight offset`() {
        val payload = BandTimeZoneCodec.fixedOffsetPayload(
            ZoneId.of("America/Los_Angeles"),
            Instant.parse("2026-09-11T19:00:00Z"),
        )

        assertEquals(96, payload.size)
        assertEquals((-420).toShort(), BandPacketCodec.readShort(payload, 60).toShort())
    }

    @Test
    fun `parses Band local system time`() {
        val bytes = shortArrayOf(2026, 9, 5, 11, 19, 42, 3, 250)
            .flatMap { BandPacketCodec.littleEndianShort(it.toInt()).asIterable() }
            .toByteArray()

        assertEquals("2026-09-11T19:42:03.250", BandTimeZoneCodec.parseSystemTime(bytes).toString())
    }
}
