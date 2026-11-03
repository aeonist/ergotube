package app.freshtuber.ui

import androidx.compose.foundation.layout.*

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.TouchApp
import app.freshtuber.R
import app.freshtuber.data.account.AccountState
import app.freshtuber.ui.radial.LocalRadialFilters
import app.freshtuber.ui.radial.RadialFilters
import app.freshtuber.ui.radial.RadialItem
import app.freshtuber.ui.radial.RadialMenu
import app.freshtuber.ui.screens.library.LibrarySection
import app.freshtuber.ui.navigation.CalibrationRoute
import androidx.compose.foundation.layout.Column
import app.freshtuber.ui.player.MiniPlayer
import app.freshtuber.ui.player.LocalInPictureInPicture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.navigation.AccountRoute
import app.freshtuber.ui.navigation.ChannelRoute
import app.freshtuber.ui.navigation.LibraryListRoute
import app.freshtuber.ui.navigation.PlaylistRoute
import app.freshtuber.ui.navigation.HomeRoute
import app.freshtuber.ui.navigation.LibraryRoute
import app.freshtuber.ui.navigation.LoginRoute
import app.freshtuber.ui.navigation.PlayerRoute
import app.freshtuber.ui.navigation.SearchRoute
import app.freshtuber.ui.navigation.SettingsRoute
import app.freshtuber.ui.navigation.ShortsRoute
import app.freshtuber.ui.navigation.SubscriptionsRoute
import app.freshtuber.ui.navigation.TopLevelDestination
import app.freshtuber.ui.navigation.WelcomeRoute
import app.freshtuber.ui.screens.account.AccountScreen
import app.freshtuber.ui.screens.home.HomeScreen
import app.freshtuber.ui.screens.library.ListScreen
import app.freshtuber.ui.screens.library.LibraryScreen
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
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val navController = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(openVideo) {
        if (openVideo != null) {
            val current = navController.currentBackStackEntry
            val sameVideo = current?.destination?.hasRoute<PlayerRoute>() == true && current.toRoute<PlayerRoute>().videoId == openVideo
            if (!sameVideo) navController.navigate(PlayerRoute(openVideo))
            onVideoOpened()
        }
    }
    val leftHanded by viewModel.leftHanded.collectAsStateWithLifecycle()
    val thumbArc by viewModel.thumbArc.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val shortsEnabled by viewModel.shortsEnabled.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val expired by viewModel.expiredSession.collectAsStateWithLifecycle()
    val identity = (accountState as? AccountState.SignedIn)?.active
    val filters = remember(identity?.id, identity?.activePageId) { RadialFilters() }
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val fullScreen = inPictureInPicture || currentDestination?.let {
        it.hasRoute<PlayerRoute>() || it.hasRoute<WelcomeRoute>() || it.hasRoute<LoginRoute>() || it.hasRoute<CalibrationRoute>()
    } ?: true
    var playerMenuRequested by remember { mutableStateOf(false) }
    val signIn: () -> Unit = { navController.navigate(LoginRoute) }
    val navigation = remember(navController) {
        object : FeedNavigation {
            override fun openVideo(videoId: String) = navController.navigate(PlayerRoute(videoId))
            override fun openPlaylist(playlistId: String, title: String, seedVideoId: String?) =
                navController.navigate(PlaylistRoute(playlistId, title, seedVideoId))
            override fun openChannel(channelId: String) = navController.navigate(ChannelRoute(channelId))
        }
    }

    val activity = androidx.activity.compose.LocalActivity.current
    val lightBars = MaterialTheme.colorScheme.surface.let { it.red + it.green + it.blue > 1.5f } && currentDestination?.hasRoute<PlayerRoute>() != true
    androidx.compose.runtime.SideEffect {
        activity?.window?.let { window -> androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        } }
    }
    val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val miniVisible = !fullScreen && !keyboardVisible && currentDestination?.hasRoute<ShortsRoute>() != true && playerState.nowPlaying?.isShort == false
    CompositionLocalProvider(LocalInPictureInPicture provides inPictureInPicture, LocalRadialFilters provides filters,
        app.freshtuber.ui.feed.LocalFeedBottomClearance provides if (miniVisible) 160.dp else 88.dp) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if ((!online || expired) && !fullScreen) androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding().fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (!online) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text(stringResource(R.string.offline_banner), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = { navController.navigate(app.freshtuber.ui.navigation.DownloadsRoute) { launchSingleTop = true } }) { androidx.compose.material3.Text(stringResource(R.string.library_downloads)) }
                }
                if (expired) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text(stringResource(R.string.error_session_expired), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = signIn) { androidx.compose.material3.Text(stringResource(R.string.welcome_sign_in)) }
                }
            }
        },
        contentWindowInsets = if (currentDestination?.hasRoute<PlayerRoute>() == true || inPictureInPicture) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
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
                        val cameFromWelcome =
                            navController.previousBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true
                        if (cameFromWelcome) {
                            navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<HomeRoute> { HomeScreen(navigation, onSignIn = signIn, onAccount = { navController.navigate(AccountRoute) }) }
            composable<ShortsRoute> { ShortsScreen(navigation, onSignIn = signIn) }
            composable<SubscriptionsRoute> { SubscriptionsScreen(navigation, onSignIn = signIn, onSearch = { navController.navigateToTopLevel(TopLevelDestination.Search) }) }
            composable<SearchRoute> { SearchScreen(navigation, onSignIn = signIn) }
            composable<LibraryRoute> {
                LibraryScreen(
                    onOpenSection = { if (it == LibrarySection.Downloads) navController.navigate(app.freshtuber.ui.navigation.DownloadsRoute) else navController.navigate(LibraryListRoute(it.name)) },
                    onSignIn = signIn,
                )
            }
            composable<app.freshtuber.ui.navigation.DownloadsRoute> { app.freshtuber.ui.screens.downloads.DownloadsScreen(onBack = { navController.popBackStack() }, onVideo = { navController.navigate(PlayerRoute(it)) }, onSearch = { navController.navigate(SearchRoute) }) }
            composable<LibraryListRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn) }
            composable<PlaylistRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn) }
            composable<ChannelRoute> { ListScreen(navigation, onBack = { navController.popBackStack() }, onSignIn = signIn) }
            composable<AccountRoute> { AccountScreen(onSignIn = signIn) }
            composable<SettingsRoute> { SettingsScreen(onCalibrate = { navController.navigate(CalibrationRoute) }, onAccount = { navController.navigate(AccountRoute) }) }
            composable<CalibrationRoute> { app.freshtuber.ui.radial.CalibrationScreen(onBack = { navController.popBackStack() }) }
            composable<PlayerRoute> {
                PlayerScreen(
                    navigation,
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onAdvanced = { next ->
                        navController.navigate(PlayerRoute(next)) { popUpTo<PlayerRoute> { inclusive = true } }
                    },
                )
            }
        }
    }
    val nowPlaying = playerState.nowPlaying
    if (miniVisible && nowPlaying != null) {
        Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
            MiniPlayer(player = viewModel.player.player, nowPlaying = nowPlaying,
                onOpen = { navController.navigate(PlayerRoute(nowPlaying.videoId)) }, onClose = viewModel.player::stop)
        }
    }
    if ((!fullScreen || playerMenuRequested) && currentDestination?.hasRoute<CalibrationRoute>() != true) {
        val sections = TopLevelDestination.entries.filter { shortsEnabled || it != TopLevelDestination.Shorts }.map { section ->
            val title = stringResource(section.labelRes)
            val openSection = RadialItem("open:${section.name}", title, section.icon) { navController.navigateToTopLevel(section) }
            val children = when (section) {
                TopLevelDestination.Home -> emptyList()
                TopLevelDestination.Library -> LibrarySection.entries.map { part ->
                    RadialItem("library:${part.name}", stringResource(part.titleRes), part.icon,
                        shortTitle = stringResource(if (part == LibrarySection.Liked) R.string.radial_liked_short else part.titleRes)) {
                        if (part == LibrarySection.Downloads) navController.navigate(app.freshtuber.ui.navigation.DownloadsRoute) else navController.navigate(LibraryListRoute(part.name))
                    }
                }
                TopLevelDestination.Account -> {
                    val signedIn = accountState as? AccountState.SignedIn
                    buildList {
                        add(openSection)
                        if (signedIn != null) {
                            add(RadialItem("profiles", stringResource(R.string.radial_profiles), Icons.Rounded.PersonAdd,
                                children = signedIn.all.map { profile ->
                                    RadialItem("profile:${profile.id}", profile.name ?: profile.email ?: title,
                                        Icons.Rounded.PersonAdd, selected = profile.id == signedIn.active.id) { viewModel.switchProfile(profile.id) }
                                }) {})
                            if (signedIn.active.channels.isNotEmpty()) add(RadialItem("channels", stringResource(R.string.radial_channels), section.icon,
                                children = signedIn.active.channels.mapIndexed { index, channel ->
                                    RadialItem("channel:$index", channel.name, section.icon,
                                        selected = channel.pageId == signedIn.active.activePageId) { viewModel.switchChannel(channel) }
                                }) {})
                        }
                        add(RadialItem("add-account", stringResource(R.string.account_add), Icons.Rounded.PersonAdd, action = signIn))
                    }
                }
                TopLevelDestination.Settings -> emptyList()
                else -> emptyList()
            }
            RadialItem(section.name, title, section.icon,
                shortTitle = when (section) { TopLevelDestination.Library -> stringResource(R.string.radial_library_short); TopLevelDestination.Settings -> stringResource(R.string.radial_settings_short); else -> title },
                selected = currentDestination?.hasRoute(section.route::class) == true,
                children = children) {
                    if (section == TopLevelDestination.Home) filters.items.firstOrNull()?.action?.invoke()
                    navController.navigateToTopLevel(section)
                }
        }
        androidx.compose.runtime.key(currentDestination?.route, playerMenuRequested) {
            RadialMenu(sections, leftHanded, thumbArc, Modifier.imePadding().padding(bottom = if (miniVisible) 72.dp else 0.dp),
                initiallyExpanded = playerMenuRequested, showAnchor = !fullScreen,
                anchorMargin = 36f,
                pageSlots = 5,
                contentInsets = WindowInsets.safeDrawing.union(WindowInsets.safeGestures.only(WindowInsetsSides.Vertical)),
                onExpandedChange = { if (!it) playerMenuRequested = false })
        }
    }
    }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo<HomeRoute> { saveState = false }
        launchSingleTop = true
        // An explicit section choice must not restore an old watch/channel child above it.
        // Home remains in the stack, preserving its filter callbacks and loaded feed.
        restoreState = false
    }
}
