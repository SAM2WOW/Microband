package com.unsame.microband.band.model

import java.time.Instant

data class BandActivitySummary(
    val startedAt: Instant?,
    val endedAt: Instant?,
    val durationMillis: Long,
    val calories: Long,
    val averageHeartRate: Long,
    val maximumHeartRate: Long,
    val distanceCentimeters: Long? = null,
)

data class BandSleepSummary(
    val startedAt: Instant?,
    val endedAt: Instant?,
    val durationMillis: Long,
    val timeAsleepMillis: Long,
    val timesWokeUp: Long,
    val calories: Long,
    val restingHeartRate: Long,
    val timeToFallAsleepMillis: Long,
)

data class BandDailyMetrics(
    val steps: Long? = null,
    val calories: Long? = null,
    val distanceCentimeters: Long? = null,
    val flightsAscended: Long? = null,
    val elevationGainCentimeters: Long? = null,
    val uvExposure: Long? = null,
    val cumulativeSinceReset: Boolean = false,
) {
    val hasData: Boolean
        get() = listOf(steps, calories, distanceCentimeters, flightsAscended, elevationGainCentimeters, uvExposure)
            .any { it != null }
}

data class BandHealthSnapshot(
    val syncedAt: Instant,
    val stepsToday: Long?,
    val daily: BandDailyMetrics? = null,
    val lastRun: BandActivitySummary?,
    val lastWorkout: BandActivitySummary?,
    val lastSleep: BandSleepSummary?,
)
