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
    val steps: Long?,
    val calories: Long?,
    val distanceCentimeters: Long?,
    val flightsAscended: Long?,
    val elevationGainCentimeters: Long?,
    val uvExposure: Long?,
)

@Dao
interface HealthDailyDao {
    @Query("SELECT * FROM health_daily ORDER BY localDate DESC LIMIT 400")
    fun observeAll(): Flow<List<HealthDailyEntity>>

    @Upsert
    suspend fun upsert(day: HealthDailyEntity)
}

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

fun HealthSnapshotEntity.toModel(): BandHealthSnapshot {
    val run = runDuration?.let {
        BandActivitySummary(
            startedAt = runStartedAt?.let(Instant::ofEpochMilli),
            endedAt = runEndedAt?.let(Instant::ofEpochMilli),
            durationMillis = it,
            distanceCentimeters = runDistance,
            calories = runCalories ?: 0,
            averageHeartRate = runAverageHeartRate ?: 0,
            maximumHeartRate = runMaximumHeartRate ?: 0,
        )
    }?.takeIf { it.isValid() }
    val workout = workoutDuration?.let {
        BandActivitySummary(
            startedAt = workoutStartedAt?.let(Instant::ofEpochMilli),
            endedAt = workoutEndedAt?.let(Instant::ofEpochMilli),
            durationMillis = it,
            calories = workoutCalories ?: 0,
            averageHeartRate = workoutAverageHeartRate ?: 0,
            maximumHeartRate = workoutMaximumHeartRate ?: 0,
        )
    }?.takeIf { it.isValid() }
    val sleep = sleepDuration?.let {
        BandSleepSummary(
            startedAt = sleepStartedAt?.let(Instant::ofEpochMilli),
            endedAt = sleepEndedAt?.let(Instant::ofEpochMilli),
            durationMillis = it,
            timeAsleepMillis = sleepTimeAsleep ?: 0,
            timesWokeUp = sleepTimesWokeUp ?: 0,
            calories = sleepCalories ?: 0,
            restingHeartRate = sleepRestingHeartRate ?: 0,
            timeToFallAsleepMillis = sleepTimeToFallAsleep ?: 0,
        )
    }?.takeIf { it.isValid() }
    return BandHealthSnapshot(
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
    lastRun = run,
    lastWorkout = workout,
    lastSleep = sleep,
    )
}

private fun BandActivitySummary.isValid() = durationMillis >= 30_000 && startedAt != null && endedAt != null && !endedAt.isBefore(startedAt)
private fun BandSleepSummary.isValid() = durationMillis >= 60_000 && startedAt != null && endedAt != null && !endedAt.isBefore(startedAt)

@Database(entities = [ProtocolPacketLog::class, HealthSnapshotEntity::class, HealthDailyEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packetLogDao(): PacketLogDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao
    abstract fun healthDailyDao(): HealthDailyDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "microband.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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
    }
}
