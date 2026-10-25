package com.nusanime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nusanime.app.ui.player.PlayerScreen
import com.nusanime.app.ui.screens.*
import com.nusanime.app.ui.theme.NusanimeTheme

object Routes {
    const val HOME = "home"
    const val ONGOING = "ongoing"
    const val COMPLETED = "completed"
    const val SCHEDULE = "schedule"
    const val SEARCH = "search"
    const val DETAIL = "detail/{animeId}"
    const val PLAYER = "player/{episodeId}"
    fun detail(animeId: String) = "detail/$animeId"
    fun player(episodeId: String) = "player/$episodeId"
}

private data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NusanimeTheme {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        BottomItem(Routes.HOME, "Beranda", Icons.Default.Home),
        BottomItem(Routes.ONGOING, "Ongoing", Icons.Default.Subscriptions),
        BottomItem(Routes.COMPLETED, "Tamatan", Icons.Default.TaskAlt),
        BottomItem(Routes.SCHEDULE, "Jadwal", Icons.Default.CalendarMonth),
        BottomItem(Routes.SEARCH, "Cari", Icons.Default.Search),
    )
    val showBars = currentRoute in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.HOME) { ListScreen(Source.Home) { nav.navigate(Routes.detail(it)) } }
            composable(Routes.ONGOING) { ListScreen(Source.Ongoing) { nav.navigate(Routes.detail(it)) } }
            composable(Routes.COMPLETED) { ListScreen(Source.Completed) { nav.navigate(Routes.detail(it)) } }
            composable(Routes.SCHEDULE) { ScheduleScreen { nav.navigate(Routes.detail(it)) } }
            composable(Routes.SEARCH) { SearchScreen { nav.navigate(Routes.detail(it)) } }
            composable(Routes.DETAIL) { entry ->
                val animeId = entry.arguments?.getString("animeId").orEmpty()
                DetailScreen(
                    animeId = animeId,
                    onBack = { nav.popBackStack() },
                    onOpenEpisode = { nav.navigate(Routes.player(it)) },
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
