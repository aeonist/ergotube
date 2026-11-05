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
import androidx.compose.ui.draw.blur
import app.freshtuber.ui.components.Reach
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
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
import app.freshtuber.ui.player.FloatingMiniPlayer
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
    val onShorts = destination?.hasRoute<ShortsRoute>() == true
    val miniVisible = dockVisible && !onShorts && playerState.nowPlaying?.isShort == false

    var mineOpen by rememberSaveable { mutableStateOf(false) }
    // The floating video remembers its corner while it is hidden on the watch page.
    var miniCorner by rememberSaveable(leftHanded) { mutableIntStateOf(app.freshtuber.ui.player.MiniCorners.default(leftHanded)) }
    // Its width in dp; pinching or dragging the grip changes it, like YouTube's floating window.
    var miniWidth by rememberSaveable { mutableFloatStateOf(0f) }
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
    val selectTab: (DockTab) -> Unit = { tab ->
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
    }

    val activity = androidx.activity.compose.LocalActivity.current
    val lightBars = MaterialTheme.colorScheme.surface.let { it.red + it.green + it.blue > 1.5f } && !onPlayer && !onShorts
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
    val fanHost = remember { app.freshtuber.ui.feed.FanHost() }
    var belowContent by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalInPictureInPicture provides inPictureInPicture, LocalAppMessages provides messages,
        LocalBottomBarSpace provides barSpace, app.freshtuber.ui.components.LocalLeftHanded provides leftHanded, app.freshtuber.ui.feed.LocalFanHost provides fanHost, app.freshtuber.ui.feed.LocalFeedBottomClearance provides 16.dp) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    // Shorts decide themselves: a full-screen video covers the light, a square one is lit around.
    SideEffect { light?.setRouteCovered(inPictureInPicture) }
    // «Моё» is a frosted pane: the screen behind it blurs while it is open (Android 12+).
    val behindBlur by androidx.compose.animation.core.animateDpAsState(if (mineOpen && dockVisible) 18.dp else 0.dp, label = "mineBlur")
    val compactDockSpace = Reach.CompactDockHeight + with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
    if (light != null) app.freshtuber.ui.liquid.LiquidBackground(light)
    Column(Modifier.fillMaxSize()
        .then(if (onPlayer || inPictureInPicture) Modifier else Modifier.windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = start,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(if (behindBlur > 0.dp) Modifier.blur(behindBlur) else Modifier).then(when {
                        onPlayer || inPictureInPicture || onShorts -> Modifier
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
                    composable<DownloadsRoute> { DownloadsScreen(onVideo = { navController.navigate(PlayerRoute(it)) }, onSearch = { openSection(SearchRoute) }) }
                    composable<LibraryListRoute> { ListScreen(navigation, onSignIn = signIn,
                        onLiked = { navController.navigate(LibraryListRoute(LibrarySection.Liked.name)) }) }
                    composable<PlaylistRoute> { ListScreen(navigation, onSignIn = signIn) }
                    composable<ChannelRoute> { ListScreen(navigation, onSignIn = signIn) }
                    composable<AccountRoute> { AccountScreen(onSignIn = signIn) }
                    composable<SettingsRoute> { SettingsScreen(onAccount = { navController.navigate(AccountRoute) }) }
                    composable<PlayerRoute> {
                        PlayerScreen(
                            navigation,
                            onBack = { navController.popBackStack() },
                            onSignIn = signIn,
                            onAdvanced = { next -> navController.navigate(PlayerRoute(next)) { popUpTo<PlayerRoute> { inclusive = true } } },
                        )
                    }
                }
                val density = LocalDensity.current
                Column(Modifier.fillMaxWidth().onSizeChanged { belowContent = with(density) { it.height.toDp() } }) {
                    if (dockVisible && (!online || expired)) StatusBanner(online, expired,
                        onDownloads = { openSection(DownloadsRoute) }, onSignIn = signIn)
                }
            }
            val nowPlaying = playerState.nowPlaying
            if (miniVisible && nowPlaying != null) {
                FloatingMiniPlayer(player = viewModel.player.player, nowPlaying = nowPlaying, left = leftHanded,
                    bottomClearance = barSpace.height + belowContent, corner = miniCorner, onCorner = { miniCorner = it },
                    widthDp = miniWidth, onWidth = { miniWidth = it },
                    hasNext = playerState.queue.isNotEmpty(), hasPrevious = playerState.history.isNotEmpty(),
                    onNext = viewModel.player::playNext, onPrevious = viewModel.player::playPrevious,
                    onOpen = { navController.navigate(PlayerRoute(nowPlaying.videoId)) }, onClose = viewModel.player::stop,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
            }
            if (mineOpen && dockVisible) MineList(downloads, bottomInset = if (onShorts) compactDockSpace else 0.dp, onPick = { item ->
                mineOpen = false
                openSection(when (item) {
                    MineItem.WatchLater -> LibraryListRoute(LibrarySection.WatchLater.name)
                    MineItem.Downloads -> DownloadsRoute
                    MineItem.History -> LibraryListRoute(LibrarySection.History.name)
                    MineItem.Playlists -> LibraryListRoute(LibrarySection.Playlists.name)
                    MineItem.Settings -> SettingsRoute
                })
            }, onDismiss = { mineOpen = false })
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barSpace.height + belowContent + 8.dp + if (onShorts && dockVisible) compactDockSpace else 0.dp)
                .then(if (onPlayer) Modifier.navigationBarsPadding() else Modifier))
        }
        if (dockVisible && !onShorts) AppDock(selected = currentTab, mineOpen = mineOpen, left = leftHanded, shorts = shortsEnabled, onSelect = selectTab)
    }
    // Over a Short the dock is a slim row of icons drawn on the video itself.
    if (dockVisible && onShorts) CompactDock(selected = currentTab, mineOpen = mineOpen, left = leftHanded, shorts = shortsEnabled,
        onSelect = selectTab, modifier = Modifier.align(Alignment.BottomCenter))
    app.freshtuber.ui.feed.FanOverlay(fanHost)
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
