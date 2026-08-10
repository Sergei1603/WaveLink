package ru.wavelink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.wavelink.app.auth.AuthScreen
import ru.wavelink.app.auth.AuthViewModel
import ru.wavelink.app.collections.CollectionDetailScreen
import ru.wavelink.app.collections.CollectionsScreen
import ru.wavelink.app.downloads.DownloadsScreen
import ru.wavelink.app.downloads.DownloadsViewModel
import ru.wavelink.app.library.LibraryScreen
import ru.wavelink.app.library.TrackDetailSheet
import ru.wavelink.app.player.NowPlayingBar
import ru.wavelink.app.player.PlayerScreen
import ru.wavelink.app.player.PlayerViewModel
import ru.wavelink.app.publicbank.PublicBankScreen
import ru.wavelink.app.settings.SettingsScreen
import ru.wavelink.app.stats.StatsScreen
import ru.wavelink.app.ui.theme.WaveLinkTheme

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Library("library", "Треки", Icons.Filled.LibraryMusic),
    PublicBank("public", "Банк", Icons.Filled.Public),
    Collections("collections", "Коллекции", Icons.Filled.QueueMusic),
    Stats("stats", "Статистика", Icons.Filled.BarChart),
    Downloads("downloads", "Офлайн", Icons.Filled.Download)
}

private const val ROUTE_PLAYER = "player"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_COLLECTION = "collections/{collectionId}"

@Composable
fun WaveLinkAppRoot() {
    WaveLinkTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val authViewModel: AuthViewModel = hiltViewModel()
            val signedIn by authViewModel.isSignedIn.collectAsStateWithLifecycle()

            when (signedIn) {
                // null = the token store has not reported yet; avoid flashing the login form.
                null -> Box(modifier = Modifier.fillMaxSize())
                false -> {
                    var registerMode by rememberSaveable { mutableStateOf(false) }
                    AuthScreen(
                        registerMode = registerMode,
                        onToggleMode = { registerMode = !registerMode },
                        viewModel = authViewModel
                    )
                }
                true -> MainScaffold(onSignOut = authViewModel::logout)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playerError by playerViewModel.error.collectAsStateWithLifecycle()

    var detailTrackId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WaveLink") },
                actions = {
                    IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                NowPlayingBar(
                    state = playerState,
                    onToggle = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::next,
                    onOpen = { navController.navigate(ROUTE_PLAYER) }
                )
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            // Five destinations leave ~72dp per label; without the smaller style
                            // and maxLines the longest one wraps onto a second line.
                            label = {
                                Text(
                                    item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            playerError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            NavHost(
                navController = navController,
                startDestination = TopLevel.Library.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(TopLevel.Library.route) {
                    LibraryScreen(
                        onPlay = playerViewModel::play,
                        onOpenDetail = { detailTrackId = it.id },
                        onShuffle = { mode -> playerViewModel.shuffle(mode) }
                    )
                }
                composable(TopLevel.PublicBank.route) {
                    PublicBankScreen(onPlay = playerViewModel::play)
                }
                composable(TopLevel.Collections.route) {
                    CollectionsScreen(onOpen = { navController.navigate("collections/$it") })
                }
                composable(
                    ROUTE_COLLECTION,
                    arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("collectionId").orEmpty()
                    CollectionDetailScreen(
                        collectionId = id,
                        onPlay = playerViewModel::play,
                        onOpenDetail = { detailTrackId = it.id },
                        onShuffle = { mode -> playerViewModel.shuffle(mode, collectionId = id) }
                    )
                }
                composable(TopLevel.Stats.route) { StatsScreen() }
                composable(TopLevel.Downloads.route) { DownloadsScreen(viewModel = downloadsViewModel) }
                composable(ROUTE_SETTINGS) { SettingsScreen(onSignOut = onSignOut) }
                composable(ROUTE_PLAYER) {
                    PlayerScreen(
                        state = playerState,
                        onToggle = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                        onPrevious = playerViewModel::previous,
                        onSeek = playerViewModel::seekTo,
                        onStop = {
                            playerViewModel.stop()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

    detailTrackId?.let { id ->
        TrackDetailSheet(
            trackId = id,
            onDismiss = { detailTrackId = null },
            onDownload = { downloadsViewModel.download(id) },
            onRemoveDownload = { downloadsViewModel.remove(id) }
        )
    }
}
