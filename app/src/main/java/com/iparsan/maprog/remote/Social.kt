package com.iparsan.maprog.remote

import com.iparsan.maprog.BuildConfig
import com.iparsan.maprog.util.TrackPt
import com.iparsan.maprog.util.Wpt
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------- Tablas de Supabase (ver supabase/schema.sql) ----------

@Serializable
data class Profile(
    val id: String,
    val username: String,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Fila de la vista `trail_feed`: la ruta con el nombre del autor y los contadores sociales. */
@Serializable
data class RemoteTrail(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    val name: String,
    val description: String = "",
    val activity: String = "hiking",
    val difficulty: Int = 2,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("elev_gain_m") val elevGainM: Double = 0.0,
    @SerialName("elev_loss_m") val elevLossM: Double = 0.0,
    @SerialName("max_ele_m") val maxEleM: Double = 0.0,
    @SerialName("min_ele_m") val minEleM: Double = 0.0,
    @SerialName("moving_time_s") val movingTimeS: Long = 0,
    @SerialName("total_time_s") val totalTimeS: Long = 0,
    val circular: Boolean = false,
    @SerialName("start_lat") val startLat: Double = 0.0,
    @SerialName("start_lon") val startLon: Double = 0.0,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("waypoint_count") val waypointCount: Int = 0,
    @SerialName("claps_count") val clapsCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("trail_rank") val trailRank: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Datos pesados de la ruta (el track completo y los waypoints), en la tabla `trails`. */
@Serializable
data class RemoteTrailGeometry(
    val id: String,
    val points: List<TrackPt> = emptyList(),
    val waypoints: List<Wpt> = emptyList(),
)

@Serializable
data class NewTrail(
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String,
    val activity: String,
    val difficulty: Int,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("elev_gain_m") val elevGainM: Double,
    @SerialName("elev_loss_m") val elevLossM: Double,
    @SerialName("max_ele_m") val maxEleM: Double,
    @SerialName("min_ele_m") val minEleM: Double,
    @SerialName("moving_time_s") val movingTimeS: Long,
    @SerialName("total_time_s") val totalTimeS: Long,
    val circular: Boolean,
    @SerialName("start_lat") val startLat: Double,
    @SerialName("start_lon") val startLon: Double,
    @SerialName("is_public") val isPublic: Boolean,
    @SerialName("cover_url") val coverUrl: String?,
    val points: List<TrackPt>,
    val waypoints: List<Wpt>,
)

@Serializable
data class IdOnly(val id: String)

@Serializable
data class Comment(
    val id: String? = null,
    @SerialName("trail_id") val trailId: String,
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    val text: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NewComment(@SerialName("trail_id") val trailId: String, @SerialName("user_id") val userId: String, val text: String)

@Serializable
data class Clap(@SerialName("trail_id") val trailId: String, @SerialName("user_id") val userId: String)

@Serializable
data class Follow(@SerialName("follower_id") val followerId: String, @SerialName("followee_id") val followeeId: String)

@Serializable
data class ProfileUpdate(val bio: String)

/** Filtros del buscador de Explora. */
data class SearchQuery(
    val text: String = "",
    val activity: String? = null,
    val maxDistanceKm: Int? = null,
    val maxDifficulty: Int? = null,
    val onlyCircular: Boolean = false,
    val sort: Sort = Sort.POPULAR,
    /** límites del mapa: sur, oeste, norte, este (opción "Buscar al mover el mapa") */
    val bbox: DoubleArray? = null,
    val authorId: String? = null,
    val followedOnly: Boolean = false,
)

enum class Sort(val label: String) { POPULAR("Más populares"), TRAILRANK("TrailRank"), RECENT("Más recientes") }

/**
 * Toda la parte social de Maprog contra Supabase (Auth + Postgres + Storage).
 * Si la app se compila sin claves, [enabled] es false y la app funciona solo en local.
 */
object Social {
    val enabled: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!enabled) null else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    private fun c(): SupabaseClient = client ?: error("La parte social no está configurada")

    // ---------- Cuenta ----------

    fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id
    fun currentEmail(): String? = client?.auth?.currentUserOrNull()?.email

    suspend fun signUp(email: String, password: String, username: String) {
        c().auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("username", username) }
        }
    }

    suspend fun signIn(email: String, password: String) {
        c().auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() { client?.auth?.signOut() }

    suspend fun profile(userId: String): Profile? =
        c().from("profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull<Profile>()

    suspend fun updateBio(bio: String) {
        val uid = currentUserId() ?: return
        c().from("profiles").update(ProfileUpdate(bio)) { filter { eq("id", uid) } }
    }

    // ---------- Explora ----------

    suspend fun search(q: SearchQuery, limit: Long = 60): List<RemoteTrail> {
        val followed = if (q.followedOnly) following(currentUserId() ?: return emptyList()) else null
        if (followed != null && followed.isEmpty()) return emptyList()
        return c().from("trail_feed").select {
            filter {
                if (q.text.isNotBlank()) ilike("name", "%${q.text.trim()}%")
                q.activity?.let { eq("activity", it) }
                q.maxDistanceKm?.let { lte("distance_m", it * 1000.0) }
                q.maxDifficulty?.let { lte("difficulty", it) }
                if (q.onlyCircular) eq("circular", true)
                q.authorId?.let { eq("user_id", it) }
                followed?.let { isIn("user_id", it) }
                q.bbox?.let { b ->
                    gte("start_lat", b[0]); lte("start_lat", b[2])
                    gte("start_lon", b[1]); lte("start_lon", b[3])
                }
            }
            when (q.sort) {
                Sort.POPULAR -> order("claps_count", Order.DESCENDING)
                Sort.TRAILRANK -> order("trail_rank", Order.DESCENDING)
                Sort.RECENT -> order("created_at", Order.DESCENDING)
            }
            limit(limit)
        }.decodeList<RemoteTrail>()
    }

    suspend fun trail(id: String): RemoteTrail? =
        c().from("trail_feed").select { filter { eq("id", id) } }.decodeSingleOrNull<RemoteTrail>()

    suspend fun geometry(id: String): RemoteTrailGeometry? =
        c().from("trails").select(io.github.jan.supabase.postgrest.query.Columns.list("id", "points", "waypoints")) {
            filter { eq("id", id) }
        }.decodeSingleOrNull<RemoteTrailGeometry>()

    // ---------- Subir rutas y fotos ----------

    suspend fun uploadPhoto(bytes: ByteArray, name: String): String {
        val uid = currentUserId() ?: error("Inicia sesión")
        val path = "$uid/${System.currentTimeMillis()}_$name"
        val bucket = c().storage.from("photos")
        bucket.upload(path, bytes) { upsert = true }
        return bucket.publicUrl(path)
    }

    suspend fun uploadTrail(t: NewTrail): String =
        c().from("trails").insert(t) { select(io.github.jan.supabase.postgrest.query.Columns.list("id")) }.decodeSingle<IdOnly>().id

    suspend fun deleteTrail(id: String) { c().from("trails").delete { filter { eq("id", id) } } }

    // ---------- Comunidad ----------

    suspend fun comments(trailId: String): List<Comment> =
        c().from("comment_feed").select {
            filter { eq("trail_id", trailId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<Comment>()

    suspend fun addComment(trailId: String, text: String) {
        val uid = currentUserId() ?: error("Inicia sesión para comentar")
        c().from("comments").insert(NewComment(trailId, uid, text))
    }

    suspend fun hasClapped(trailId: String): Boolean {
        val uid = currentUserId() ?: return false
        return c().from("claps").select { filter { eq("trail_id", trailId); eq("user_id", uid) } }.decodeList<Clap>().isNotEmpty()
    }

    suspend fun toggleClap(trailId: String): Boolean {
        val uid = currentUserId() ?: error("Inicia sesión para aplaudir")
        return if (hasClapped(trailId)) {
            c().from("claps").delete { filter { eq("trail_id", trailId); eq("user_id", uid) } }
            false
        } else {
            c().from("claps").insert(Clap(trailId, uid))
            true
        }
    }

    suspend fun following(userId: String): List<String> =
        c().from("follows").select { filter { eq("follower_id", userId) } }.decodeList<Follow>().map { it.followeeId }

    suspend fun followers(userId: String): List<String> =
        c().from("follows").select { filter { eq("followee_id", userId) } }.decodeList<Follow>().map { it.followerId }

    suspend fun isFollowing(userId: String): Boolean {
        val me = currentUserId() ?: return false
        return following(me).contains(userId)
    }

    suspend fun toggleFollow(userId: String): Boolean {
        val me = currentUserId() ?: error("Inicia sesión para seguir a otros")
        return if (isFollowing(userId)) {
            c().from("follows").delete { filter { eq("follower_id", me); eq("followee_id", userId) } }
            false
        } else {
            c().from("follows").insert(Follow(me, userId))
            true
        }
    }

    suspend fun profiles(ids: List<String>): List<Profile> =
        if (ids.isEmpty()) emptyList() else c().from("profiles").select { filter { isIn("id", ids) } }.decodeList<Profile>()

    suspend fun searchPeople(text: String): List<Profile> =
        c().from("profiles").select {
            filter { ilike("username", "%${text.trim()}%") }
            limit(30)
        }.decodeList<Profile>()
}
