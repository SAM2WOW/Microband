package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandActivitySummary
import com.unsame.microband.band.model.BandSleepSummary
import com.unsame.microband.band.model.BandDailyMetrics
import java.time.Instant

object BandHealthCodec {
    const val RUN_STATISTICS_SIZE = 50
    const val WORKOUT_STATISTICS_SIZE = 38
    const val SLEEP_STATISTICS_SIZE = 54
    const val PEDOMETER_WITH_DAILY_VALUES = 109
    const val CALORIES_WITH_DAILY_VALUES = 107
    const val DISTANCE_WITH_DAILY_VALUES = 108
    const val ELEVATION_WITH_DAILY_VALUES = 110
    const val UV_WITH_DAILY_VALUES = 111
    const val PEDOMETER = 19
    const val DISTANCE = 13
    const val CALORIES = 46

    fun dailySteps(subscriptionPayload: ByteArray): Long? {
        return dailyMetrics(subscriptionPayload).steps
    }

    fun dailyMetrics(subscriptionPayload: ByteArray): BandDailyMetrics {
        var offset = 0
        var metrics = BandDailyMetrics()
        while (offset + 4 <= subscriptionPayload.size) {
            val type = subscriptionPayload[offset].toInt() and 0xFF
            val sampleSize = BandPacketCodec.readShort(subscriptionPayload, offset + 2)
            offset += 4
            if (sampleSize < 0 || offset + sampleSize > subscriptionPayload.size) return metrics
            metrics = when {
                type == CALORIES_WITH_DAILY_VALUES && sampleSize >= 8 -> metrics.copy(calories = uint32(subscriptionPayload, offset + 4))
                type == DISTANCE_WITH_DAILY_VALUES && sampleSize >= 17 -> metrics.copy(distanceCentimeters = uint32(subscriptionPayload, offset + 13))
                type == PEDOMETER_WITH_DAILY_VALUES && sampleSize >= 8 -> metrics.copy(steps = uint32(subscriptionPayload, offset + 4))
                type == ELEVATION_WITH_DAILY_VALUES && sampleSize >= 42 -> metrics.copy(
                    flightsAscended = uint32(subscriptionPayload, offset + 34),
                    elevationGainCentimeters = uint32(subscriptionPayload, offset + 38),
                )
                type == UV_WITH_DAILY_VALUES && sampleSize >= 5 -> metrics.copy(uvExposure = uint32(subscriptionPayload, offset + 1))
                type == PEDOMETER && sampleSize >= 13 -> metrics.copy(steps = uint32(subscriptionPayload, offset), cumulativeSinceReset = true)
                type == CALORIES && sampleSize >= 20 -> metrics.copy(calories = uint32(subscriptionPayload, offset), cumulativeSinceReset = true)
                type == DISTANCE && sampleSize >= 22 -> metrics.copy(distanceCentimeters = uint32(subscriptionPayload, offset), cumulativeSinceReset = true)
                else -> metrics
            }
            offset += sampleSize
        }
        return metrics
    }

    fun run(bytes: ByteArray): BandActivitySummary {
        require(bytes.size == RUN_STATISTICS_SIZE)
        require(BandPacketCodec.readShort(bytes, 8) > 0) { "No saved run" }
        return BandActivitySummary(
            startedAt = fileTime(bytes, 0),
            durationMillis = uint32(bytes, 10),
            distanceCentimeters = uint32(bytes, 14),
            calories = uint32(bytes, 26),
            averageHeartRate = uint32(bytes, 30),
            maximumHeartRate = uint32(bytes, 34),
            endedAt = fileTime(bytes, 38),
        ).validated("run")
    }

    fun workout(bytes: ByteArray): BandActivitySummary {
        require(bytes.size == WORKOUT_STATISTICS_SIZE)
        require(BandPacketCodec.readShort(bytes, 8) > 0) { "No saved workout" }
        return BandActivitySummary(
            startedAt = fileTime(bytes, 0),
            durationMillis = uint32(bytes, 10),
            calories = uint32(bytes, 14),
            averageHeartRate = uint32(bytes, 18),
            maximumHeartRate = uint32(bytes, 22),
            endedAt = fileTime(bytes, 26),
        ).validated("workout")
    }

    fun sleep(bytes: ByteArray): BandSleepSummary {
        require(bytes.size == SLEEP_STATISTICS_SIZE)
        require(BandPacketCodec.readShort(bytes, 8) > 0) { "No saved sleep" }
        return BandSleepSummary(
            startedAt = fileTime(bytes, 0),
            durationMillis = uint32(bytes, 10),
            timesWokeUp = uint32(bytes, 14),
            timeAsleepMillis = uint32(bytes, 22),
            calories = uint32(bytes, 26),
            restingHeartRate = uint32(bytes, 30),
            endedAt = fileTime(bytes, 38),
            timeToFallAsleepMillis = uint32(bytes, 46),
        ).also {
            require(it.startedAt != null && it.endedAt != null && !it.endedAt.isBefore(it.startedAt)) { "Invalid sleep timestamps" }
            require(it.durationMillis >= 60_000 && it.timeAsleepMillis <= it.durationMillis) { "Empty sleep record" }
        }
    }

    private fun BandActivitySummary.validated(label: String) = also {
        require(it.startedAt != null && it.endedAt != null && !it.endedAt.isBefore(it.startedAt)) { "Invalid $label timestamps" }
        require(it.durationMillis >= 30_000) { "Empty $label record" }
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        BandPacketCodec.readInt(bytes, offset).toUInt().toLong()

    private fun fileTime(bytes: ByteArray, offset: Int): Instant? = runCatching {
        BandPacketCodec.instantFromWindowsFileTime(BandPacketCodec.readLong(bytes, offset))
    }.getOrNull()?.takeIf { it.epochSecond in 946_684_800L..4_102_444_800L }
}
