package com.unsame.microband.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unsame.microband.band.model.BandActivitySummary
import com.unsame.microband.band.model.BandHealthSnapshot
import com.unsame.microband.band.model.BandSleepSummary
import com.unsame.microband.band.model.BandDailyMetrics
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "protocol_packet_log")
data class ProtocolPacketLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val direction: String,
    val transport: String,
    val commandId: Int?,
    val payloadLength: Int,
    val hexPayload: String,
    val parsedStatus: String?,
)

@Dao
interface PacketLogDao {
    @Insert
    suspend fun insert(entry: ProtocolPacketLog)

    @Query("SELECT * FROM protocol_packet_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 80): Flow<List<ProtocolPacketLog>>

    @Query("DELETE FROM protocol_packet_log")
    suspend fun clear()

    @Query("DELETE FROM protocol_packet_log WHERE id NOT IN (SELECT id FROM protocol_packet_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 500)
}

@Entity(tableName = "health_snapshot")
data class HealthSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val syncedAt: Long,
    val stepsToday: Long?,
    val dailyIsCumulative: Boolean = false,
    val dailyCalories: Long? = null,
    val dailyDistanceCentimeters: Long? = null,
    val dailyFlightsAscended: Long? = null,
    val dailyElevationGainCentimeters: Long? = null,
    val dailyUvExposure: Long? = null,
    val runStartedAt: Long?,
    val runEndedAt: Long?,
    val runDuration: Long?,
    val runDistance: Long?,
    val runCalories: Long?,
    val runAverageHeartRate: Long?,
    val runMaximumHeartRate: Long?,
    val workoutStartedAt: Long?,
    val workoutEndedAt: Long?,
    val workoutDuration: Long?,
    val workoutCalories: Long?,
    val workoutAverageHeartRate: Long?,
    val workoutMaximumHeartRate: Long?,
    val sleepStartedAt: Long?,
    val sleepEndedAt: Long?,
    val sleepDuration: Long?,
    val sleepTimeAsleep: Long?,
    val sleepTimesWokeUp: Long?,
    val sleepCalories: Long?,
    val sleepRestingHeartRate: Long?,
    val sleepTimeToFallAsleep: Long?,
)

@Dao
interface HealthSnapshotDao {
    @Query("SELECT * FROM health_snapshot WHERE id = 1")
    fun observe(): Flow<HealthSnapshotEntity?>

    @Upsert
    suspend fun upsert(snapshot: HealthSnapshotEntity)
}

@Entity(tableName = "health_daily")
data class HealthDailyEntity(
    @PrimaryKey val localDate: String,
    val syncedAt: Long,
    val steps: Long? = null,
    val calories: Long? = null,
    val distanceCentimeters: Long? = null,
    val flightsAscended: Long? = null,
    val elevationGainCentimeters: Long? = null,
    val uvExposure: Long? = null,
    val runStartedAt: Long? = null,
    val runEndedAt: Long? = null,
    val runDuration: Long? = null,
    val runDistance: Long? = null,
    val runCalories: Long? = null,
    val runAverageHeartRate: Long? = null,
    val runMaximumHeartRate: Long? = null,
    val workoutStartedAt: Long? = null,
    val workoutEndedAt: Long? = null,
    val workoutDuration: Long? = null,
    val workoutCalories: Long? = null,
    val workoutAverageHeartRate: Long? = null,
    val workoutMaximumHeartRate: Long? = null,
    val sleepStartedAt: Long? = null,
    val sleepEndedAt: Long? = null,
    val sleepDuration: Long? = null,
    val sleepTimeAsleep: Long? = null,
    val sleepTimesWokeUp: Long? = null,
    val sleepCalories: Long? = null,
    val sleepRestingHeartRate: Long? = null,
    val sleepTimeToFallAsleep: Long? = null,
)

@Dao
interface HealthDailyDao {
    @Query("SELECT * FROM health_daily ORDER BY localDate DESC LIMIT 400")
    fun observeAll(): Flow<List<HealthDailyEntity>>

    @Query("SELECT * FROM health_daily WHERE localDate = :localDate")
    suspend fun get(localDate: String): HealthDailyEntity?

    @Upsert
    suspend fun upsert(day: HealthDailyEntity)
}

private fun activitySummary(
    startedAt: Long?,
    endedAt: Long?,
    duration: Long?,
    distanceCentimeters: Long?,
    calories: Long?,
    averageHeartRate: Long?,
    maximumHeartRate: Long?,
): BandActivitySummary? = duration?.let {
    BandActivitySummary(
        startedAt = startedAt?.let(Instant::ofEpochMilli),
        endedAt = endedAt?.let(Instant::ofEpochMilli),
        durationMillis = it,
        distanceCentimeters = distanceCentimeters,
        calories = calories ?: 0,
        averageHeartRate = averageHeartRate ?: 0,
        maximumHeartRate = maximumHeartRate ?: 0,
    )
}?.takeIf { it.isValid() }

private fun sleepSummary(
    startedAt: Long?,
    endedAt: Long?,
    duration: Long?,
    timeAsleep: Long?,
    timesWokeUp: Long?,
    calories: Long?,
    restingHeartRate: Long?,
    timeToFallAsleep: Long?,
): BandSleepSummary? = duration?.let {
    BandSleepSummary(
        startedAt = startedAt?.let(Instant::ofEpochMilli),
        endedAt = endedAt?.let(Instant::ofEpochMilli),
        durationMillis = it,
        timeAsleepMillis = timeAsleep ?: 0,
        timesWokeUp = timesWokeUp ?: 0,
        calories = calories ?: 0,
        restingHeartRate = restingHeartRate ?: 0,
        timeToFallAsleepMillis = timeToFallAsleep ?: 0,
    )
}?.takeIf { it.isValid() }

fun HealthDailyEntity.runSummary(): BandActivitySummary? = activitySummary(
    runStartedAt, runEndedAt, runDuration, runDistance, runCalories, runAverageHeartRate, runMaximumHeartRate,
)

fun HealthDailyEntity.workoutSummary(): BandActivitySummary? = activitySummary(
    workoutStartedAt, workoutEndedAt, workoutDuration, null, workoutCalories, workoutAverageHeartRate, workoutMaximumHeartRate,
)

fun HealthDailyEntity.sleepSummary(): BandSleepSummary? = sleepSummary(
    sleepStartedAt, sleepEndedAt, sleepDuration, sleepTimeAsleep, sleepTimesWokeUp, sleepCalories,
    sleepRestingHeartRate, sleepTimeToFallAsleep,
)

fun BandHealthSnapshot.toEntity() = HealthSnapshotEntity(
    syncedAt = syncedAt.toEpochMilli(),
    stepsToday = stepsToday,
    dailyIsCumulative = daily?.cumulativeSinceReset == true,
    dailyCalories = daily?.calories,
    dailyDistanceCentimeters = daily?.distanceCentimeters,
    dailyFlightsAscended = daily?.flightsAscended,
    dailyElevationGainCentimeters = daily?.elevationGainCentimeters,
    dailyUvExposure = daily?.uvExposure,
    runStartedAt = lastRun?.startedAt?.toEpochMilli(),
    runEndedAt = lastRun?.endedAt?.toEpochMilli(),
    runDuration = lastRun?.durationMillis,
    runDistance = lastRun?.distanceCentimeters,
    runCalories = lastRun?.calories,
    runAverageHeartRate = lastRun?.averageHeartRate,
    runMaximumHeartRate = lastRun?.maximumHeartRate,
    workoutStartedAt = lastWorkout?.startedAt?.toEpochMilli(),
    workoutEndedAt = lastWorkout?.endedAt?.toEpochMilli(),
    workoutDuration = lastWorkout?.durationMillis,
    workoutCalories = lastWorkout?.calories,
    workoutAverageHeartRate = lastWorkout?.averageHeartRate,
    workoutMaximumHeartRate = lastWorkout?.maximumHeartRate,
    sleepStartedAt = lastSleep?.startedAt?.toEpochMilli(),
    sleepEndedAt = lastSleep?.endedAt?.toEpochMilli(),
    sleepDuration = lastSleep?.durationMillis,
    sleepTimeAsleep = lastSleep?.timeAsleepMillis,
    sleepTimesWokeUp = lastSleep?.timesWokeUp,
    sleepCalories = lastSleep?.calories,
    sleepRestingHeartRate = lastSleep?.restingHeartRate,
    sleepTimeToFallAsleep = lastSleep?.timeToFallAsleepMillis,
)

fun HealthSnapshotEntity.toModel() = BandHealthSnapshot(
    syncedAt = Instant.ofEpochMilli(syncedAt),
    stepsToday = stepsToday,
    daily = BandDailyMetrics(
        steps = stepsToday,
        calories = dailyCalories,
        distanceCentimeters = dailyDistanceCentimeters,
        flightsAscended = dailyFlightsAscended,
        elevationGainCentimeters = dailyElevationGainCentimeters,
        uvExposure = dailyUvExposure,
        cumulativeSinceReset = dailyIsCumulative,
    ).takeIf { it.hasData },
    lastRun = activitySummary(
        runStartedAt, runEndedAt, runDuration, runDistance, runCalories, runAverageHeartRate, runMaximumHeartRate,
    ),
    lastWorkout = activitySummary(
        workoutStartedAt, workoutEndedAt, workoutDuration, null, workoutCalories, workoutAverageHeartRate, workoutMaximumHeartRate,
    ),
    lastSleep = sleepSummary(
        sleepStartedAt, sleepEndedAt, sleepDuration, sleepTimeAsleep, sleepTimesWokeUp, sleepCalories,
        sleepRestingHeartRate, sleepTimeToFallAsleep,
    ),
)

private fun BandActivitySummary.isValid() = durationMillis >= 30_000 && startedAt != null && endedAt != null && !endedAt.isBefore(startedAt)
private fun BandSleepSummary.isValid() = durationMillis >= 60_000 && startedAt != null && endedAt != null && !endedAt.isBefore(startedAt)

@Database(entities = [ProtocolPacketLog::class, HealthSnapshotEntity::class, HealthDailyEntity::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packetLogDao(): PacketLogDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao
    abstract fun healthDailyDao(): HealthDailyDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "microband.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `health_snapshot` (`id` INTEGER NOT NULL, `syncedAt` INTEGER NOT NULL, `stepsToday` INTEGER, `runStartedAt` INTEGER, `runEndedAt` INTEGER, `runDuration` INTEGER, `runDistance` INTEGER, `runCalories` INTEGER, `runAverageHeartRate` INTEGER, `runMaximumHeartRate` INTEGER, `workoutStartedAt` INTEGER, `workoutEndedAt` INTEGER, `workoutDuration` INTEGER, `workoutCalories` INTEGER, `workoutAverageHeartRate` INTEGER, `workoutMaximumHeartRate` INTEGER, `sleepStartedAt` INTEGER, `sleepEndedAt` INTEGER, `sleepDuration` INTEGER, `sleepTimeAsleep` INTEGER, `sleepTimesWokeUp` INTEGER, `sleepCalories` INTEGER, `sleepRestingHeartRate` INTEGER, `sleepTimeToFallAsleep` INTEGER, PRIMARY KEY(`id`))""",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `health_daily` (`localDate` TEXT NOT NULL, `syncedAt` INTEGER NOT NULL, `steps` INTEGER, `calories` INTEGER, `distanceCentimeters` INTEGER, `flightsAscended` INTEGER, `elevationGainCentimeters` INTEGER, `uvExposure` INTEGER, PRIMARY KEY(`localDate`))""",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyIsCumulative` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyCalories` INTEGER")
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyDistanceCentimeters` INTEGER")
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyFlightsAscended` INTEGER")
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyElevationGainCentimeters` INTEGER")
                db.execSQL("ALTER TABLE `health_snapshot` ADD COLUMN `dailyUvExposure` INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "runStartedAt", "runEndedAt", "runDuration", "runDistance", "runCalories",
                    "runAverageHeartRate", "runMaximumHeartRate", "workoutStartedAt", "workoutEndedAt",
                    "workoutDuration", "workoutCalories", "workoutAverageHeartRate", "workoutMaximumHeartRate",
                    "sleepStartedAt", "sleepEndedAt", "sleepDuration", "sleepTimeAsleep", "sleepTimesWokeUp",
                    "sleepCalories", "sleepRestingHeartRate", "sleepTimeToFallAsleep",
                ).forEach { column ->
                    db.execSQL("ALTER TABLE `health_daily` ADD COLUMN `$column` INTEGER")
                }
            }
        }
    }
}
