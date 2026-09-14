package com.unsame.microband.band.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class BandKeyboardCodecTest {
    @Test
    fun createsFixedSizeKeyboardInitialization() {
        val command = BandKeyboardCodec.command(BandKeyboardCodec.INIT)
        assertEquals(407, command.size)
        assertEquals(0, command[0].toInt())
        assertEquals(0, BandPacketCodec.readInt(command, 3))
    }

    @Test
    fun readsEventSubtype() {
        assertEquals(BandKeyboardCodec.PRE_INIT_V2, BandKeyboardCodec.eventType(byteArrayOf(7, 0)))
    }
}
