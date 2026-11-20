package app.freshtuber.ui

import kotlinx.coroutines.flow.first

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import app.freshtuber.ui.components.Reach
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import app.freshtuber.ui.motion.Motion
import app.freshtuber.ui.motion.leaving
import app.freshtuber.ui.motion.settled
import app.freshtuber.ui.motion.transitionBlur
import app.freshtuber.ui.motion.blurVisible
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
fun AppRoot(inPictureInPicture: Boolean, viewModel: MainViewModel = hiltViewModel(), openVideo: String? = null, onVideoOpened: () -> Unit = {},
    openMusic: Boolean = false, onMusicOpened: () -> Unit = {}) {
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
            if (!sameVideo) navController.openVideoPage(openVideo)
            onVideoOpened()
        }
    }
    // «Открывать музыку при запуске»: once per launch, and never over a video or song that was asked for.
    var startChosen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startChosen) return@LaunchedEffect
        startChosen = true
        if (start == HomeRoute && openVideo == null && !openMusic && viewModel.musicInApp.value && viewModel.startsWithMusic()) {
            navController.currentBackStackEntryFlow.first()
            navController.navigateToTopLevel(MusicListenRoute)
        }
    }
    val music = viewModel.music
    val musicState by music.state.collectAsStateWithLifecycle()
    val musicTabSetting by viewModel.musicTab.collectAsStateWithLifecycle()
    val musicInApp by viewModel.musicInApp.collectAsStateWithLifecycle()
    val musicEarSetting by viewModel.musicEar.collectAsStateWithLifecycle()
    val musicTab = musicTabSetting && musicInApp
    LaunchedEffect(openMusic) {
        if (openMusic && musicInApp) {
            // The graph must be in place, and a restored queue loaded, before navigating.
            navController.currentBackStackEntryFlow.first()
            kotlinx.coroutines.withTimeoutOrNull(4_000) { music.state.first { it.active } }
            navController.navigate(if (music.state.value.active) MusicPlayerRoute else MusicListenRoute) { launchSingleTop = true }
        }
        if (openMusic) onMusicOpened()
    }
    val leftHanded by viewModel.leftHanded.collectAsStateWithLifecycle()
    val classic = app.freshtuber.ui.components.LocalClassic.current
    val shortsEnabled by viewModel.shortsEnabled.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val expired by viewModel.expiredSession.collectAsStateWithLifecycle()
    val downloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onMusicPlayer = destination?.hasRoute<MusicPlayerRoute>() == true
    val inMusic = destination?.let { it.hasRoute<MusicListenRoute>() || it.hasRoute<MusicLibraryRoute>() } == true
    val onPlayer = destination?.hasRoute<PlayerRoute>() == true || onMusicPlayer
    val inFlow = destination == null || destination.hasRoute<WelcomeRoute>() || destination.hasRoute<LoginRoute>()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val dockVisible = !inPictureInPicture && !onPlayer && !inFlow && !keyboardVisible
    val onShorts = destination?.hasRoute<ShortsRoute>() == true || destination?.hasRoute<ShortsViewerRoute>() == true
    val miniVisible = dockVisible && !onShorts && playerState.nowPlaying?.isShort == false
    DisposableEffect(miniVisible) {
        if (miniVisible) viewModel.player.setVideoScreen(true)
        onDispose { if (miniVisible) viewModel.player.setVideoScreen(false) }
    }
    val handoff = remember { app.freshtuber.ui.player.VideoHandoff() }
    // Read by the watch page when it leaves: the video then flies into the floating window.
    handoff.miniExpected = miniVisible

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
            override fun openVideo(videoId: String) = navController.openVideoPage(videoId)
            override fun openPlaylist(playlistId: String, title: String, seedVideoId: String?) =
                navController.navigate(PlaylistRoute(playlistId, title, seedVideoId))
            override fun openChannel(channelId: String) = navController.navigate(ChannelRoute(channelId))
            override fun openShorts(videoIds: List<String>, thumbnails: List<String>, start: Int) =
                navController.navigate(ShortsViewerRoute(videoIds, thumbnails, start))
        }
    }
    val openSection: (Any) -> Unit = { route -> navController.navigateToTopLevel(route) }
    var homeScrollPending by remember { mutableStateOf(false) }
    // The «Музыка» ear above the dock leaves while Home is read down and returns on the way up.
    val earHide = app.freshtuber.ui.components.rememberScrollHide()
    // Search by source: songs or videos, switched in the search field's bar.
    var searchMusic by rememberSaveable { mutableStateOf(false) }
    val searchSwitch: @Composable () -> Unit = { app.freshtuber.ui.music.SearchSourceSwitch(searchMusic, leftHanded) { searchMusic = it } }
    val musicNavigation = remember(navController, music) {
        object : app.freshtuber.ui.music.MusicNavigation {
            override fun openPlayer() = navController.navigate(MusicPlayerRoute) { launchSingleTop = true }
            override fun openCollection(collection: String) = navController.navigate(MusicLibraryRoute(collection))
            override fun openMusicHome() = navController.navigateToTopLevel(MusicListenRoute)
            override fun watchVideo(track: app.freshtuber.data.music.MusicTrack) {
                val position = music.player.currentPosition.takeIf { music.player.currentMediaItem?.mediaId == track.id } ?: 0L
                music.pause()
                viewModel.player.startNextAt(track.videoId, position)
                navController.navigate(PlayerRoute(track.videoId))
            }
        }
    }
    // «Слушать» on a video: the song with radio after it.
    val listen: (app.freshtuber.domain.model.VideoSummary) -> Unit = remember(music, navController) { { video ->
        music.playRadio(app.freshtuber.data.music.MusicTrack.youtube(video))
        navController.navigate(MusicPlayerRoute) { launchSingleTop = true }
    } }
    // «Как музыку» on the watch page: the same song goes on as audio from the same second.
    val asMusic: () -> Unit = remember(music, navController, viewModel) { {
        viewModel.player.state.value.nowPlaying?.takeIf { !it.isLive }?.let { now ->
            val track = app.freshtuber.data.music.MusicTrack.youtube(app.freshtuber.domain.model.VideoSummary(now.videoId, now.title,
                channelName = now.channelName, thumbnailUrl = now.thumbnailUrl, durationSeconds = now.durationSeconds))
            music.takeOverFromVideo(track, null)
            navController.navigate(MusicPlayerRoute) { popUpTo<PlayerRoute> { inclusive = true }; launchSingleTop = true }
        }
        Unit
    } }
    val selectTab: (DockTab) -> Unit = { tab ->
        if (tab == DockTab.Mine) mineOpen = !mineOpen
        else if (tab == DockTab.Music) { mineOpen = false; openSection(MusicListenRoute) }
        else {
            mineOpen = false
            if (tab == DockTab.Home) homeScrollPending = true
            if (tab != DockTab.Home || destination?.hasRoute<HomeRoute>() != true) openSection(when (tab) {
                DockTab.Home -> HomeRoute
                DockTab.Subscriptions -> SubscriptionsRoute
                DockTab.Shorts -> ShortsRoute
                else -> SearchRoute
            })
        }
    }

    val activity = androidx.activity.compose.LocalActivity.current
    val lightBars = MaterialTheme.colorScheme.surface.let { it.red + it.green + it.blue > 1.5f } && !onPlayer && !onShorts
    // The status bar icons change halfway through a screen change, when the screens cross.
    var barsLight by remember { mutableStateOf(lightBars) }
    LaunchedEffect(lightBars) { kotlinx.coroutines.delay(150); barsLight = lightBars }
    SideEffect {
        activity?.window?.let { window -> androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = barsLight
            isAppearanceLightNavigationBars = barsLight
        } }
    }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember(snackbarHost, scope) { AppMessages(snackbarHost, scope) }
    val barSpace = remember { BottomBarSpace() }
    val fanHost = remember { app.freshtuber.ui.feed.FanHost() }
    val flickHost = remember { app.freshtuber.ui.music.FlickHost() }
    // Music notes: stopped from the dock («Вернуть»), skipped songs, a song that failed.
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(music, messages) {
        music.events.collect { event ->
            when (event) {
                app.freshtuber.player.music.MusicEvent.Stopped -> messages.show(resources.getString(R.string.music_stopped), resources.getString(R.string.action_return)) { undo ->
                    if (undo) music.undoStop()
                }
                is app.freshtuber.player.music.MusicEvent.Skipped -> messages.show(resources.getQuantityString(R.plurals.music_skipped, event.count, event.count))
                app.freshtuber.player.music.MusicEvent.Failed -> messages.show(resources.getString(R.string.music_failed))
            }
        }
    }
    // Taking music out of the app also stops what plays and leaves its screens.
    LaunchedEffect(musicInApp) { if (!musicInApp && music.state.value.active) music.stop() }
    LaunchedEffect(musicInApp, inMusic, onMusicPlayer) {
        if (!musicInApp && (inMusic || onMusicPlayer)) navController.navigateToTopLevel(HomeRoute)
    }
    val warmVideo: (String) -> Unit = remember(viewModel) { { id -> viewModel.player.warmShort(id) } }

    CompositionLocalProvider(LocalInPictureInPicture provides inPictureInPicture, LocalAppMessages provides messages,
        LocalBottomBarSpace provides barSpace, app.freshtuber.ui.components.LocalLeftHanded provides leftHanded, app.freshtuber.ui.feed.LocalFanHost provides fanHost, app.freshtuber.ui.feed.LocalFeedBottomClearance provides 16.dp,
        app.freshtuber.ui.player.LocalVideoHandoff provides handoff, app.freshtuber.ui.player.LocalWarmVideo provides warmVideo,
        app.freshtuber.ui.music.LocalMusicNavigation provides musicNavigation, app.freshtuber.ui.music.LocalFlickHost provides flickHost,
        app.freshtuber.ui.feed.LocalListen provides listen, app.freshtuber.ui.screens.player.LocalAsMusic provides asMusic) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    // Shorts decide themselves: a full-screen video covers the light, a square one is lit around.
    SideEffect { light?.setRouteCovered(inPictureInPicture) }
    SideEffect { if (!onMusicPlayer) light?.musicMode = inMusic }
    // «Моё» is a frosted pane: the screen behind it blurs while it is open (Android 12+).
    val behindBlur by androidx.compose.animation.core.animateDpAsState(if (fanHost.session != null) 26.dp else if (flickHost.center != null || (mineOpen && dockVisible)) 18.dp else 0.dp,
        androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "mineBlur")
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val compactDockSpace = Reach.CompactDockHeight + navBottom
    // The dock floats over the screens; each screen keeps room for it, whatever the next screen
    // is, so nothing jumps while two screens cross-fade.
    // While music is on, its song line sits in the dock drop above the buttons.
    val musicLine = musicInApp && musicState.active && !onShorts
    val dockSpace = (if (classic) compactDockSpace else Reach.DockHeight + Reach.DockTopGap + app.freshtuber.ui.components.dockBottomSpace()) + if (musicLine) app.freshtuber.ui.music.MusicLineHeight else 0.dp
    // The «Музыка» ear stands on top of Home's bottom chip row (right above the dock when Home
    // has no chips). It folds away with the chips when scrolling hides them, so the feed reaches
    // the dock. The classic layout keeps it in the dock column.
    // While a song plays its line is already in the dock, and with «Музыка» in the dock instead of
    // Shorts the dock has its own button, so the ear is not needed then.
    // The ear's own switch in the music settings hides it everywhere.
    val homeEar: (@Composable () -> Unit)? = if (!musicInApp || !musicEarSetting || classic || musicState.active || musicTab) null else ({
        app.freshtuber.ui.motion.BlurVisibility(earHide.visible && !mineOpen, collapse = true) {
            app.freshtuber.ui.music.MusicEar(true, leftHanded, onClick = { openSection(MusicListenRoute) })
        }
    })
    var bannerHeight by remember { mutableStateOf(0.dp) }
    val screenBottom by androidx.compose.animation.core.animateDpAsState(
        if (keyboardVisible) 0.dp else dockSpace + bannerHeight, label = "screenBottom")
    val fullDock = dockVisible && !onShorts
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
    if (light != null) app.freshtuber.ui.liquid.LiquidBackground(light)
    Box(Modifier.fillMaxSize()
        .then(if (onPlayer || inPictureInPicture) Modifier else Modifier.windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)))) {
        val docked = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = if (inPictureInPicture) 0.dp else screenBottom)
        val flow = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime))
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize().then(if (behindBlur > 0.dp) Modifier.blur(behindBlur) else Modifier),
            enterTransition = { ScreenMotion.enter(initialState, targetState) },
            exitTransition = { ScreenMotion.exit(initialState, targetState) },
            popEnterTransition = { ScreenMotion.popEnter(initialState, targetState) },
            popExitTransition = { ScreenMotion.popExit(initialState, targetState) },
        ) {
            composable<WelcomeRoute> {
                Box(flow.then(transitionBlur())) {
                    WelcomeScreen(
                        onSignIn = { navController.navigate(LoginRoute) },
                        onSkip = {
                            viewModel.continueWithoutAccount()
                            navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                        },
                    )
                }
            }
            composable<LoginRoute> {
                Box(flow.then(transitionBlur())) {
                    LoginScreen(
                        onClose = { navController.popBackStack() },
                        onDone = {
                            val cameFromWelcome = navController.previousBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true
                            if (cameFromWelcome) navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                            else navController.popBackStack()
                        },
                    )
                }
            }
            composable<HomeRoute> { Box(docked.then(transitionBlur()).nestedScroll(earHide.connection)) { ClassicTop(classic, { openSection(SearchRoute) }, { openSection(SettingsRoute) }) { HomeScreen(navigation, onSignIn = signIn, scrollToTop = homeScrollPending, onScrolledToTop = { homeScrollPending = false }, ear = homeEar) } } }
            composable<ShortsRoute> { Box(Modifier.fillMaxSize().then(transitionBlur())) { ShortsScreen(navigation, onSignIn = signIn, settled = settled) } }
            composable<ShortsViewerRoute> { Box(Modifier.fillMaxSize().then(transitionBlur())) { ShortsScreen(navigation, onSignIn = signIn, settled = settled) } }
            composable<SubscriptionsRoute> { Box(docked.then(transitionBlur())) { ClassicTop(classic, { openSection(SearchRoute) }, { openSection(SettingsRoute) }) {
                SubscriptionsScreen(navigation, onSignIn = signIn, onSearch = { openSection(SearchRoute) }) } } }
            composable<SearchRoute> { Box(docked.then(transitionBlur())) {
                if (searchMusic && musicInApp) app.freshtuber.ui.music.MusicSearchScreen(sourceSwitch = searchSwitch)
                else SearchScreen(navigation, onSignIn = signIn, sourceSwitch = if (musicInApp) searchSwitch else null)
            } }
            composable<DownloadsRoute> { Box(docked.then(transitionBlur())) { DownloadsScreen(onVideo = { navController.navigate(PlayerRoute(it)) }, onSearch = { openSection(SearchRoute) }) } }
            composable<LibraryListRoute> { Box(docked.then(transitionBlur())) { ListScreen(navigation, onSignIn = signIn,
                onLiked = { navController.navigate(LibraryListRoute(LibrarySection.Liked.name)) }) } }
            composable<PlaylistRoute> { Box(docked.then(transitionBlur())) { ListScreen(navigation, onSignIn = signIn) } }
            composable<ChannelRoute> { Box(docked.then(transitionBlur())) { ListScreen(navigation, onSignIn = signIn) } }
            composable<AccountRoute> { Box(docked.then(transitionBlur())) { AccountScreen(onSignIn = signIn) } }
            composable<SettingsRoute> { Box(docked.then(transitionBlur())) { SettingsScreen() } }
            composable<MusicListenRoute> { Box(docked.then(transitionBlur())) { app.freshtuber.ui.music.MusicListenScreen() } }
            composable<MusicLibraryRoute> { entry -> Box(docked.then(transitionBlur())) { app.freshtuber.ui.music.MusicLibraryScreen(entry.toRoute<MusicLibraryRoute>().collection) } }
            composable<MusicPlayerRoute> { entry -> Box(Modifier.fillMaxSize().then(transitionBlur(28.dp))) {
                // Only while this page is on top: a pull that ends after the arrow was tapped must not close a second page.
                app.freshtuber.ui.music.MusicPlayerScreen(onCollapse = {
                    if (navController.currentBackStackEntry?.id == entry.id && !navController.popBackStack()) navController.navigate(MusicListenRoute)
                })
            } }
            composable<PlayerRoute> { entry ->
                Box(Modifier.fillMaxSize().then(transitionBlur(28.dp))) {
                    PlayerScreen(
                        navigation,
                        onBack = { navController.popBackStack() },
                        onSignIn = signIn,
                        onAdvanced = { next, keepFull ->
                            if (navController.currentBackStackEntry?.id == entry.id) {
                                navController.navigate(PlayerRoute(next, fullscreen = keepFull)) {
                                    popUpTo<PlayerRoute> { inclusive = true }
                                }
                            }
                        },
                        settled = settled,
                        currentPage = backStackEntry?.id == entry.id,
                        departureCommitted = leaving && backStackEntry?.id != entry.id,
                    )
                }
            }
        }
        // Offline / expired notices sit right above the dock.
        if (dockVisible && (!online || expired)) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (fullDock) dockSpace else compactDockSpace)
            .onSizeChanged { bannerHeight = with(density) { it.height.toDp() } }) {
            StatusBanner(online, expired, onDownloads = { openSection(DownloadsRoute) }, onSignIn = signIn)
        } else if (bannerHeight != 0.dp) SideEffect { bannerHeight = 0.dp }
        // A newer build on GitHub: a banner above the dock, or its corner chip after «Позже».
        val update by viewModel.updates.available.collectAsStateWithLifecycle()
        val updateCollapsed by viewModel.updates.collapsed.collectAsStateWithLifecycle()
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        update?.takeIf { dockVisible }?.let { available ->
            app.freshtuber.ui.components.UpdateBanner(available, updateCollapsed, leftHanded,
                onUpdate = { runCatching { uriHandler.openUri(available.url) } }, onCollapse = viewModel.updates::collapse,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bannerHeight + if (fullDock) dockSpace else compactDockSpace))
        }
        val belowContent = bannerHeight + if (fullDock) dockSpace else 0.dp
        val nowPlaying = playerState.nowPlaying
        // Closing the floating video fades it out; it keeps its last picture meanwhile.
        val lastPlaying = remember { arrayOfNulls<app.freshtuber.player.NowPlaying>(1) }
        if (nowPlaying != null && !nowPlaying.isShort) lastPlaying[0] = nowPlaying
        // Dragged, the floating video and its close cross float above the dock.
        var miniRaised by remember { mutableStateOf(false) }
        androidx.compose.animation.AnimatedVisibility(miniVisible && nowPlaying != null,
            Modifier.zIndex(if (miniRaised) 1f else 0f),
            enter = EnterTransition.None,
            exit = if (nowPlaying == null) app.freshtuber.ui.motion.blurOut(240, 0.9f) else ExitTransition.None) {
            val playing = lastPlaying[0] ?: return@AnimatedVisibility
            // Closing it blurs the floating video away (its picture is a texture, so it blurs too).
            Box(if (nowPlaying == null) blurVisible(24.dp, exitMs = 240) else Modifier) {
            FloatingMiniPlayer(player = viewModel.player.player, nowPlaying = playing, left = leftHanded,
                bottomClearance = barSpace.height + belowContent, corner = miniCorner, onCorner = { miniCorner = it },
                widthDp = miniWidth, onWidth = { miniWidth = it },
                hasNext = playerState.queue.isNotEmpty(), hasPrevious = playerState.history.isNotEmpty(),
                onNext = viewModel.player::playNext, onPrevious = viewModel.player::playPrevious,
                onOpen = { navController.navigate(PlayerRoute(playing.videoId)) }, onClose = viewModel.player::stop,
                tapOpens = app.freshtuber.ui.theme.LocalAppPreferences.current.enabled(app.freshtuber.data.settings.AppFeature.MiniPlayerTapOpens),
                hidden = mineOpen, onRaised = { miniRaised = it },
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
            }
        }
        // «Моё» blurs away when it closes, as it came.
        app.freshtuber.ui.motion.BlurVisibility(mineOpen && dockVisible, max = 18.dp, enterMs = 1, exitMs = Motion.EXIT_MS, fromScale = 1f) { MineList(downloads, bottomInset = if (onShorts) compactDockSpace else dockSpace,
            showShorts = musicTab && shortsEnabled,
            accountName = (viewModel.accountState.collectAsStateWithLifecycle().value as? app.freshtuber.data.account.AccountState.SignedIn)
                ?.active?.let { it.name ?: it.email }, onPick = { item ->
            mineOpen = false
            openSection(when (item) {
                MineItem.WatchLater -> LibraryListRoute(LibrarySection.WatchLater.name)
                MineItem.Shorts -> ShortsRoute
                MineItem.Downloads -> DownloadsRoute
                MineItem.History -> LibraryListRoute(LibrarySection.History.name)
                MineItem.Playlists -> LibraryListRoute(LibrarySection.Playlists.name)
                MineItem.Settings -> SettingsRoute
                MineItem.Account -> AccountRoute
            })
        }, onDismiss = { mineOpen = false }) }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter)
            .padding(bottom = if (onMusicPlayer) 250.dp else barSpace.height + belowContent + 8.dp + if (onShorts && dockVisible) compactDockSpace else 0.dp)
            .then(if (onPlayer) Modifier.navigationBarsPadding() else Modifier))
        // The dock blurs away under the player and comes back into focus when it closes.
        app.freshtuber.ui.motion.BlurVisibility(fullDock, Modifier.align(Alignment.BottomCenter),
            max = 20.dp, enterMs = Motion.ENTER_MS, exitMs = Motion.EXIT_MS, fromScale = 0.96f) {
            val line: (@Composable () -> Unit)? = if (!musicLine) null else ({
                app.freshtuber.ui.music.MusicLine(musicState, music.player, offline = !online,
                    onOpen = { navController.navigate(MusicPlayerRoute) { launchSingleTop = true } }, onToggle = music::toggle,
                    onNext = music::next, onPrevious = music::previous, onStop = music::stop)
            })
            // The ear belongs to Home alone; outside the classic layout Home draws it above its chips.
            val ear: (@Composable () -> Unit)? = if (!musicInApp || !musicEarSetting || !classic || musicState.active || musicTab || destination?.hasRoute<HomeRoute>() != true) null else ({
                app.freshtuber.ui.music.MusicEar(earHide.visible && !mineOpen, leftHanded, onClick = { openSection(MusicListenRoute) })
            })
            if (classic) Column {
                // YouTube's own layout: the song line is a plain strip right above the tabs.
                ear?.invoke()
                if (line != null) androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) { line() }
                app.freshtuber.ui.classic.ClassicNavBar(if (inMusic) null else currentTab, mineOpen, shortsEnabled, overVideo = false, onSelect = selectTab)
            }
            else AppDock(selected = if (inMusic) null else currentTab, mineOpen = mineOpen, left = leftHanded, shorts = shortsEnabled, onSelect = selectTab, music = musicTab, line = line, ear = ear)
        }
    }
    // Over a Short the dock is a slim row of icons drawn on the video itself.
    app.freshtuber.ui.motion.BlurVisibility(dockVisible && onShorts, Modifier.align(Alignment.BottomCenter),
        max = 20.dp, enterMs = Motion.ENTER_MS, exitMs = Motion.EXIT_MS, fromScale = 0.96f) {
        if (classic) app.freshtuber.ui.classic.ClassicNavBar(currentTab, mineOpen, shortsEnabled, overVideo = true, onSelect = selectTab)
        else CompactDock(selected = currentTab, mineOpen = mineOpen, left = leftHanded, shorts = shortsEnabled, onSelect = selectTab, music = musicTab)
    }
    // Home's floating video needs a full-window surface after the system takes over.
    if (inPictureInPicture && !onPlayer && !onShorts) app.freshtuber.ui.player.VideoSurface(
        viewModel.player.player, Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
    app.freshtuber.ui.feed.FanOverlay(fanHost)
    app.freshtuber.ui.music.FlickOverlay(flickHost)
    app.freshtuber.ui.player.VideoFlightOverlay(handoff)
    }
    }
    }
}

/**
 * How screens replace each other. Everything cross-fades through a soft blur (applied by each
 * screen); the watch page rises over the feed, which recedes, and sinks back when it closes.
 * Dock sections swap quickly in place; deeper screens come forward and step back.
 */
private object ScreenMotion {
    private fun NavBackStackEntry.isPlayer() = destination.hasRoute<PlayerRoute>() || destination.hasRoute<MusicPlayerRoute>()
    private fun NavBackStackEntry.isSection() = destination.let {
        it.hasRoute<HomeRoute>() || it.hasRoute<SubscriptionsRoute>() || it.hasRoute<ShortsRoute>() || it.hasRoute<SearchRoute>() ||
            it.hasRoute<DownloadsRoute>() || it.hasRoute<LibraryListRoute>() || it.hasRoute<SettingsRoute>() ||
            it.hasRoute<MusicListenRoute>() || it.hasRoute<MusicLibraryRoute>()
    }
    private fun <T> enterSpec(delay: Int = 0) = tween<T>(Motion.ENTER_MS, delay, Motion.Decelerate)
    private fun <T> exitSpec() = tween<T>(Motion.EXIT_MS, easing = Motion.Accelerate)

    fun enter(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
        to.isPlayer() -> fadeIn(enterSpec(40)) + slideInVertically(enterSpec()) { (it * 0.06f).toInt() }
        from.isSection() && to.isSection() -> fadeIn(tween(300, 70, Motion.Decelerate)) + scaleIn(tween(360, easing = Motion.Decelerate), 0.985f)
        else -> fadeIn(enterSpec(40)) + scaleIn(enterSpec(), 0.95f)
    }

    fun exit(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
        // A page pulled down that has nothing to go back to is replaced: it only finishes its fade too.
        from.isPlayer() && app.freshtuber.ui.player.MinimizeDrag.recentlyDismissed() -> ExitTransition.None
        to.isPlayer() -> fadeOut(exitSpec()) + scaleOut(enterSpec(), 0.94f)
        from.isSection() && to.isSection() -> fadeOut(tween(170, easing = Motion.Accelerate))
        else -> fadeOut(exitSpec()) + scaleOut(enterSpec(), 1.04f)
    }

    fun popEnter(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
        // After a pull-down the page has already blurred away: what was under it comes into focus at once.
        from.isPlayer() && app.freshtuber.ui.player.MinimizeDrag.recentlyDismissed() ->
            fadeIn(tween(280, easing = Motion.Decelerate)) + scaleIn(tween(320, easing = Motion.Decelerate), 0.96f)
        from.isPlayer() -> fadeIn(enterSpec(60)) + scaleIn(enterSpec(), 0.94f)
        else -> fadeIn(enterSpec(40)) + scaleIn(enterSpec(), 1.04f)
    }

    fun popExit(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
        // Pulled down by the finger, the page has already blurred away: no second exit on top.
        from.isPlayer() && app.freshtuber.ui.player.MinimizeDrag.recentlyDismissed() -> ExitTransition.None
        from.isPlayer() -> fadeOut(exitSpec()) + slideOutVertically(tween(Motion.EXIT_MS + 60, easing = Motion.Accelerate)) { (it * 0.08f).toInt() }
        else -> fadeOut(exitSpec()) + scaleOut(exitSpec(), 0.95f)
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
    hasRoute<ShortsViewerRoute>() -> null
    hasRoute<SearchRoute>() -> DockTab.Search
    hasRoute<LibraryListRoute>() || hasRoute<DownloadsRoute>() || hasRoute<SettingsRoute>() || hasRoute<AccountRoute>() -> DockTab.Mine
    hasRoute<MusicListenRoute>() || hasRoute<MusicLibraryRoute>() -> DockTab.Music
    else -> null
}

/**
 * Opens a video. From a watch page the new video takes that page's place, so Back always leaves
 * the player for the screen it was opened from; the earlier video stays one tap away on «Previous».
 */
private fun NavHostController.openVideoPage(videoId: String) {
    val onPlayer = currentBackStackEntry?.destination?.hasRoute<PlayerRoute>() == true
    navigate(PlayerRoute(videoId)) { if (onPlayer) popUpTo<PlayerRoute> { inclusive = true } }
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

/** In the classic layout Home and Subscriptions sit under YouTube's top bar. */
@Composable
private fun ClassicTop(classic: Boolean, onSearch: () -> Unit, onSettings: () -> Unit, content: @Composable () -> Unit) {
    if (!classic) { content(); return }
    Column(Modifier.fillMaxSize()) {
        app.freshtuber.ui.classic.ClassicTopBar(onSearch, onSettings)
        Box(Modifier.weight(1f)) { content() }
    }
}
