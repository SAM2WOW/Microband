package com.unsame.microband.band.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BandTileCodecTest {
    @Test
    fun decodeReadsNamesAndSortsByOrder() {
        val payload = ByteBuffer.allocate(4 + BandTileCodec.RECORD_SIZE * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2)
            .put(record("Settings", order = 1, idByte = 2))
            .put(record("Me", order = 0, idByte = 1))
            .array()

        val decoded = BandTileCodec.decode(payload)

        assertEquals(listOf("Me", "Settings"), decoded.map { it.name })
        assertEquals(listOf(0, 1), decoded.map { it.order })
    }

    @Test
    fun encodeRewritesOrderWithoutChangingTileData() {
        val me = BandTileCodec.decode(wrapped(record("Me", 4, 1))).single()
        val settings = BandTileCodec.decode(wrapped(record("Settings", 7, 2))).single()

        val encoded = BandTileCodec.encode(listOf(settings, me))
        val decoded = BandTileCodec.decode(encoded)

        assertEquals(listOf("Settings", "Me"), decoded.map { it.name })
        assertEquals(listOf(0, 1), decoded.map { it.order })
        assertEquals(listOf(settings.id, me.id), decoded.map { it.id })
    }

    private fun wrapped(record: ByteArray): ByteArray = ByteBuffer.allocate(4 + record.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(1)
        .put(record)
        .array()

    private fun record(name: String, order: Int, idByte: Int): ByteArray {
        val raw = ByteArray(BandTileCodec.RECORD_SIZE)
        raw.fill(idByte.toByte(), 0, 16)
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(16, order)
        buffer.putShort(24, name.length.toShort())
        name.toByteArray(Charsets.UTF_16LE).copyInto(raw, 28)
        return raw
    }
}
