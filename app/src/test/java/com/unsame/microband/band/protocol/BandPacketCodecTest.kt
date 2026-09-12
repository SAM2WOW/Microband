package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandException
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BandPacketCodecTest {
    @Test
    fun `encodes read command header in little endian`() {
        val packet = BandPacketCodec.command(
            facility = BandConstants.FACILITY_JUTIL,
            transferless = true,
            code = 3,
            dataLength = 1,
        )
        assertArrayEquals(byteArrayOf(0xF9.toByte(), 0x2E, 0x83.toByte(), 0x76, 1, 0, 0, 0), packet)
    }

    @Test
    fun `adds one byte RFCOMM frame length`() {
        val command = ByteArray(8) { it.toByte() }
        assertArrayEquals(byteArrayOf(8) + command, BandPacketCodec.frame(command))
    }

    @Test
    fun `parses successful and error status packets`() {
        val success = BandPacketCodec.parseStatus(byteArrayOf(0xFE.toByte(), 0xA6.toByte(), 0, 0, 0, 0))
        assertFalse(success.isError)

        val error = BandPacketCodec.parseStatus(byteArrayOf(0xFE.toByte(), 0xA6.toByte(), 2, 0, 0xC3.toByte(), 0x80.toByte()))
        assertTrue(error.isError)
        assertEquals(0xC3, error.facility)
        assertEquals(2, error.code)
    }

    @Test
    fun `rejects malformed status marker`() {
        assertThrows(BandException.InvalidPacket::class.java) {
            BandPacketCodec.parseStatus(ByteArray(6))
        }
    }

    @Test
    fun `readExact handles partial stream reads`() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val partial = object : InputStream() {
            private val delegate = ByteArrayInputStream(source)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, minOf(2, length))
        }
        assertArrayEquals(source, partial.readExactBlocking(source.size))
    }

    @Test
    fun `readExact treats minus one as disconnect`() {
        assertThrows(EOFException::class.java) {
            ByteArrayInputStream(byteArrayOf(1)).readExactBlocking(2)
        }
    }

    @Test
    fun `Windows file time round trips`() {
        val time = Instant.parse("2026-09-11T12:34:56.123456700Z")
        assertEquals(time, BandPacketCodec.instantFromWindowsFileTime(BandPacketCodec.windowsFileTime(time)))
    }

    @Test
    fun `GUID encoding uses Microsoft little endian layout`() {
        val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
        assertArrayEquals(
            byteArrayOf(0x33, 0x22, 0x11, 0x00, 0x55, 0x44, 0x77, 0x66, 0x88.toByte(), 0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            BandPacketCodec.guidLittleEndian(uuid),
        )
    }
}
