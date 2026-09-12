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

@Database(entities = [ProtocolPacketLog::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packetLogDao(): PacketLogDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "microband.db",
        ).build()
    }
}
