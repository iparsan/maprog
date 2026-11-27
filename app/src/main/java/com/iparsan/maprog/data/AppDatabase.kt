package com.iparsan.maprog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrailDao {
    @Query("SELECT * FROM trails WHERE saved = 0 ORDER BY createdAt DESC")
    abstract fun myTrails(): Flow<List<TrailEntity>>

    @Query("SELECT * FROM trails WHERE saved = 1 ORDER BY createdAt DESC")
    abstract fun savedTrails(): Flow<List<TrailEntity>>

    @Query("SELECT * FROM trails ORDER BY createdAt DESC")
    abstract fun allTrails(): Flow<List<TrailEntity>>

    @Query("SELECT * FROM trails WHERE id = :id")
    abstract suspend fun trail(id: Long): TrailEntity?

    @Query("SELECT * FROM trails WHERE id = :id")
    abstract fun trailFlow(id: Long): Flow<TrailEntity?>

    @Query("SELECT * FROM trails WHERE remoteId = :remoteId LIMIT 1")
    abstract suspend fun byRemoteId(remoteId: String): TrailEntity?

    @Query("SELECT * FROM trails WHERE pendingUpload = 1")
    abstract suspend fun pending(): List<TrailEntity>

    @Insert
    abstract suspend fun insert(trail: TrailEntity): Long

    @Update
    abstract suspend fun update(trail: TrailEntity)

    @Delete
    abstract suspend fun delete(trail: TrailEntity)

    @Query("SELECT * FROM points WHERE trailId = :trailId ORDER BY seq")
    abstract suspend fun points(trailId: Long): List<PointEntity>

    @Insert
    abstract suspend fun insertPoints(points: List<PointEntity>)

    @Query("SELECT * FROM waypoints WHERE trailId = :trailId")
    abstract suspend fun waypoints(trailId: Long): List<WaypointEntity>

    @Insert
    abstract suspend fun insertWaypoints(wpts: List<WaypointEntity>)

    @Transaction
    open suspend fun insertFull(trail: TrailEntity, points: List<PointEntity>, wpts: List<WaypointEntity>): Long {
        val id = insert(trail)
        insertPoints(points.mapIndexed { i, p -> p.copy(id = 0, trailId = id, seq = i) })
        insertWaypoints(wpts.map { it.copy(id = 0, trailId = id) })
        return id
    }
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY createdAt DESC")
    fun lists(): Flow<List<ListEntity>>

    @Insert
    suspend fun insert(list: ListEntity): Long

    @Delete
    suspend fun delete(list: ListEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: ListEntryEntity)

    @Query("DELETE FROM list_entries WHERE listId = :listId AND trailId = :trailId")
    suspend fun remove(listId: Long, trailId: Long)

    @Query("SELECT t.* FROM trails t INNER JOIN list_entries e ON e.trailId = t.id WHERE e.listId = :listId ORDER BY t.createdAt DESC")
    fun trailsInList(listId: Long): Flow<List<TrailEntity>>

    @Query("SELECT COUNT(*) FROM list_entries WHERE listId = :listId")
    fun count(listId: Long): Flow<Int>
}

@Database(
    entities = [TrailEntity::class, PointEntity::class, WaypointEntity::class, ListEntity::class, ListEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trails(): TrailDao
    abstract fun lists(): ListDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "maprog.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
