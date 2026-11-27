package com.iparsan.maprog.ui.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.iparsan.maprog.MaprogApp
import com.iparsan.maprog.recording.RecState
import com.iparsan.maprog.recording.RecStatus
import com.iparsan.maprog.recording.Recorder
import com.iparsan.maprog.recording.RecordingService
import com.iparsan.maprog.remote.Social
import com.iparsan.maprog.ui.components.ElevationProfile
import com.iparsan.maprog.ui.components.MapLayer
import com.iparsan.maprog.ui.components.MapLine
import com.iparsan.maprog.ui.components.OsmMap
import com.iparsan.maprog.ui.components.Stat
import com.iparsan.maprog.ui.components.startEndPins
import com.iparsan.maprog.ui.components.trackLine
import com.iparsan.maprog.ui.components.waypointPins
import com.iparsan.maprog.ui.theme.MaprogGreen
import com.iparsan.maprog.ui.theme.RecRed
import com.iparsan.maprog.util.Activity
import com.iparsan.maprog.util.Difficulty
import com.iparsan.maprog.util.Fmt
import com.iparsan.maprog.util.WaypointTypes
import com.iparsan.maprog.util.Wpt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@Composable
fun RecordScreen(onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val rec by Recorder.state.collectAsState()
    val scope = rememberCoroutineScope()
    val repo = MaprogApp.instance.repo

    var hasPermission by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var preview by remember { mutableStateOf<Location?>(null) }
    var layer by remember { mutableStateOf(MapLayer.TOPO) }
    var showElevation by remember { mutableStateOf(false) }
    var showWaypoint by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var followMe by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = hasLocationPermission(ctx)
        if (!hasPermission) Toast.makeText(ctx, "Maprog necesita la ubicación para grabar rutas", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(permissionsToAsk())
    }
    // Reloj de la pantalla
    LaunchedEffect(rec.status) {
        while (rec.status == RecStatus.RECORDING) { now = System.currentTimeMillis(); delay(1000) }
        now = System.currentTimeMillis()
    }
    // Posición antes de empezar a grabar (cuando ya graba, la da el servicio)
    if (hasPermission && !rec.active) PreviewLocation { preview = it }

    val lat = if (rec.active) rec.lastLat else preview?.latitude ?: rec.lastLat
    val lon = if (rec.active) rec.lastLon else preview?.longitude ?: rec.lastLon
    val follow = rec.follow

    Box(Modifier.fillMaxSize()) {
        val lines = buildList {
            if (follow != null) add(trackLine(follow.points, com.iparsan.maprog.ui.theme.TrackOrange.toArgb(), 12f))
            if (rec.points.size >= 2) add(MapLine(rec.points, MaprogGreen.toArgb(), 9f))
        }
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            layer = layer,
            lines = lines,
            pins = (follow?.let { startEndPins(it.points) + waypointPins(it.waypoints) } ?: emptyList()) + waypointPins(rec.waypoints),
            myLat = lat, myLon = lon,
            follow = followMe && lat != null,
            fitTo = if (!rec.active) follow?.points else null,
            showCompass = true,
            initialCenter = if (lat != null && lon != null) GeoPoint(lat, lon) else GeoPoint(37.05, -3.35),
            initialZoom = if (lat != null) 15.0 else 11.0,
        )

        // ---- Barra superior de datos (como en la pantalla de grabación de Wikiloc) ----
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            if (rec.active) {
                Surface(tonalElevation = 4.dp, shadowElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Stat("Tiempo", Fmt.duration(rec.elapsedMs(now) / 1000), Modifier.weight(1f), big = true)
                        Stat("Distancia", Fmt.km(rec.stats.distanceM), Modifier.weight(1f), big = true)
                        Stat("Desnivel +", Fmt.m(rec.stats.elevGainM), Modifier.weight(1f), big = true)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Velocidad", Fmt.speed(rec.speedKmh), Modifier.weight(1f))
                        Stat("Altitud", Fmt.m(rec.lastEle), Modifier.weight(1f))
                        Stat("Precisión GPS", "±${rec.accuracyM.toInt()} m", Modifier.weight(1f))
                    }
                }
            }
            if (follow != null) FollowBanner(rec)
            if (rec.status == RecStatus.PAUSED) {
                Text("⏸ Grabación en pausa", Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(6.dp),
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ---- Controles del mapa ----
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallFloatingActionButton(onClick = { layer = if (layer == MapLayer.TOPO) MapLayer.OSM else MapLayer.TOPO }) {
                Icon(Icons.Filled.Layers, "Capas")
            }
            SmallFloatingActionButton(onClick = { followMe = !followMe }) {
                Text(if (followMe) "🎯" else "📍")
            }
        }

        // ---- Panel inferior ----
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(showElevation && rec.active) {
                Surface(tonalElevation = 3.dp) {
                    ElevationProfile(
                        follow?.points ?: rec.points,
                        Modifier.padding(10.dp),
                        progressIndex = if (follow != null) rec.followIndex else null,
                    )
                }
            }
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                if (!rec.active) {
                    IdleControls(
                        hasPermission = hasPermission,
                        followName = follow?.name,
                        onRemoveFollow = { Recorder.setFollow(null) },
                        onAskPermission = { permLauncher.launch(permissionsToAsk()) },
                        onStart = {
                            Recorder.start()
                            RecordingService.start(ctx)
                        },
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Botón rojo de pausa abajo a la izquierda
                        FilledIconButton(
                            onClick = { if (rec.status == RecStatus.RECORDING) Recorder.pause() else Recorder.resume() },
                            modifier = Modifier.size(60.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (rec.status == RecStatus.RECORDING) RecRed else MaprogGreen),
                        ) {
                            Icon(if (rec.status == RecStatus.RECORDING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (rec.status == RecStatus.RECORDING) "Pausar" else "Reanudar", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { showWaypoint = true }) { Icon(Icons.Filled.AddLocationAlt, "Waypoint", modifier = Modifier.size(30.dp)) }
                            Text("Waypoint", fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { showElevation = !showElevation }) { Icon(Icons.Filled.ShowChart, "Elevación", modifier = Modifier.size(30.dp)) }
                            Text("Elevación", fontSize = 11.sp)
                        }
                        Button(onClick = { Recorder.pause(); showSave = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(4.dp)); Text("Finalizar")
                        }
                    }
                }
            }
        }
    }

    if (showWaypoint) {
        WaypointDialog(
            onDismiss = { showWaypoint = false },
            onSave = { w ->
                val la = rec.lastLat; val lo = rec.lastLon
                if (la == null || lo == null) {
                    Toast.makeText(ctx, "Esperando señal GPS…", Toast.LENGTH_SHORT).show()
                } else {
                    Recorder.addWaypoint(w.copy(lat = la, lon = lo, ele = rec.lastEle))
                    Toast.makeText(ctx, "Waypoint añadido", Toast.LENGTH_SHORT).show()
                }
                showWaypoint = false
            },
        )
    }

    if (showSave) {
        SaveDialog(
            rec = rec,
            onResume = { showSave = false; Recorder.resume() },
            onDiscard = {
                showSave = false
                RecordingService.stop(ctx)
                Recorder.reset()
                Recorder.setFollow(null)
            },
            onSave = { name, desc, difficulty, isPublic ->
                showSave = false
                val points = rec.points
                val wpts = rec.waypoints
                val activity = rec.activity
                RecordingService.stop(ctx)
                scope.launch {
                    if (points.size < 2) {
                        Toast.makeText(ctx, "La ruta es demasiado corta para guardarla", Toast.LENGTH_LONG).show()
                        Recorder.reset(); return@launch
                    }
                    val id = repo.saveRecorded(name, desc, activity, difficulty, isPublic, points, wpts)
                    Recorder.reset(); Recorder.setFollow(null)
                    if (isPublic && Social.currentUserId() != null) {
                        try { repo.upload(id); Toast.makeText(ctx, "Ruta guardada y publicada", Toast.LENGTH_SHORT).show() }
                        catch (_: Exception) { Toast.makeText(ctx, "Guardada. Se subirá cuando haya cobertura.", Toast.LENGTH_LONG).show() }
                    } else {
                        Toast.makeText(ctx, "Ruta guardada en tu móvil", Toast.LENGTH_SHORT).show()
                    }
                    onSaved(id)
                }
            },
        )
    }
}

@Composable
private fun IdleControls(
    hasPermission: Boolean,
    followName: String?,
    onRemoveFollow: () -> Unit,
    onAskPermission: () -> Unit,
    onStart: () -> Unit,
) {
    val rec by Recorder.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val act = Activity.of(rec.activity)
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        if (followName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧭 Ruta a seguir: ", fontWeight = FontWeight.Bold)
                Text(followName, Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = onRemoveFollow) { Icon(Icons.Filled.Close, "Quitar ruta") }
            }
        }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${act.emoji}  ${act.label}  ▾")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Activity.entries.forEach { a ->
                    DropdownMenuItem(text = { Text("${a.emoji}  ${a.label}") }, onClick = { Recorder.setActivity(a.key); menu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!hasPermission) {
            Button(onClick = onAskPermission, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Permitir ubicación") }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecRed),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (followName != null) "Empezar a seguir" else "Empieza a grabar", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun FollowBanner(rec: RecState) {
    val f = rec.follow ?: return
    val doneM = f.cumulative.getOrElse(rec.followIndex) { 0.0 }
    val leftM = (f.cumulative.lastOrNull() ?: 0.0) - doneM
    val off = rec.isOffRoute
    Column(
        Modifier.fillMaxWidth().background(if (off) RecRed else Color(0xE6FF6D00)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (off) {
            Text("⚠️ Te has salido de la ruta (${rec.offRouteM.toInt()} m)", color = Color.White, fontWeight = FontWeight.Bold)
        } else {
            Text("🧭 ${f.name}", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recorrido ${Fmt.km(doneM)} · Faltan ${Fmt.km(leftM.coerceAtLeast(0.0))}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (rec.followIndex == 0 && rec.lastLat != null && rec.offRouteM > 200) {
                TextButton(onClick = { Recorder.reverseFollow() }) { Text("Invertir sentido", color = Color.White) }
            }
        }
        rec.nearWaypoint?.let { Text("📍 Cerca de: $it", color = Color.White, fontSize = 13.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaypointDialog(onDismiss: () -> Unit, onSave: (Wpt) -> Unit) {
    val repo = MaprogApp.instance.repo
    var type by remember { mutableStateOf("generic") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<java.io.File?>(null) }
    var pending by remember { mutableStateOf<Pair<java.io.File, Uri>?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pending
        if (ok && p != null) { photoFile = p.first; photoUri = p.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo waypoint") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WaypointTypes.all.forEach { (k, label) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (opcional)") })
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    val p = repo.newPhotoFile()
                    pending = p
                    camera.launch(p.second)
                }) { Text(if (photoUri != null) "📷 Foto añadida (repetir)" else "📷 Hacer foto") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Wpt(0.0, 0.0, 0.0, name.ifBlank { WaypointTypes.label(type).substringAfter(' ') }, type, desc,
                    photoFile?.let { Uri.fromFile(it).toString() }))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SaveDialog(
    rec: RecState,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String, String, Int, Boolean) -> Unit,
) {
    val act = Activity.of(rec.activity)
    var name by remember { mutableStateOf(rec.follow?.name ?: "${act.label} ${Fmt.date(System.currentTimeMillis())}") }
    var desc by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf(2) }
    var isPublic by remember { mutableStateOf(Social.enabled && Social.currentUserId() != null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onResume,
        title = { Text("Guardar ruta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${Fmt.km(rec.stats.distanceM)} · ↗ ${rec.stats.elevGainM.toInt()} m · ${Fmt.duration(rec.elapsedMs() / 1000)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nombre de la ruta") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción") }, minLines = 3)
                Text("Dificultad técnica", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                Difficulty.labels.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth().clickable { difficulty = i + 1 }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = difficulty == i + 1, onClick = { difficulty = i + 1 })
                        Text(l)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pública", fontWeight = FontWeight.Bold)
                        Text(
                            if (!Social.enabled) "La comunidad no está configurada"
                            else if (Social.currentUserId() == null) "Inicia sesión en Perfil para publicar"
                            else "Otros podrán verla y seguirla",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = Social.enabled && Social.currentUserId() != null)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.ifBlank { "Mi ruta" }, desc, difficulty, isPublic) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDiscard = true }) { Text("Descartar", color = RecRed) }
                TextButton(onClick = onResume) { Text("Seguir grabando") }
            }
        },
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar la grabación?") },
            text = { Text("Se perderá el track que has grabado. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Descartar", color = RecRed) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }
}

/** Escucha el GPS solo mientras se ve la pantalla, para mostrar tu posición antes de grabar. */
@SuppressLint("MissingPermission")
@Composable
private fun PreviewLocation(onLocation: (Location) -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { onLocation(it) }
        try {
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let(onLocation)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, listener, Looper.getMainLooper())
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 20f, listener, Looper.getMainLooper())
        } catch (_: SecurityException) { }
        onDispose { lm.removeUpdates(listener) }
    }
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun permissionsToAsk(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
