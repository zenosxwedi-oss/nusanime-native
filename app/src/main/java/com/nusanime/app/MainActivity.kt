package com.nusanime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nusanime.app.ui.player.PlayerScreen
import com.nusanime.app.ui.screens.*
import com.nusanime.app.ui.theme.NusanimeTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

object Routes {
    const val HOME = "home"
    const val ONGOING = "ongoing"
    const val COMPLETED = "completed"
    const val SCHEDULE = "schedule"
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{animeId}"
    const val PLAYER = "player/{episodeId}"
    fun detail(animeId: String) = "detail/$animeId"
    fun player(episodeId: String) = "player/$episodeId"
}

private data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

class MainActivity : ComponentActivity() {
    private var deepLinkEpisode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.nusanime.app.notify.EpisodeNotifier.ensureChannel(this)
        com.nusanime.app.notify.EpisodeNotifier.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        deepLinkEpisode = intent?.getStringExtra("openEpisode")
        if (deepLinkEpisode != null) {
            startActivity(android.content.Intent(this, MainActivity::class.java).apply {
                putExtra("openEpisode", deepLinkEpisode)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        setContent {
            NusanimeTheme {
                App(openEpisode = deepLinkEpisode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(openEpisode: String? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    androidx.compose.runtime.LaunchedEffect(openEpisode) {
        if (openEpisode != null) nav.navigate(Routes.player(openEpisode))
    }

    // Auto-check update on launch (once)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var updateInfo by remember { mutableStateOf<com.nusanime.app.update.SelfUpdater.ReleaseInfo?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val info = com.nusanime.app.update.SelfUpdater.latest(ctx)
                val cur = com.nusanime.app.update.SelfUpdater.currentVersionName(ctx)
                if (info != null && com.nusanime.app.update.SelfUpdater.isNewer(info.versionName, cur)) {
                    updateInfo = info
                }
            } catch (_: Exception) {}
        }
    }
    updateInfo?.takeIf { !updateDismissed }?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { updateDismissed = true },
            title = { Text("Pembaruan tersedia") },
            text = { Text("Versi baru v${info.versionName} tersedia. Unduh sekarang?") },
            confirmButton = {
                TextButton(onClick = {
                    updateDismissed = true
                    nav.navigate(Routes.SETTINGS)
                }) { Text("Buka Unduhan") }
            },
            dismissButton = { TextButton(onClick = { updateDismissed = true }) { Text("Nanti") } },
        )
    }

    val tabs = listOf(
        BottomItem(Routes.HOME, "Beranda", Icons.Default.Home),
        BottomItem(Routes.ONGOING, "Ongoing", Icons.Default.Subscriptions),
        BottomItem(Routes.COMPLETED, "Tamatan", Icons.Default.TaskAlt),
        BottomItem(Routes.SCHEDULE, "Jadwal", Icons.Default.CalendarMonth),
        BottomItem(Routes.SEARCH, "Cari", Icons.Default.Search),
        BottomItem(Routes.BOOKMARKS, "Simpan", Icons.Default.Bookmarks),
        BottomItem(Routes.HISTORY, "Riwayat", Icons.Default.History),
        BottomItem(Routes.DOWNLOADS, "Unduhan", Icons.Default.Download),
        BottomItem(Routes.SETTINGS, "Setelan", Icons.Default.Settings),
    )
    val showBars = currentRoute in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBars) {
                NavigationBar(
                    containerColor = Color(0xFF161D2C),
                    tonalElevation = 8.dp,
                ) {
                    val bottomTabs = listOf(
                        BottomItem(Routes.HOME, "HOME", Icons.Default.Home),
                        BottomItem(Routes.SCHEDULE, "JADWAL", Icons.Default.CalendarMonth),
                        BottomItem(Routes.SEARCH, "CARI", Icons.Default.Search),
                        BottomItem(Routes.BOOKMARKS, "SIMPAN", Icons.Default.Bookmarks),
                        BottomItem(Routes.SETTINGS, "PROFILE", Icons.Default.Settings),
                    )
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFFF4757),
                                selectedTextColor = Color(0xFFFF4757),
                                unselectedIconColor = Color(0xFF8A94A8),
                                unselectedTextColor = Color(0xFF8A94A8),
                                indicatorColor = Color(0xFF2A1B20),
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "splash",
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.HOME) { ListScreen(Source.Home, { nav.navigate(Routes.detail(it)) }) { nav.navigate(Routes.player(it)) } }
            composable(Routes.ONGOING) { ListScreen(Source.Ongoing, { nav.navigate(Routes.detail(it)) }) { nav.navigate(Routes.player(it)) } }
            composable(Routes.COMPLETED) { ListScreen(Source.Completed, { nav.navigate(Routes.detail(it)) }) }
            composable(Routes.SCHEDULE) { ScheduleScreen { nav.navigate(Routes.detail(it)) } }
            composable(Routes.SEARCH) { SearchScreen { nav.navigate(Routes.detail(it)) } }
            composable(Routes.BOOKMARKS) { BookmarksScreen { nav.navigate(Routes.detail(it)) } }
            composable(Routes.HISTORY) { HistoryScreen { nav.navigate(Routes.player(it)) } }
            composable(Routes.DOWNLOADS) { DownloadsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable("splash") {
                com.nusanime.app.ui.screens.SplashScreen()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    nav.navigate(Routes.HOME) { popUpTo("splash") { inclusive = true } }
                }
            }
            composable(Routes.DETAIL) { entry ->
                val animeId = entry.arguments?.getString("animeId").orEmpty()
                DetailScreen(
                    animeId = animeId,
                    onBack = { nav.popBackStack() },
                    onOpenEpisode = { nav.navigate(Routes.player(it)) },
                    onOpenDetail = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.PLAYER) { entry ->
                val episodeId = entry.arguments?.getString("episodeId").orEmpty()
                PlayerScreen(
                    episodeId = episodeId,
                    onBack = { nav.popBackStack() },
                    onOpenEpisode = { ep ->
                        nav.navigate(Routes.player(ep)) { popUpTo(Routes.PLAYER) { inclusive = true } }
                    },
                )
            }
        }
    }
}
