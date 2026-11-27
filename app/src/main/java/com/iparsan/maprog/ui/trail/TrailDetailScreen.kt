package com.iparsan.maprog.ui.trail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iparsan.maprog.MaprogApp
import com.iparsan.maprog.data.ListEntryEntity
import com.iparsan.maprog.data.ListEntity
import com.iparsan.maprog.data.TrailFull
import com.iparsan.maprog.recording.FollowTarget
import com.iparsan.maprog.recording.Recorder
import com.iparsan.maprog.remote.Comment
import com.iparsan.maprog.remote.Social
import com.iparsan.maprog.ui.components.Avatar
import com.iparsan.maprog.ui.components.DifficultyChip
import com.iparsan.maprog.ui.components.ElevationProfile
import com.iparsan.maprog.ui.components.MapLayer
import com.iparsan.maprog.ui.components.OsmMap
import com.iparsan.maprog.ui.components.Stat
import com.iparsan.maprog.ui.components.TrailRankBadge
import com.iparsan.maprog.ui.components.startEndPins
import com.iparsan.maprog.ui.components.trackLine
import com.iparsan.maprog.ui.components.waypointPins
import com.iparsan.maprog.util.Activity
import com.iparsan.maprog.util.Fmt
import com.iparsan.maprog.util.WaypointTypes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailDetailScreen(
    localId: Long?,
    remoteId: String?,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenLocal: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val repo = MaprogApp.instance.repo
    val scope = rememberCoroutineScope()

    var trail by remember { mutableStateOf<TrailFull?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var clapped by remember { mutableStateOf(false) }
    var claps by remember { mutableStateOf(0) }
    var newComment by remember { mutableStateOf("") }
    var savedLocalId by remember { mutableStateOf<Long?>(localId) }
    var showLists by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var layer by remember { mutableStateOf(MapLayer.TOPO) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    suspend fun loadSocial(rid: String) {
        if (!Social.enabled) return
        try {
            comments = Social.comments(rid)
            clapped = Social.hasClapped(rid)
        } catch (_: Exception) { }
    }

    LaunchedEffect(localId, remoteId) {
        try {
            val t = when {
                localId != null -> repo.loadLocal(localId)
                remoteId != null -> repo.loadRemote(remoteId)
                else -> null
            }
            trail = t
            claps = t?.claps ?: 0
            if (t == null) error = "No se encontró la ruta"
            if (remoteId != null) savedLocalId = repo.trails.byRemoteId(remoteId)?.id
            t?.remoteId?.let { loadSocial(it) }
        } catch (e: Exception) { error = "No se pudo cargar la ruta: ${e.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.name ?: "Ruta", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    trail?.let { t ->
                        IconButton(onClick = {
                            scope.launch {
                                val uri = repo.exportGpx(t)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "${t.name} · ${Fmt.km(t.distanceM)} · ↗ ${t.elevGainM.toInt()} m — compartida con Maprog")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Compartir ruta (GPX)"))
                            }
                        }) { Icon(Icons.Filled.Share, "Compartir") }
                    }
                },
            )
        },
    ) { pad ->
        val t = trail
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                if (error != null) Text(error!!, Modifier.padding(24.dp)) else CircularProgressIndicator()
            }
            return@Scaffold
        }
        val mine = t.authorId != null && t.authorId == Social.currentUserId()
        val act = Activity.of(t.activity)

        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            // ---- Mapa ----
            item {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    OsmMap(
                        modifier = Modifier.fillMaxSize(),
                        layer = layer,
                        lines = listOf(trackLine(t.points)),
                        pins = startEndPins(t.points) + waypointPins(t.waypoints),
                        fitTo = t.points,
                    )
                    TextButton(
                        onClick = { layer = if (layer == MapLayer.TOPO) MapLayer.OSM else MapLayer.TOPO },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) { Text(if (layer == MapLayer.TOPO) "🗺️ Callejero" else "⛰️ Topo") }
                }
            }
            // ---- Cabecera ----
            item {
                Column(Modifier.padding(14.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${act.emoji} ${act.label}")
                        DifficultyChip(t.difficulty)
                        TrailRankBadge(t.trailRank)
                        if (t.circular) Text("🔁 Circular", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = t.authorId != null && !mine) { t.authorId?.let(onOpenUser) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(t.authorName ?: if (t.authorId == null) "Yo" else "?")
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(t.authorName ?: if (mine || t.authorId == null) "Tu ruta" else "Autor", fontWeight = FontWeight.Bold)
                            if (t.createdAt > 0) Text(Fmt.date(t.createdAt), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // ---- Acciones ----
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val id = savedLocalId ?: repo.saveRemote(t).also { savedLocalId = it }
                                    Recorder.setFollow(FollowTarget(id, t.name, t.points, t.waypoints))
                                    Recorder.setActivity(t.activity)
                                    onFollow()
                                } catch (e: Exception) { toast("Error: ${e.message}") }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && t.points.size >= 2,
                    ) {
                        Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Seguir ruta")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (t.remoteId != null && t.localId == null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (savedLocalId == null) { savedLocalId = repo.saveRemote(t); toast("Guardada en tu móvil: disponible sin conexión") }
                                        else toast("Ya la tienes en «Rutas guardadas»")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(if (savedLocalId != null) Icons.Filled.DownloadDone else Icons.Filled.Download, null)
                                Spacer(Modifier.width(4.dp)); Text(if (savedLocalId != null) "Guardada" else "Guardar")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    if (savedLocalId == null) savedLocalId = repo.saveRemote(t)
                                    showLists = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(4.dp)); Text("A una lista") }
                        if (t.remoteId != null) {
                            OutlinedButton(
                                onClick = {
                                    if (Social.currentUserId() == null) { toast("Inicia sesión en Perfil para aplaudir"); return@OutlinedButton }
                                    scope.launch {
                                        try {
                                            clapped = Social.toggleClap(t.remoteId)
                                            claps += if (clapped) 1 else -1
                                        } catch (e: Exception) { toast("Error: ${e.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text((if (clapped) "👏 " else "👏🏻 ") + claps) }
                        }
                    }
                    if (t.localId != null && (t.authorId == null || mine)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (t.remoteId == null && Social.enabled) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val rid = repo.upload(t.localId)
                                                trail = t.copy(remoteId = rid, isPublic = true)
                                                toast("¡Ruta publicada en la comunidad!")
                                            } catch (e: Exception) { toast(e.message ?: "Error al subir") }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy, modifier = Modifier.weight(1f),
                                ) { Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(4.dp)); Text("Publicar") }
                            }
                            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Eliminar")
                            }
                        }
                    }
                }
            }
            // ---- Estadísticas ----
            item {
                Column(Modifier.padding(14.dp)) {
                    Text("Estadísticas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Distancia", Fmt.km(t.distanceM), Modifier.weight(1f))
                        Stat("Desnivel +", Fmt.m(t.elevGainM), Modifier.weight(1f))
                        Stat("Desnivel −", Fmt.m(t.elevLossM), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Altitud máx.", Fmt.m(t.maxEleM), Modifier.weight(1f))
                        Stat("Altitud mín.", Fmt.m(t.minEleM), Modifier.weight(1f))
                        Stat("Tiempo", if (t.totalTimeS > 0) Fmt.hoursMinutes(t.totalTimeS) else "—", Modifier.weight(1f))
                    }
                    if (t.movingTimeS > 0) Row(Modifier.fillMaxWidth()) {
                        Stat("En movimiento", Fmt.hoursMinutes(t.movingTimeS), Modifier.weight(1f))
                        Stat("Vel. media", Fmt.speed(t.distanceM / t.movingTimeS * 3.6), Modifier.weight(1f))
                        Stat("Waypoints", t.waypoints.size.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    ElevationProfile(t.points)
                }
            }
            // ---- Descripción ----
            if (t.description.isNotBlank()) item {
                Column(Modifier.padding(14.dp)) {
                    Text("Descripción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(t.description)
                }
            }
            // ---- Fotos ----
            val photos = t.waypoints.mapNotNull { w -> w.photo?.let { it to w.name } }
            if (photos.isNotEmpty()) item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Fotos", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos) { (url, name) ->
                            AsyncImage(
                                model = url, contentDescription = name, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
            // ---- Waypoints ----
            if (t.waypoints.isNotEmpty()) {
                item { Text("Waypoints", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(t.waypoints) { w ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(WaypointTypes.emoji(w.type), style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(w.name, fontWeight = FontWeight.Bold)
                            Text("${WaypointTypes.label(w.type).substringAfter(' ')} · ${w.ele.toInt()} m", style = MaterialTheme.typography.labelSmall)
                            if (w.description.isNotBlank()) Text(w.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // ---- Comentarios ----
            if (t.remoteId != null && Social.enabled) {
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("Comentarios (${comments.size})", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(comments, key = { it.id ?: it.hashCode().toString() }) { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Avatar(c.username, 30)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(c.username ?: "Usuario", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable { onOpenUser(c.userId) })
                            Text(c.text)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newComment, onValueChange = { newComment = it.take(1000) },
                            placeholder = { Text(if (Social.currentUserId() != null) "Escribe un comentario" else "Inicia sesión para comentar") },
                            enabled = Social.currentUserId() != null, modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = newComment.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    try {
                                        Social.addComment(t.remoteId, newComment.trim())
                                        newComment = ""
                                        loadSocial(t.remoteId)
                                    } catch (e: Exception) { toast("Error: ${e.message}") }
                                }
                            },
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showLists) {
            savedLocalId?.let { lid -> ListPickerDialog(lid) { showLists = false } }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("¿Eliminar esta ruta?") },
                text = { Text(if (t.remoteId != null && mine) "Se borrará de tu móvil y también de la comunidad. No se puede deshacer." else "Se borrará de tu móvil. No se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                if (t.remoteId != null && mine) Social.deleteTrail(t.remoteId)
                                t.localId?.let { id -> repo.trails.trail(id)?.let { repo.trails.delete(it) } }
                                confirmDelete = false
                                onBack()
                            } catch (e: Exception) { toast("Error: ${e.message}") }
                        }
                    }) { Text("Eliminar") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            )
        }
    }
}

/** Diálogo para añadir la ruta a una de tus listas (o crear una nueva). */
@Composable
fun ListPickerDialog(trailId: Long, onDismiss: () -> Unit) {
    val repo = MaprogApp.instance.repo
    val scope = rememberCoroutineScope()
    val lists by repo.lists.lists().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar en una lista") },
        text = {
            Column {
                lists.forEach { l: ListEntity ->
                    Text(
                        "📁 ${l.name}",
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                repo.lists.add(ListEntryEntity(l.id, trailId))
                                Toast.makeText(ctx, "Añadida a «${l.name}»", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }.padding(vertical = 10.dp),
                    )
                }
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nueva lista") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                scope.launch {
                    val id = repo.lists.insert(ListEntity(name = newName.trim()))
                    repo.lists.add(ListEntryEntity(id, trailId))
                    Toast.makeText(ctx, "Añadida a «${newName.trim()}»", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Crear y añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
