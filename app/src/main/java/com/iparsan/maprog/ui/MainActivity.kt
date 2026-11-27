package com.iparsan.maprog.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iparsan.maprog.MaprogApp
import com.iparsan.maprog.recording.RecStatus
import com.iparsan.maprog.recording.Recorder
import com.iparsan.maprog.ui.explore.ExploreScreen
import com.iparsan.maprog.ui.profile.ListScreen
import com.iparsan.maprog.ui.profile.ProfileScreen
import com.iparsan.maprog.ui.profile.UserScreen
import com.iparsan.maprog.ui.record.RecordScreen
import com.iparsan.maprog.ui.theme.MaprogTheme
import com.iparsan.maprog.ui.trail.TrailDetailScreen
import kotlinx.coroutines.launch

object Routes {
    const val EXPLORE = "explore"
    const val RECORD = "record"
    const val PROFILE = "profile"
    fun localTrail(id: Long) = "trail/local/$id"
    fun remoteTrail(id: String) = "trail/remote/$id"
    fun user(id: String) = "user/$id"
    fun list(id: Long) = "list/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.EXPLORE, "Explora", Icons.Filled.TravelExplore),
    Tab(Routes.RECORD, "Grabar ruta", Icons.Filled.FiberManualRecord),
    Tab(Routes.PROFILE, "Perfil", Icons.Filled.Person),
)

class MainActivity : ComponentActivity() {

    private val openTrail = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleGpxIntent(intent)
        setContent {
            MaprogTheme { MaprogRoot(openTrail.value) { openTrail.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGpxIntent(intent)
    }

    /** Si abres un .gpx con Maprog desde otra app, se importa y se abre su ficha. */
    private fun handleGpxIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            try {
                val id = (application as MaprogApp).repo.importGpx(uri)
                openTrail.value = id
                Toast.makeText(this@MainActivity, "Ruta importada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "No se pudo importar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun MaprogRoot(openTrail: Long?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val rec by Recorder.state.collectAsState()
    val showBar = route in tabs.map { it.route }

    LaunchedEffect(openTrail) {
        if (openTrail != null) { nav.navigate(Routes.localTrail(openTrail)); onOpened() }
    }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = {
                            val tint = if (t.route == Routes.RECORD && rec.status == RecStatus.RECORDING) com.iparsan.maprog.ui.theme.RecRed else androidx.compose.ui.graphics.Color.Unspecified
                            Icon(t.icon, contentDescription = t.label, tint = tint)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.EXPLORE, modifier = Modifier.padding(padding)) {
            composable(Routes.EXPLORE) {
                ExploreScreen(
                    onOpenRemote = { nav.navigate(Routes.remoteTrail(it)) },
                    onOpenLocal = { nav.navigate(Routes.localTrail(it)) },
                    onOpenUser = { nav.navigate(Routes.user(it)) },
                )
            }
            composable(Routes.RECORD) {
                RecordScreen(onSaved = { id -> nav.navigate(Routes.localTrail(id)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenLocal = { nav.navigate(Routes.localTrail(it)) },
                    onOpenRemote = { nav.navigate(Routes.remoteTrail(it)) },
                    onOpenUser = { nav.navigate(Routes.user(it)) },
                    onOpenList = { nav.navigate(Routes.list(it)) },
                )
            }
            composable("trail/local/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                TrailDetailScreen(
                    localId = e.arguments?.getLong("id"), remoteId = null,
                    onBack = { nav.popBackStack() },
                    onFollow = { nav.goTab(Routes.RECORD) },
                    onOpenUser = { nav.navigate(Routes.user(it)) },
                    onOpenLocal = { nav.navigate(Routes.localTrail(it)) { popUpTo("trail/local/{id}") { inclusive = true } } },
                )
            }
            composable("trail/remote/{id}") { e ->
                TrailDetailScreen(
                    localId = null, remoteId = e.arguments?.getString("id"),
                    onBack = { nav.popBackStack() },
                    onFollow = { nav.goTab(Routes.RECORD) },
                    onOpenUser = { nav.navigate(Routes.user(it)) },
                    onOpenLocal = { nav.navigate(Routes.localTrail(it)) },
                )
            }
            composable("user/{id}") { e ->
                UserScreen(
                    userId = e.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    onOpenRemote = { nav.navigate(Routes.remoteTrail(it)) },
                )
            }
            composable("list/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ListScreen(
                    listId = e.arguments?.getLong("id") ?: 0,
                    onBack = { nav.popBackStack() },
                    onOpenLocal = { nav.navigate(Routes.localTrail(it)) },
                )
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
