package com.unsame.microband.band.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BandHealthCodecTest {
    @Test
    fun readsDailyStepsFromSubscriptionSample() {
        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put(BandHealthCodec.PEDOMETER_WITH_DAILY_VALUES.toByte())
            .put(0)
            .putShort(8)
            .putInt(123_456)
            .putInt(7_890)
            .array()

        assertEquals(7_890L, BandHealthCodec.dailySteps(payload))
    }

    @Test
    fun readsLegacyCumulativeStepsWithoutCallingThemDaily() {
        val payload = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            .put(BandHealthCodec.PEDOMETER.toByte())
            .put(0)
            .putShort(13)
            .putInt(54_321)
            .put(ByteArray(9))
            .array()

        val metrics = BandHealthCodec.dailyMetrics(payload)
        assertEquals(54_321L, metrics.steps)
        assertEquals(true, metrics.cumulativeSinceReset)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDefaultWorkoutRecord() {
        BandHealthCodec.workout(ByteArray(BandHealthCodec.WORKOUT_STATISTICS_SIZE))
    }

    @Test
    fun readsLastWorkoutStatistics() {
        val start = Instant.parse("2026-09-12T08:00:00Z")
        val end = Instant.parse("2026-09-12T08:30:00Z")
        val bytes = ByteBuffer.allocate(BandHealthCodec.WORKOUT_STATISTICS_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(BandPacketCodec.windowsFileTime(start))
            .putShort(1)
            .putInt(1_800_000)
            .putInt(250)
            .putInt(132)
            .putInt(170)
            .putLong(BandPacketCodec.windowsFileTime(end))
            .putInt(0)
            .array()

        val workout = BandHealthCodec.workout(bytes)
        assertEquals(start, workout.startedAt)
        assertEquals(end, workout.endedAt)
        assertEquals(1_800_000L, workout.durationMillis)
        assertEquals(250L, workout.calories)
        assertEquals(132L, workout.averageHeartRate)
    }

    @Test
    fun derivesSleepStartFromEndAndDuration() {
        // Captured from Band 2 firmware 2.0.5202.0. The first FILETIME is a
        // record timestamp and can be later than EndTime; it is not StartTime.
        val bytes = "1C7E45C0BC43DD0107008D2ACC0108000000B0A37800DC8653018C020000430000005200000010529495B543DD0171890F0002000000"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val sleep = BandHealthCodec.sleep(bytes)

        assertEquals(30_157_453L, sleep.durationMillis)
        assertEquals(22_251_228L, sleep.timeAsleepMillis)
        assertEquals(8L, sleep.timesWokeUp)
        assertEquals(67L, sleep.restingHeartRate)
        assertEquals(sleep.endedAt?.minusMillis(sleep.durationMillis), sleep.startedAt)
    }
}
