package com.iparsan.maprog.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iparsan.maprog.BuildConfig
import com.iparsan.maprog.MaprogApp
import com.iparsan.maprog.data.ListEntity
import com.iparsan.maprog.data.TrailEntity
import com.iparsan.maprog.remote.Profile
import com.iparsan.maprog.remote.RemoteTrail
import com.iparsan.maprog.remote.SearchQuery
import com.iparsan.maprog.remote.Social
import com.iparsan.maprog.remote.Sort
import com.iparsan.maprog.ui.components.Avatar
import com.iparsan.maprog.ui.components.TrailCard
import com.iparsan.maprog.util.Fmt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenLocal: (Long) -> Unit,
    onOpenRemote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenList: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val repo = MaprogApp.instance.repo
    val scope = rememberCoroutineScope()

    var userId by remember { mutableStateOf(Social.currentUserId()) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var following by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var followersCount by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editBio by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }

    val myTrails by repo.trails.myTrails().collectAsState(initial = emptyList())
    val saved by repo.trails.savedTrails().collectAsState(initial = emptyList())
    val lists by repo.lists.lists().collectAsState(initial = emptyList())

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val id = repo.importGpx(uri); onOpenLocal(id) }
            catch (e: Exception) { Toast.makeText(ctx, "No se pudo importar: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        try {
            profile = Social.profile(uid)
            following = Social.profiles(Social.following(uid))
            followersCount = Social.followers(uid).size
            val n = repo.uploadPending()
            if (n > 0) Toast.makeText(ctx, "Se han subido $n rutas pendientes", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "Importar GPX") }
                    if (userId != null) IconButton(onClick = {
                        scope.launch { Social.signOut(); userId = null; profile = null }
                    }) { Icon(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // ---- Cabecera del perfil / acceso ----
            if (Social.enabled && userId == null) {
                AuthCard(onLoggedIn = { userId = Social.currentUserId() })
            } else {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile?.username ?: "Yo", 64)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(profile?.username ?: "Mi Maprog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val bio = profile?.bio
                        if (!bio.isNullOrBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${myTrails.size} rutas · ${following.size} siguiendo · $followersCount seguidores",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val km = myTrails.sumOf { it.distanceM }
                        val up = myTrails.sumOf { it.elevGainM }
                        Text("Total: ${Fmt.km(km)} · ↗ ${up.toInt()} m", style = MaterialTheme.typography.labelMedium)
                    }
                    if (userId != null) IconButton(onClick = { editBio = true }) { Icon(Icons.Filled.Edit, "Editar perfil") }
                }
            }

            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                listOf("Tus rutas", "Rutas guardadas", "Listas", "Siguiendo").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> {
                        if (myTrails.isEmpty()) item { Empty("Aún no has grabado rutas", "Ve a «Grabar ruta» o importa un GPX con el botón de arriba.") }
                        if (myTrails.any { it.pendingUpload } && userId != null) item {
                            TextButton(onClick = { scope.launch { val n = repo.uploadPending(); Toast.makeText(ctx, "Subidas: $n", Toast.LENGTH_SHORT).show() } },
                                modifier = Modifier.padding(horizontal = 12.dp)) {
                                Icon(Icons.Filled.Sync, null); Text(" Subir rutas pendientes")
                            }
                        }
                        items(myTrails, key = { it.id }) { t -> LocalCard(t, onOpenLocal) }
                    }
                    1 -> {
                        if (saved.isEmpty()) item { Empty("No tienes rutas guardadas", "En la ficha de una ruta de Explora pulsa «Guardar» para tenerla sin conexión.") }
                        items(saved, key = { it.id }) { t -> LocalCard(t, onOpenLocal) }
                    }
                    2 -> {
                        item {
                            OutlinedButton(onClick = { newList = true }, modifier = Modifier.padding(12.dp)) { Text("+ Nueva lista") }
                        }
                        if (lists.isEmpty()) item { Empty("Sin listas", "Organiza rutas por viajes o planes, como «Pirineo 2027».") }
                        items(lists, key = { it.id }) { l -> ListRow(l, onOpenList) }
                    }
                    3 -> {
                        if (following.isEmpty()) item { Empty("No sigues a nadie", "Busca personas en Explora o entra en el perfil de un autor.") }
                        items(following, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenUser(p.id) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(p.username)
                                Text(p.username, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Maprog ${BuildConfig.VERSION_NAME} · software libre (GPL-3.0) · Mapas © colaboradores de OpenStreetMap",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (editBio) {
        var bio by remember { mutableStateOf(profile?.bio ?: "") }
        AlertDialog(
            onDismissRequest = { editBio = false },
            title = { Text("Sobre ti") },
            text = { OutlinedTextField(bio, { bio = it.take(300) }, minLines = 3, label = { Text("Descripción del perfil") }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Social.updateBio(bio); profile = profile?.copy(bio = bio) } catch (_: Exception) { }
                        editBio = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editBio = false }) { Text("Cancelar") } },
        )
    }
    if (newList) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newList = false },
            title = { Text("Nueva lista") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { repo.lists.insert(ListEntity(name = name.trim())); newList = false }
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { newList = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LocalCard(t: TrailEntity, onOpen: (Long) -> Unit) {
    val status = when {
        t.saved -> "guardada"
        t.remoteId != null -> "publicada"
        t.pendingUpload -> "pendiente de subir"
        else -> "privada"
    }
    TrailCard(t.name, t.activity, t.distanceM, t.elevGainM, t.difficulty, t.authorName, null, null, null,
        subtitle = "${Fmt.date(t.createdAt)} · $status") { onOpen(t.id) }
}

@Composable
private fun ListRow(l: ListEntity, onOpen: (Long) -> Unit) {
    val count by MaprogApp.instance.repo.lists.count(l.id).collectAsState(initial = 0)
    Row(Modifier.fillMaxWidth().clickable { onOpen(l.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("📁", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp)) {
            Text(l.name, fontWeight = FontWeight.Bold)
            Text("$count rutas", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun Empty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Registro / inicio de sesión con email y contraseña (Supabase Auth). */
@Composable
private fun AuthCard(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(if (register) "Crea tu cuenta de Maprog" else "Inicia sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Para publicar rutas, comentar, aplaudir y seguir a otros. Sin cuenta puedes explorar y grabar igualmente.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (register) OutlinedTextField(username, { username = it.take(30) }, label = { Text("Nombre de usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mín. 6)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && email.contains("@") && password.length >= 6 && (!register || username.length >= 3),
                onClick = {
                    scope.launch {
                        busy = true; message = null
                        try {
                            if (register) {
                                Social.signUp(email, password, username)
                                if (Social.currentUserId() != null) onLoggedIn()
                                else message = "Te hemos enviado un email: confírmalo y luego inicia sesión."
                                register = false
                            } else {
                                Social.signIn(email, password)
                                onLoggedIn()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(ctx, traducir(e.message), Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                },
            ) { Text(if (register) "Crear cuenta" else "Entrar") }
            TextButton(onClick = { register = !register; message = null }) {
                Text(if (register) "Ya tengo cuenta" else "Crear cuenta nueva")
            }
        }
    }
}

private fun traducir(msg: String?): String = when {
    msg == null -> "Error desconocido"
    msg.contains("Invalid login", true) -> "Email o contraseña incorrectos"
    msg.contains("Email not confirmed", true) -> "Confirma tu email antes de entrar (revisa tu correo)"
    msg.contains("already registered", true) -> "Ese email ya tiene cuenta"
    msg.contains("rate limit", true) -> "Demasiados intentos, espera un poco"
    else -> msg.lineSequence().firstOrNull() ?: msg
}

// ------------------- Perfil de otra persona -------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(userId: String, onBack: () -> Unit, onOpenRemote: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var trails by remember { mutableStateOf<List<RemoteTrail>>(emptyList()) }
    var isFollowing by remember { mutableStateOf(false) }
    var followers by remember { mutableIntStateOf(0) }
    var followingCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(userId) {
        try {
            profile = Social.profile(userId)
            trails = Social.search(SearchQuery(authorId = userId, sort = Sort.RECENT), 100)
            isFollowing = Social.isFollowing(userId)
            followers = Social.followers(userId).size
            followingCount = Social.following(userId).size
        } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.username ?: "Perfil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile?.username, 64)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(profile?.username ?: "…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val bio = profile?.bio
                        if (!bio.isNullOrBlank()) Text(bio)
                        Text("${trails.size} rutas · $followers seguidores · $followingCount siguiendo", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (userId != Social.currentUserId()) {
                    val action: () -> Unit = {
                        if (Social.currentUserId() == null) Toast.makeText(ctx, "Inicia sesión para seguir a otros", Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            try {
                                isFollowing = Social.toggleFollow(userId)
                                followers += if (isFollowing) 1 else -1
                            } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    if (isFollowing) OutlinedButton(onClick = action, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Siguiendo ✓") }
                    else Button(onClick = action, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Seguir") }
                }
            }
            items(trails, key = { it.id }) { r ->
                TrailCard(r.name, r.activity, r.distanceM, r.elevGainM, r.difficulty, null, r.trailRank, r.clapsCount, r.coverUrl) { onOpenRemote(r.id) }
            }
        }
    }
}

// ------------------- Una lista -------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(listId: Long, onBack: () -> Unit, onOpenLocal: (Long) -> Unit) {
    val repo = MaprogApp.instance.repo
    val scope = rememberCoroutineScope()
    val trails by repo.lists.trailsInList(listId).collectAsState(initial = emptyList())
    val lists by repo.lists.lists().collectAsState(initial = emptyList())
    val list = lists.firstOrNull { it.id == listId }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "Lista") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = { IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Borrar lista") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            if (trails.isEmpty()) item { Empty("Lista vacía", "Añade rutas desde su ficha con «A una lista».") }
            items(trails, key = { it.id }) { t -> LocalCard(t, onOpenLocal) }
        }
    }
    if (confirm && list != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Borrar la lista «${list.name}»?") },
            text = { Text("Las rutas no se borran, solo la lista.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.lists.delete(list); confirm = false; onBack() } }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
