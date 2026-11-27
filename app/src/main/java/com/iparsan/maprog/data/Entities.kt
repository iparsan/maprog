package com.iparsan.maprog.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una ruta guardada en el móvil: grabada por ti, importada de un GPX o descargada de la comunidad. */
@Entity(tableName = "trails")
data class TrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val activity: String = "hiking",
    /** 1 Fácil, 2 Moderado, 3 Difícil, 4 Muy difícil, 5 Solo expertos */
    val difficulty: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val distanceM: Double = 0.0,
    val elevGainM: Double = 0.0,
    val elevLossM: Double = 0.0,
    val maxEleM: Double = 0.0,
    val minEleM: Double = 0.0,
    val movingTimeS: Long = 0,
    val totalTimeS: Long = 0,
    val circular: Boolean = false,
    val isPublic: Boolean = false,
    /** true = ruta de otra persona guardada para seguirla ("Rutas guardadas") */
    val saved: Boolean = false,
    /** id en Supabase si está subida o descargada */
    val remoteId: String? = null,
    val authorName: String? = null,
    /** pendiente de subir (se grabó sin cobertura) */
    val pendingUpload: Boolean = false,
)

@Entity(
    tableName = "points",
    foreignKeys = [ForeignKey(entity = TrailEntity::class, parentColumns = ["id"], childColumns = ["trailId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trailId")]
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val ele: Double,
    val time: Long,
)

@Entity(
    tableName = "waypoints",
    foreignKeys = [ForeignKey(entity = TrailEntity::class, parentColumns = ["id"], childColumns = ["trailId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trailId")]
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val lat: Double,
    val lon: Double,
    val ele: Double,
    val name: String,
    val type: String = "generic",
    val description: String = "",
    /** ruta local (file://) o URL pública de la foto */
    val photo: String? = null,
)

/** Listas para organizar rutas, como en el Perfil de Wikiloc. */
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "list_entries",
    primaryKeys = ["listId", "trailId"],
    foreignKeys = [
        ForeignKey(entity = ListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrailEntity::class, parentColumns = ["id"], childColumns = ["trailId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trailId")]
)
data class ListEntryEntity(val listId: Long, val trailId: Long)
