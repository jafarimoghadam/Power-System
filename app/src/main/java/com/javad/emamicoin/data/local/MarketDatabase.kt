package com.javad.emamicoin.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val goldUsdPerOunce: Double,
    val usdIrr: Double,
    val coinPriceIrr: Double,
    val theoreticalValueIrr: Double,
    val premiumIrr: Double,
    val premiumPercent: Double,
    val impliedUsdIrr: Double,
    val goldSource: String,
    val usdSource: String,
    val coinSource: String
)

@Dao
interface SnapshotDao {
    @Insert suspend fun insert(snapshot: SnapshotEntity)
    @Query("SELECT * FROM snapshots ORDER BY capturedAt DESC LIMIT :limit")
    fun recent(limit: Int = 120): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM snapshots ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latest(): SnapshotEntity?
    @Query("DELETE FROM snapshots") suspend fun clear()
}

@Database(entities = [SnapshotEntity::class], version = 1, exportSchema = false)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun snapshots(): SnapshotDao

    companion object {
        @Volatile private var INSTANCE: MarketDatabase? = null

        fun get(context: Context): MarketDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                MarketDatabase::class.java,
                "emami_market.db"
            ).build().also { INSTANCE = it }
        }
    }
}
