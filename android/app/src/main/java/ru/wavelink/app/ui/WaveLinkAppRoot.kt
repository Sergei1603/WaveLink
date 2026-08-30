package ru.wavelink.app.ui

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
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
import ru.wavelink.app.library.ArtistDetailScreen
import ru.wavelink.app.library.LibraryScreen
import ru.wavelink.app.library.SearchScreen
import ru.wavelink.app.library.TrackDetailSheet
import ru.wavelink.app.player.NowPlayingBar
import ru.wavelink.app.player.PlayerScreen
import ru.wavelink.app.player.PlayerViewModel
import ru.wavelink.app.profile.ProfileScreen
import ru.wavelink.app.publicbank.PublicBankScreen
import ru.wavelink.app.stats.StatsScreen
import ru.wavelink.app.stats.TopChartKind
import ru.wavelink.app.stats.TopChartScreen
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.theme.WaveLinkTheme
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * Three tabs, as the design specifies. Collections, downloads, statistics and the Топ-100 lists
 * are levels *inside* a tab rather than tabs of their own, so the bar stays legible and every
 * sub-level can name the level it returns to.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Library(Routes.LIBRARY, "Библиотека", Icons.Filled.LibraryMusic),
    Bank(Routes.BANK, "Банк", Icons.Filled.Public),
    Profile(Routes.PROFILE, "Профиль", Icons.Filled.Person)
}

object Routes {
    const val LIBRARY = "library"
    const val BANK = "bank"
    const val PROFILE = "profile"
    const val SEARCH = "library/search"
    const val COLLECTIONS = "library/collections"
    const val COLLECTION = "library/collections/{collectionId}"
    const val ARTIST = "library/artists/{artist}"
    const val STATS = "profile/stats"
    const val TOP = "profile/stats/top/{kind}"
    const val DOWNLOADS = "downloads/{parent}"
    const val PLAYER = "player"

    fun collection(id: String) = "library/collections/$id"
    fun top(kind: TopChartKind) = "profile/stats/top/${kind.name}"
    fun downloads(parent: String) = "downloads/$parent"

    /**
     * An artist name is free text — it can hold a slash, a space, a `#`. Percent-encoding is not
     * enough here: Navigation decodes `%2F` before it matches the route, so «AC/DC» would look
     * like two path segments. Base64 (URL-safe, unpadded) has no reserved character at all.
     */
    fun artist(name: String): String =
        "library/artists/" + Base64.encodeToString(
            name.trim().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decodeArtist(encoded: String): String = runCatching {
        String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
    }.getOrDefault("")
}

@Composable
fun WaveLinkAppRoot() {
    WaveLinkTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Wl.Bg) {
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

@Composable
private fun MainScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playerError by playerViewModel.error.collectAsStateWithLifecycle()
    val playerNotice by playerViewModel.notice.collectAsStateWithLifecycle()

    var detailTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    val openPlayer = { navController.navigate(Routes.PLAYER) }

    // The player is a full-bleed level: it hides the tab bar and the mini-player it grew out of.
    val chromeVisible = route != Routes.PLAYER

    Box(modifier = Modifier.fillMaxSize().background(Wl.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.LIBRARY,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            playingTrackId = playerState.trackId,
                            onPlay = { track, queue -> playerViewModel.play(track, queue, "Библиотека") },
                            onOpenDetail = { detailTrackId = it.id },
                            onShuffle = { mode -> playerViewModel.shuffle(mode) },
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenCollections = { navController.navigate(Routes.COLLECTIONS) },
                            onOpenDownloads = { navController.navigate(Routes.downloads("Библиотека")) },
                            onOpenArtist = { navController.navigate(Routes.artist(it)) },
                            onDownload = { ids -> ids.forEach(downloadsViewModel::download) }
                        )
                    }
                    composable(
                        Routes.ARTIST,
                        arguments = listOf(navArgument("artist") { type = NavType.StringType })
                    ) { entry ->
                        val name = Routes.decodeArtist(entry.arguments?.getString("artist").orEmpty())
                        ArtistDetailScreen(
                            artist = name,
                            playingTrackId = playerState.trackId,
                            onBack = navController::popBackStack,
                            onPlay = { track, queue -> playerViewModel.play(track, queue, name) },
                            onOpenDetail = { detailTrackId = it.id },
                            // A rename retires this route's key, so the new folder replaces it
                            // rather than stacking a second artist screen behind the first.
                            onOpenArtist = { renamed ->
                                navController.navigate(Routes.artist(renamed)) {
                                    popUpTo(Routes.ARTIST) { inclusive = true }
                                }
                            },
                            onDownload = { ids -> ids.forEach(downloadsViewModel::download) }
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onBack = navController::popBackStack,
                            onPlay = { track, queue -> playerViewModel.play(track, queue, "Поиск") },
                            onOpenDetail = { detailTrackId = it.id },
                            onOpenBank = {
                                navController.popBackStack()
                                navController.switchTab(Routes.BANK)
                            }
                        )
                    }
                    composable(Routes.COLLECTIONS) {
                        CollectionsScreen(
                            onBack = navController::popBackStack,
                            onOpen = { navController.navigate(Routes.collection(it)) },
                            onShuffle = { mode -> playerViewModel.shuffle(mode) }
                        )
                    }
                    composable(
                        Routes.COLLECTION,
                        arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                    ) { entry ->
                        val id = entry.arguments?.getString("collectionId").orEmpty()
                        CollectionDetailScreen(
                            collectionId = id,
                            playingTrackId = playerState.trackId,
                            onBack = navController::popBackStack,
                            onPlay = { track, queue, source -> playerViewModel.play(track, queue, source) },
                            onOpenDetail = { detailTrackId = it.id },
                            onShuffle = { mode -> playerViewModel.shuffle(mode, collectionId = id) }
                        )
                    }
                    composable(Routes.BANK) {
                        PublicBankScreen(
                            playingTrackId = playerState.trackId,
                            onPlay = { track, queue -> playerViewModel.play(track, queue, "Общий банк") },
                            onOpenDetail = { detailTrackId = it.id }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            onOpenStats = { navController.navigate(Routes.STATS) },
                            onOpenDownloads = { navController.navigate(Routes.downloads("Профиль")) },
                            onSignOut = onSignOut
                        )
                    }
                    composable(Routes.STATS) {
                        StatsScreen(
                            onBack = navController::popBackStack,
                            onOpenTop = { kind -> navController.navigate(Routes.top(kind)) },
                            onOpenTrack = { detailTrackId = it },
                            onOpenArtist = { navController.navigate(Routes.artist(it)) }
                        )
                    }
                    composable(
                        Routes.TOP,
                        arguments = listOf(navArgument("kind") { type = NavType.StringType })
                    ) { entry ->
                        val kind = runCatching {
                            TopChartKind.valueOf(entry.arguments?.getString("kind").orEmpty())
                        }.getOrDefault(TopChartKind.Tracks)
                        TopChartScreen(
                            kind = kind,
                            onBack = navController::popBackStack,
                            onOpenTrack = { detailTrackId = it },
                            onOpenArtist = { navController.navigate(Routes.artist(it)) }
                        )
                    }
                    composable(
                        Routes.DOWNLOADS,
                        arguments = listOf(navArgument("parent") { type = NavType.StringType })
                    ) { entry ->
                        DownloadsScreen(
                            parentLabel = entry.arguments?.getString("parent") ?: "Библиотека",
                            onBack = navController::popBackStack,
                            viewModel = downloadsViewModel
                        )
                    }
                    composable(Routes.PLAYER) {
                        PlayerScreen(
                            onCollapse = navController::popBackStack,
                            onOpenDetail = { detailTrackId = it },
                            viewModel = playerViewModel,
                            onDownload = downloadsViewModel::download,
                            onRemoveDownload = downloadsViewModel::remove
                        )
                    }
                }

                playerError?.let { Toast(it, onDismiss = playerViewModel::dismissError) }
                if (playerError == null) {
                    playerNotice?.let { Toast(it, onDismiss = playerViewModel::dismissNotice) }
                }
            }

            if (chromeVisible) {
                Column(modifier = Modifier.fillMaxWidth().background(Wl.BarBackground)) {
                    NowPlayingBar(
                        state = playerState,
                        onToggle = playerViewModel::togglePlayPause,
                        onOpen = openPlayer
                    )
                    TabBar(navController = navController, currentRoute = route)
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

/** The design's three-column bar: icon over an 11px label, accent when current. */
@Composable
private fun TabBar(navController: NavHostController, currentRoute: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingRule(top = true, inset = 0.dp)
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute != null && currentRoute.startsWith(tab.route)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { navController.switchTab(tab.route) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) Wl.Accent else Wl.text(45),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    tab.label,
                    style = WlType.Micro,
                    color = if (selected) Wl.Accent else Wl.text(45),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A tab press returns to that tab's root and keeps the other tabs' stacks — the usual bottom-nav
 * contract, spelled out because `library/collections` and friends live under a tab's prefix.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Errors from playback and shuffle land here rather than in a Material snackbar host. */
@Composable
private fun Toast(message: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(Wl.RadiusMd)
                .background(Wl.Surface)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message, style = WlType.Caption, color = Wl.Accent200)
        }
    }
}

/** The mini-player's play-state glyph, shared with the full player. */
@Composable
internal fun PlayPauseIcon(isPlaying: Boolean, tint: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    Icon(
        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = if (isPlaying) "Пауза" else "Играть",
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/** A 2px seam of progress along the top of the mini-player. */
@Composable
internal fun HairlineProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(2.dp).background(Wl.Neutral800)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Wl.Accent)
        )
    }
}
