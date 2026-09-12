package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object BandTimeZoneCodec {
    private const val SYSTEM_TIME_SIZE = 16

    fun fixedOffsetPayload(zoneId: ZoneId, instant: Instant): ByteArray {
        val name = zoneId.id.take(30).toByteArray(Charsets.UTF_16LE).copyOf(60)
        val offsetMinutes = zoneId.rules.getOffset(instant).totalSeconds / 60
        return name +
            BandPacketCodec.littleEndianShort(offsetMinutes) +
            BandPacketCodec.littleEndianShort(0) +
            ByteArray(SYSTEM_TIME_SIZE * 2)
    }

    fun parseSystemTime(bytes: ByteArray): LocalDateTime {
        if (bytes.size != SYSTEM_TIME_SIZE) throw BandException.InvalidPacket("Band local time must be 16 bytes")
        return try {
            LocalDateTime.of(
                BandPacketCodec.readShort(bytes, 0),
                BandPacketCodec.readShort(bytes, 2),
                BandPacketCodec.readShort(bytes, 6),
                BandPacketCodec.readShort(bytes, 8),
                BandPacketCodec.readShort(bytes, 10),
                BandPacketCodec.readShort(bytes, 12),
                BandPacketCodec.readShort(bytes, 14) * 1_000_000,
            )
        } catch (exception: RuntimeException) {
            throw BandException.InvalidPacket("Band returned an invalid local time")
        }
    }
}
