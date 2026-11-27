package com.iparsan.maprog.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.iparsan.maprog.MaprogApp
import com.iparsan.maprog.remote.Profile
import com.iparsan.maprog.remote.RemoteTrail
import com.iparsan.maprog.remote.SearchQuery
import com.iparsan.maprog.remote.Social
import com.iparsan.maprog.remote.Sort
import com.iparsan.maprog.ui.components.Avatar
import com.iparsan.maprog.ui.components.MapLayer
import com.iparsan.maprog.ui.components.MapPin
import com.iparsan.maprog.ui.components.OsmMap
import com.iparsan.maprog.ui.components.TrailCard
import com.iparsan.maprog.util.Activity
import com.iparsan.maprog.util.Difficulty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(onOpenRemote: (String) -> Unit, onOpenLocal: (Long) -> Unit, onOpenUser: (String) -> Unit) {
    val repo = MaprogApp.instance.repo
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf(SearchQuery()) }
    var mapMode by rememberSaveable { mutableStateOf(false) }
    var searchOnMove by rememberSaveable { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<RemoteTrail>>(emptyList()) }
    var people by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RemoteTrail?>(null) }
    var searchTab by rememberSaveable { mutableIntStateOf(0) } // 0 rutas, 1 personas
    var moveJob by remember { mutableStateOf<Job?>(null) }
    val localTrails by repo.trails.allTrails().collectAsState(initial = emptyList())

    fun doSearch(q: SearchQuery = query) {
        if (!Social.enabled) return
        scope.launch {
            loading = true; error = null
            try {
                results = Social.search(q)
                people = if (q.text.isNotBlank()) Social.searchPeople(q.text) else emptyList()
            } catch (e: Exception) { error = "No se pudo conectar: ${e.message}" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (results.isEmpty()) doSearch() }

    Column(Modifier.fillMaxSize()) {
        // ---- Buscador ----
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            placeholder = { Text("Busca un lugar o una ruta") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) IconButton(onClick = { text = ""; query = query.copy(text = ""); doSearch(query) }) { Icon(Icons.Filled.Close, "Borrar") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = query.copy(text = text, bbox = null); doSearch(query) }),
        )

        // ---- Barra: filtros, orden y lista/mapa ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { showFilters = true }, label = { Text("Filtros" + activeFilters(query)) }, leadingIcon = { Icon(Icons.Filled.FilterList, null) })
            Spacer(Modifier.padding(4.dp))
            Box {
                AssistChip(onClick = { sortMenu = true }, label = { Text(query.sort.label) }, leadingIcon = { Icon(Icons.Filled.Sort, null) })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { sortMenu = false; query = query.copy(sort = s); doSearch(query) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = !mapMode, onClick = { mapMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Icon(Icons.Filled.List, "Lista") }
                SegmentedButton(selected = mapMode, onClick = { mapMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Icon(Icons.Filled.Map, "Mapa") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (!Social.enabled) {
            Text(
                "La comunidad no está configurada en esta compilación. Estas son las rutas de tu móvil.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn {
                items(localTrails, key = { it.id }) { t ->
                    TrailCard(t.name, t.activity, t.distanceM, t.elevGainM, t.difficulty, t.authorName, null, null, null) { onOpenLocal(t.id) }
                }
            }
            return@Column
        }

        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }

        if (mapMode) {
            Box(Modifier.fillMaxSize()) {
                OsmMap(
                    modifier = Modifier.fillMaxSize(),
                    layer = MapLayer.TOPO,
                    pins = results.map { r ->
                        MapPin(r.startLat, r.startLon, r.name, Activity.of(r.activity).emoji) { selected = r }
                    },
                    onMapMoved = { box ->
                        if (searchOnMove) {
                            moveJob?.cancel()
                            moveJob = scope.launch {
                                delay(700)
                                query = query.copy(bbox = doubleArrayOf(box.latSouth, box.lonWest, box.latNorth, box.lonEast))
                                doSearch(query)
                            }
                        }
                    },
                )
                Row(
                    Modifier.align(Alignment.TopCenter).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Buscar al mover el mapa", style = MaterialTheme.typography.labelMedium)
                            Switch(checked = searchOnMove, onCheckedChange = { searchOnMove = it }, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                selected?.let { r ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                        TrailCard(r.name, r.activity, r.distanceM, r.elevGainM, r.difficulty, r.username, r.trailRank, r.clapsCount, r.coverUrl) { onOpenRemote(r.id) }
                    }
                }
            }
        } else {
            if (query.text.isNotBlank() && people.isNotEmpty()) {
                TabRow(selectedTabIndex = searchTab) {
                    Tab(selected = searchTab == 0, onClick = { searchTab = 0 }, text = { Text("Rutas (${results.size})") })
                    Tab(selected = searchTab == 1, onClick = { searchTab = 1 }, text = { Text("Personas (${people.size})") })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (searchTab == 1 && people.isNotEmpty() && query.text.isNotBlank()) {
                    items(people, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p.username)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(p.username, fontWeight = FontWeight.Bold)
                                if (!p.bio.isNullOrBlank()) Text(p.bio, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            TextButton(onClick = { onOpenUser(p.id) }) { Text("Ver perfil") }
                        }
                    }
                } else {
                    if (results.isEmpty() && !loading) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🧭", style = MaterialTheme.typography.displayMedium)
                                Text("No hay rutas con estos filtros", fontWeight = FontWeight.Bold)
                                Text("Prueba otra zona, quita filtros o sé el primero en subir una ruta desde «Grabar ruta».",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(results, key = { it.id }) { r ->
                        TrailCard(r.name, r.activity, r.distanceM, r.elevGainM, r.difficulty, r.username, r.trailRank, r.clapsCount, r.coverUrl) { onOpenRemote(r.id) }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FiltersSheet(query) { q -> query = q; showFilters = false; doSearch(q) }
        }
    }
}

private fun activeFilters(q: SearchQuery): String {
    val n = listOfNotNull(q.activity, q.maxDistanceKm, q.maxDifficulty, if (q.onlyCircular) 1 else null, if (q.followedOnly) 1 else null).size
    return if (n > 0) " ($n)" else ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(initial: SearchQuery, onApply: (SearchQuery) -> Unit) {
    var activity by remember { mutableStateOf(initial.activity) }
    var maxKm by remember { mutableStateOf((initial.maxDistanceKm ?: 0).toFloat()) }
    var maxDiff by remember { mutableStateOf((initial.maxDifficulty ?: 0).toFloat()) }
    var circular by remember { mutableStateOf(initial.onlyCircular) }
    var followed by remember { mutableStateOf(initial.followedOnly) }

    Column(Modifier.padding(16.dp)) {
        Text("Filtros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Actividad", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = activity == null, onClick = { activity = null }, label = { Text("Todas") })
            Activity.entries.forEach { a ->
                FilterChip(selected = activity == a.key, onClick = { activity = a.key }, label = { Text("${a.emoji} ${a.label}") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Distancia máxima: " + if (maxKm < 1) "sin límite" else "${maxKm.toInt()} km", fontWeight = FontWeight.Bold)
        Slider(value = maxKm, onValueChange = { maxKm = it }, valueRange = 0f..100f, steps = 19)
        Text("Dificultad máxima: " + if (maxDiff < 1) "cualquiera" else Difficulty.label(maxDiff.toInt()), fontWeight = FontWeight.Bold)
        Slider(value = maxDiff, onValueChange = { maxDiff = it }, valueRange = 0f..5f, steps = 4)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solo rutas circulares", Modifier.weight(1f))
            Switch(checked = circular, onCheckedChange = { circular = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solo de autores que sigo", Modifier.weight(1f))
            Switch(checked = followed, onCheckedChange = { followed = it }, enabled = Social.currentUserId() != null)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = { onApply(SearchQuery(text = initial.text, sort = initial.sort)) }) { Text("Quitar filtros") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                onApply(initial.copy(
                    activity = activity,
                    maxDistanceKm = maxKm.toInt().takeIf { it >= 1 },
                    maxDifficulty = maxDiff.toInt().takeIf { it >= 1 },
                    onlyCircular = circular,
                    followedOnly = followed,
                ))
            }) { Text("Aplicar filtros") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
