package com.iparsan.maprog.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.iparsan.maprog.remote.NewTrail
import com.iparsan.maprog.remote.RemoteTrail
import com.iparsan.maprog.remote.Social
import com.iparsan.maprog.util.Gpx
import com.iparsan.maprog.util.TrackMath
import com.iparsan.maprog.util.TrackPt
import com.iparsan.maprog.util.Wpt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Una ruta lista para pintar en la ficha, venga del móvil o de la comunidad. */
data class TrailFull(
    val localId: Long?,
    val remoteId: String?,
    val name: String,
    val description: String,
    val activity: String,
    val difficulty: Int,
    val authorId: String?,
    val authorName: String?,
    val createdAt: Long,
    val distanceM: Double,
    val elevGainM: Double,
    val elevLossM: Double,
    val maxEleM: Double,
    val minEleM: Double,
    val movingTimeS: Long,
    val totalTimeS: Long,
    val circular: Boolean,
    val isPublic: Boolean,
    val points: List<TrackPt>,
    val waypoints: List<Wpt>,
    val claps: Int = 0,
    val comments: Int = 0,
    val trailRank: Int = 0,
    val coverUrl: String? = null,
)

fun PointEntity.toPt() = TrackPt(lat, lon, ele, time)
fun WaypointEntity.toWpt() = Wpt(lat, lon, ele, name, type, description, photo)

class Repo(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    val trails = db.trails()
    val lists = db.lists()

    suspend fun loadLocal(id: Long): TrailFull? = withContext(Dispatchers.IO) {
        val t = trails.trail(id) ?: return@withContext null
        val pts = trails.points(id).map { it.toPt() }
        val wps = trails.waypoints(id).map { it.toWpt() }
        TrailFull(
            t.id, t.remoteId, t.name, t.description, t.activity, t.difficulty,
            if (t.saved) null else Social.currentUserId(), t.authorName, t.createdAt,
            t.distanceM, t.elevGainM, t.elevLossM, t.maxEleM, t.minEleM, t.movingTimeS, t.totalTimeS,
            t.circular, t.isPublic, pts, wps,
            trailRank = TrackMath.trailRank(t.description.length, wps.count { it.photo != null }, wps.size, 0, 0),
        )
    }

    suspend fun loadRemote(id: String): TrailFull? = withContext(Dispatchers.IO) {
        val t = Social.trail(id) ?: return@withContext null
        val g = Social.geometry(id)
        t.toFull(g?.points ?: emptyList(), g?.waypoints ?: emptyList())
    }

    private fun RemoteTrail.toFull(points: List<TrackPt>, wps: List<Wpt>) = TrailFull(
        null, id, name, description, activity, difficulty, userId, username,
        createdAt?.let { parseIso(it) } ?: 0L,
        distanceM, elevGainM, elevLossM, maxEleM, minEleM, movingTimeS, totalTimeS, circular, true,
        points, wps, clapsCount, commentsCount, trailRank, coverUrl,
    )

    private fun parseIso(s: String): Long = try {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Exception) { 0L }

    /** Guarda en el móvil una ruta recién grabada. */
    suspend fun saveRecorded(
        name: String, description: String, activity: String, difficulty: Int, isPublic: Boolean,
        points: List<TrackPt>, waypoints: List<Wpt>,
    ): Long = withContext(Dispatchers.IO) {
        val s = TrackMath.stats(points)
        val entity = TrailEntity(
            name = name, description = description, activity = activity, difficulty = difficulty,
            distanceM = s.distanceM, elevGainM = s.elevGainM, elevLossM = s.elevLossM,
            maxEleM = s.maxEleM, minEleM = s.minEleM, movingTimeS = s.movingTimeS, totalTimeS = s.totalTimeS,
            circular = s.circular, isPublic = isPublic, pendingUpload = isPublic,
        )
        trails.insertFull(entity, points.map { PointEntity(trailId = 0, seq = 0, lat = it.lat, lon = it.lon, ele = it.ele, time = it.time) },
            waypoints.map { WaypointEntity(trailId = 0, lat = it.lat, lon = it.lon, ele = it.ele, name = it.name, type = it.type, description = it.description, photo = it.photo) })
    }

    /** "Guardar" una ruta de la comunidad para tenerla sin conexión y poder seguirla. */
    suspend fun saveRemote(full: TrailFull): Long = withContext(Dispatchers.IO) {
        full.remoteId?.let { rid -> trails.byRemoteId(rid)?.let { return@withContext it.id } }
        val entity = TrailEntity(
            name = full.name, description = full.description, activity = full.activity, difficulty = full.difficulty,
            createdAt = System.currentTimeMillis(),
            distanceM = full.distanceM, elevGainM = full.elevGainM, elevLossM = full.elevLossM,
            maxEleM = full.maxEleM, minEleM = full.minEleM, movingTimeS = full.movingTimeS, totalTimeS = full.totalTimeS,
            circular = full.circular, saved = full.authorId != Social.currentUserId(),
            remoteId = full.remoteId, authorName = full.authorName,
        )
        trails.insertFull(entity, full.points.map { PointEntity(trailId = 0, seq = 0, lat = it.lat, lon = it.lon, ele = it.ele, time = it.time) },
            full.waypoints.map { WaypointEntity(trailId = 0, lat = it.lat, lon = it.lon, ele = it.ele, name = it.name, type = it.type, description = it.description, photo = it.photo) })
    }

    /** Importa un GPX desde el selector de archivos o desde otra app. */
    suspend fun importGpx(uri: Uri): Long = withContext(Dispatchers.IO) {
        val parsed = ctx.contentResolver.openInputStream(uri)?.use { Gpx.parse(it) } ?: error("No se pudo abrir el archivo")
        if (parsed.points.size < 2) error("El GPX no tiene ningún track")
        val s = TrackMath.stats(parsed.points)
        val name = parsed.name ?: uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".gpx") ?: "Ruta importada"
        trails.insertFull(
            TrailEntity(name = name, description = parsed.description ?: "", distanceM = s.distanceM,
                elevGainM = s.elevGainM, elevLossM = s.elevLossM, maxEleM = s.maxEleM, minEleM = s.minEleM,
                movingTimeS = s.movingTimeS, totalTimeS = s.totalTimeS, circular = s.circular),
            parsed.points.map { PointEntity(trailId = 0, seq = 0, lat = it.lat, lon = it.lon, ele = it.ele, time = it.time) },
            parsed.waypoints.map { WaypointEntity(trailId = 0, lat = it.lat, lon = it.lon, ele = it.ele, name = it.name, type = it.type, description = it.description) },
        )
    }

    /** Escribe el GPX en caché y devuelve una Uri para compartirlo o abrirlo en otra app. */
    suspend fun exportGpx(full: TrailFull): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safe = full.name.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().ifBlank { "ruta" }
        val f = File(dir, "$safe.gpx")
        f.writeText(Gpx.write(full.name, full.description, full.activity, full.points, full.waypoints))
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    /** Sube una ruta del móvil a la comunidad, con sus fotos. */
    suspend fun upload(localId: Long): String = withContext(Dispatchers.IO) {
        val uid = Social.currentUserId() ?: error("Inicia sesión en Perfil para publicar rutas")
        val t = trails.trail(localId) ?: error("Ruta no encontrada")
        val pts = trails.points(localId).map { it.toPt() }
        val wpsLocal = trails.waypoints(localId)
        // Sube las fotos de los waypoints y usa la primera como portada
        val wps = wpsLocal.map { w ->
            val photo = w.photo
            if (photo != null && photo.startsWith("file:")) {
                val file = File(Uri.parse(photo).path ?: "")
                val url = if (file.exists()) Social.uploadPhoto(compress(file), file.name) else null
                w.toWpt().copy(photo = url)
            } else w.toWpt()
        }
        val simplified = TrackMath.simplify(pts, 4.0)
        val id = Social.uploadTrail(
            NewTrail(
                userId = uid, name = t.name, description = t.description, activity = t.activity, difficulty = t.difficulty,
                distanceM = t.distanceM, elevGainM = t.elevGainM, elevLossM = t.elevLossM, maxEleM = t.maxEleM, minEleM = t.minEleM,
                movingTimeS = t.movingTimeS, totalTimeS = t.totalTimeS, circular = t.circular,
                startLat = pts.firstOrNull()?.lat ?: 0.0, startLon = pts.firstOrNull()?.lon ?: 0.0,
                isPublic = true, coverUrl = wps.firstOrNull { it.photo != null }?.photo,
                points = simplified, waypoints = wps,
            )
        )
        trails.update(t.copy(remoteId = id, isPublic = true, pendingUpload = false))
        id
    }

    /** Sube las rutas que se grabaron sin cobertura. */
    suspend fun uploadPending(): Int {
        if (Social.currentUserId() == null) return 0
        var n = 0
        for (t in trails.pending()) {
            try { upload(t.id); n++ } catch (_: Exception) { }
        }
        return n
    }

    /** Reduce la foto a ~1600 px para no gastar datos ni espacio. */
    private fun compress(file: File): ByteArray {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, opts)
        var sample = 1
        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return file.readBytes()
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }

    fun newPhotoFile(): Pair<File, Uri> {
        val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
        val f = File(dir, "wpt_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }
}
