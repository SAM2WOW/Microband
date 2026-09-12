package com.unsame.microband.band.protocol

import com.unsame.microband.band.protocol.BandPacketCodec.hex
import org.junit.Assert.assertEquals
import org.junit.Test

class BandPersonalizationCodecTest {
    @Test
    fun themeContainsSixOpaqueLittleEndianColors() {
        val encoded = BandPersonalizationCodec.encodeTheme(
            BandPersonalizationCodec.themeFromAccent(0xFF0078D7.toInt()),
        )

        assertEquals(24, encoded.size)
        assertEquals("D77800FF", encoded.copyOfRange(0, 4).hex())
        for (offset in 3 until 24 step 4) assertEquals(0xFF.toByte(), encoded[offset])
    }
}
