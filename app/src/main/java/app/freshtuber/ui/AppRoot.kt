package app.freshtuber.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.freshtuber.R
import app.freshtuber.ui.components.AppMessages
import app.freshtuber.ui.components.BottomBarSpace
import app.freshtuber.ui.components.LocalAppMessages
import app.freshtuber.ui.components.LocalBottomBarSpace
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.navigation.*
import app.freshtuber.ui.player.LocalInPictureInPicture
import app.freshtuber.ui.player.MiniPlayer
import app.freshtuber.ui.screens.account.AccountScreen
import app.freshtuber.ui.screens.downloads.DownloadsScreen
import app.freshtuber.ui.screens.home.HomeScreen
import app.freshtuber.ui.screens.library.LibrarySection
import app.freshtuber.ui.screens.library.ListScreen
import app.freshtuber.ui.screens.login.LoginScreen
import app.freshtuber.ui.screens.player.PlayerScreen
import app.freshtuber.ui.screens.search.SearchScreen
import app.freshtuber.ui.screens.settings.SettingsScreen
import app.freshtuber.ui.screens.shorts.ShortsScreen
import app.freshtuber.ui.screens.subscriptions.SubscriptionsScreen
import app.freshtuber.ui.screens.welcome.WelcomeScreen

@Composable
fun AppRoot(inPictureInPicture: Boolean, viewModel: MainViewModel = hiltViewModel(), openVideo: String? = null, onVideoOpened: () -> Unit = {}) {
    val loadedStart by viewModel.startRoute.collectAsStateWithLifecycle()
    // The graph's start is fixed on first load; later changes are handled by navigation.
    var startRoute by remember { mutableStateOf<Any?>(null) }
    if (startRoute == null && loadedStart != null) startRoute = loadedStart

    val start = startRoute
    if (start == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    LaunchedEffect(openVideo) {
        if (openVideo != null) {
            val current = navController.currentBackStackEntry
            val sameVideo = current?.destination?.hasRoute<PlayerRoute>() == true && current.toRoute<PlayerRoute>().videoId == openVideo
            if (!sameVideo) navController.navigate(PlayerRoute(openVideo))
            onVideoOpened()
        }
    }
    val leftHanded by viewModel.leftHanded.collectAsStateWithLifecycle()
    val shortsEnabled by viewModel.shortsEnabled.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val expired by viewModel.expiredSession.collectAsStateWithLifecycle()
    val downloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onPlayer = destination?.hasRoute<PlayerRoute>() == true
    val inFlow = destination == null || destination.hasRoute<WelcomeRoute>() || destination.hasRoute<LoginRoute>()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val dockVisible = !inPictureInPicture && !onPlayer && !inFlow && !keyboardVisible
    val miniVisible = dockVisible && destination?.hasRoute<ShortsRoute>() != true && playerState.nowPlaying?.isShort == false

    var mineOpen by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(DockTab.Home) }
    LaunchedEffect(destination) { destination?.dockTab()?.let { currentTab = it } }
    LaunchedEffect(dockVisible) { if (!dockVisible) mineOpen = false }

    val signIn: () -> Unit = { navController.navigate(LoginRoute) }
    val navigation = remember(navController) {
        object : FeedNavigation {
            override fun openVideo(videoId: String) = navController.navigate(PlayerRoute(videoId))
            override fun openPlaylist(playlistId: String, title: String, seedVideoId: String?) =
                navController.navigate(PlaylistRoute(playlistId, title, seedVideoId))
            override fun openChannel(channelId: String) = navController.navigate(ChannelRoute(channelId))
        }
    }
    val openSection: (Any) -> Unit = { route -> navController.navigateToTopLevel(route) }

    val activity = androidx.activity.compose.LocalActivity.current
    val lightBars = MaterialTheme.colorScheme.surface.let { it.red + it.green + it.blue > 1.5f } && !onPlayer
    SideEffect {
        activity?.window?.let { window -> androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        } }
    }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember(snackbarHost, scope) { AppMessages(snackbarHost, scope) }
    val barSpace = remember { BottomBarSpace() }

    CompositionLocalProvider(LocalInPictureInPicture provides inPictureInPicture, LocalAppMessages provides messages,
        LocalBottomBarSpace provides barSpace, app.freshtuber.ui.components.LocalLeftHanded provides leftHanded, app.freshtuber.ui.feed.LocalFeedBottomClearance provides 16.dp) {
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize()
        .then(if (onPlayer || inPictureInPicture) Modifier else Modifier.windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = start,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(when {
                        onPlayer || inPictureInPicture -> Modifier
                        dockVisible -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        else -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime))
                    }),
                ) {
                    composable<WelcomeRoute> {
                        WelcomeScreen(
                            onSignIn = { navController.navigate(LoginRoute) },
                            onSkip = {
                                viewModel.continueWithoutAccount()
                                navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                            },
                        )
                    }
                    composable<LoginRoute> {
                        LoginScreen(
                            onClose = { navController.popBackStack() },
                            onDone = {
                                val cameFromWelcome = navController.previousBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true
                                if (cameFromWelcome) navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                                else navController.popBackStack()
                            },
                        )
                    }
                    composable<HomeRoute> { HomeScreen(navigation, onSignIn = signIn) }
                    composable<ShortsRoute> { ShortsScreen(navigation, onSignIn = signIn) }
                    composable<SubscriptionsRoute> { SubscriptionsScreen(navigation, onSignIn = signIn, onSearch = { openSection(SearchRoute) }) }
                    composable<SearchRoute> { SearchScreen(navigation, onSignIn = signIn) }
                    composable<DownloadsRoute> { DownloadsScreen(onBack = { navController.popBackStack() }, onVideo = { navController.navigate(PlayerRoute(it)) }, onSearch = { openSection(SearchRoute) }) }
                    composable<LibraryListRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn,
                        onLiked = { navController.navigate(LibraryListRoute(LibrarySection.Liked.name)) }) }
                    composable<PlaylistRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn) }
                    composable<ChannelRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn) }
                    composable<AccountRoute> { AccountScreen(onSignIn = signIn, onBack = { navController.popBackStack() }) }
                    composable<SettingsRoute> { SettingsScreen(onAccount = { navController.navigate(AccountRoute) }, onBack = { navController.popBackStack() }) }
                    composable<PlayerRoute> {
                        PlayerScreen(
                            navigation,
                            onBack = { navController.popBackStack() },
                            onSignIn = signIn,
                            onAdvanced = { next -> navController.navigate(PlayerRoute(next)) { popUpTo<PlayerRoute> { inclusive = true } } },
                        )
                    }
                }
                if (dockVisible && (!online || expired)) StatusBanner(online, expired,
                    onDownloads = { openSection(DownloadsRoute) }, onSignIn = signIn)
                val nowPlaying = playerState.nowPlaying
                if (miniVisible && nowPlaying != null) {
                    MiniPlayer(player = viewModel.player.player, nowPlaying = nowPlaying, left = leftHanded,
                        onOpen = { navController.navigate(PlayerRoute(nowPlaying.videoId)) }, onClose = viewModel.player::stop)
                }
            }
            if (mineOpen && dockVisible) MineList(downloads, onPick = { item ->
                mineOpen = false
                openSection(when (item) {
                    MineItem.WatchLater -> LibraryListRoute(LibrarySection.WatchLater.name)
                    MineItem.Downloads -> DownloadsRoute
                    MineItem.History -> LibraryListRoute(LibrarySection.History.name)
                    MineItem.Playlists -> LibraryListRoute(LibrarySection.Playlists.name)
                    MineItem.Settings -> SettingsRoute
                })
            }, onDismiss = { mineOpen = false })
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).padding(bottom = barSpace.height + 8.dp)
                .then(if (onPlayer) Modifier.navigationBarsPadding() else Modifier))
        }
        if (dockVisible) AppDock(selected = currentTab, mineOpen = mineOpen, left = leftHanded, shorts = shortsEnabled, onSelect = { tab ->
            if (tab == DockTab.Mine) mineOpen = !mineOpen
            else {
                mineOpen = false
                openSection(when (tab) {
                    DockTab.Home -> HomeRoute
                    DockTab.Subscriptions -> SubscriptionsRoute
                    DockTab.Shorts -> ShortsRoute
                    else -> SearchRoute
                })
            }
        })
    }
    }
    }
}

/** Offline and expired-session notices, low on the screen with their action under the thumb. */
@Composable
private fun StatusBanner(online: Boolean, expired: Boolean, onDownloads: () -> Unit, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (!online) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.offline_banner), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDownloads, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_downloads)) }
        }
        if (expired) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.error_session_expired), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSignIn, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.welcome_sign_in)) }
        }
    }
}

/** Which dock button a screen belongs to; null keeps the previous one (channel, playlist, player). */
private fun NavDestination.dockTab(): DockTab? = when {
    hasRoute<HomeRoute>() -> DockTab.Home
    hasRoute<SubscriptionsRoute>() -> DockTab.Subscriptions
    hasRoute<ShortsRoute>() -> DockTab.Shorts
    hasRoute<SearchRoute>() -> DockTab.Search
    hasRoute<LibraryListRoute>() || hasRoute<DownloadsRoute>() || hasRoute<SettingsRoute>() || hasRoute<AccountRoute>() -> DockTab.Mine
    else -> null
}

private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo<HomeRoute> { saveState = false }
        launchSingleTop = true
        // An explicit section choice must not restore an old watch/channel child above it.
        // Home remains in the stack, preserving its loaded feed.
        restoreState = false
    }
}
