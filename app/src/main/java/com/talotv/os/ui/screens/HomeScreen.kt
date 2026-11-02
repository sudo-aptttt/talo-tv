package com.talotv.os.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.talotv.os.TaloApplication
import com.talotv.os.core.media.ContentRepository
import com.talotv.os.core.media.DockAppItem
import com.talotv.os.core.media.MediaSpotlight
import com.talotv.os.core.profile.UserProfile
import com.talotv.os.ui.components.FloatingAppDock
import com.talotv.os.ui.components.FloatingProfileTimeWidget
import com.talotv.os.ui.components.FullAppGrid
import com.talotv.os.ui.components.NavDestination
import com.talotv.os.ui.components.ProfileSwitcherDialog
import com.talotv.os.ui.components.TopShelfSpotlight
import com.talotv.os.ui.components.UpwardChevronIndicator
import com.talotv.os.ui.components.TvSidebarDrawer
import com.talotv.os.ui.components.SettingsSidePanel
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
fun HomeScreen(
    isSettingsOpen: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {},
    onNavigateToSettings: () -> Unit = onOpenSettings,
    onNavigateToSearch: () -> Unit,
    onNavigateToStore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profileManager = remember { TaloApplication.instance.profileManager }
    val userProfileManager = remember { com.talotv.os.core.profile.UserProfileManager(context) }
    val installedAppsManager = remember { TaloApplication.instance.installedAppsManager }

    val activeProfile by userProfileManager.activeProfile.collectAsState()
    val allProfiles by userProfileManager.profiles.collectAsState()
    val installedApps by installedAppsManager.installedApps.collectAsState()
    val dockApps = remember(installedApps) { installedAppsManager.getDockApps() }
    val gridApps = remember(installedApps, dockApps) {
        val dockPackageNames = dockApps.map { it.packageName }.toSet()
        val dockIds = dockApps.map { it.id }.toSet()
        val remaining = installedApps.filter { app ->
            app.packageName !in dockPackageNames && app.id !in dockIds
        }
        // Place Store first, other apps in middle, and Settings last
        remaining.sortedWith(
            compareBy<com.talotv.os.core.apps.InstalledApp> { app ->
                when {
                    app.isStore -> 0
                    app.isSettings -> 2
                    else -> 1
                }
            }.thenBy { it.name }
        )
    }

    var activeSpotlight by remember {
        mutableStateOf<MediaSpotlight>(dockApps.firstOrNull()?.spotlight ?: ContentRepository.fallbackSpotlight)
    }
    var activeSuggestion by remember {
        mutableStateOf<com.talotv.os.core.media.AppHeroSuggestion?>(
            dockApps.firstOrNull()?.let { com.talotv.os.core.media.ContentSuggestionManager.getSuggestion(context, it) }
        )
    }
    var isProfileSwitcherOpen by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(false) }

    LaunchedEffect(dockApps) {
        if (dockApps.isNotEmpty()) {
            activeSpotlight = dockApps.first().spotlight
            activeSuggestion = com.talotv.os.core.media.ContentSuggestionManager.getSuggestion(context, dockApps.first())
        }
    }

    val dockFirstFocusRequester = remember { FocusRequester() }
    val dockSettingsFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    var openedFromDockSettings by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Initial focus on the first dock item to avoid being trapped on profile avatar, scrolled to top
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
        delay(120)
        try {
            dockFirstFocusRequester.requestFocus()
        } catch (e: Exception) { }
    }

    fun launchAppPackage(pkg: String, name: String) {
        if (pkg == "com.android.vending") {
            installedAppsManager.launchStore(context)
            return
        }
        val pm: PackageManager = context.packageManager
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "Lancement de $name (Talo OS)", Toast.LENGTH_SHORT).show()
        }
    }

    // Focus restoration when settings side-panel closes: return to caller or dock first item
    LaunchedEffect(isSettingsOpen) {
        if (!isSettingsOpen) {
            delay(100)
            try {
                if (openedFromDockSettings) {
                    dockSettingsFocusRequester.requestFocus()
                    openedFromDockSettings = false
                } else {
                    dockFirstFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    // Intercept Back button: closes dialogs or toggles sidebar
    // Disabled while isSettingsOpen is true so SettingsSidePanel's BackHandler handles sub-level/close exclusively
    BackHandler(enabled = !isSettingsOpen) {
        if (isProfileSwitcherOpen) {
            isProfileSwitcherOpen = false
        } else if (isSidebarOpen) {
            isSidebarOpen = false
        } else {
            isSidebarOpen = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Scrollable TV Experience (focus disabled behind settings overlay)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !isSettingsOpen }
                .verticalScroll(scrollState)
        ) {
            // 1. Top Shelf Showcase (Hero Section with Dynamic Spotlight Backdrop full screen to bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
            ) {
                TopShelfSpotlight(
                    spotlight = activeSpotlight,
                    suggestion = activeSuggestion,
                    modifier = Modifier.fillMaxSize()
                )

                // The Signature Talo Floating Glass Dock sitting at bottom of screen
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                ) {
                    // Upward Chevron Indicator right above dock
                    UpwardChevronIndicator()

                    Spacer(modifier = Modifier.height(8.dp))

                    FloatingAppDock(
                        apps = dockApps,
                        firstItemFocusRequester = dockFirstFocusRequester,
                        settingsItemFocusRequester = dockSettingsFocusRequester,
                        onNavigateLeftFromFirst = { isSidebarOpen = true },
                        onNavigateUpToProfile = {
                            try { profileFocusRequester.requestFocus() } catch (e: Exception) { }
                        },
                        onAppFocused = { app ->
                            activeSpotlight = app.spotlight
                            activeSuggestion = com.talotv.os.core.media.ContentSuggestionManager.getSuggestion(context, app)
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        },
                        onAppSelected = { app ->
                            if (app.id == "app-store" || app.packageName == "com.android.vending") {
                                onNavigateToStore()
                            } else if (app.id == "dock-settings" || app.id == "app-settings" || app.packageName == "com.talotv.settings") {
                                openedFromDockSettings = true
                                onOpenSettings()
                            } else {
                                launchAppPackage(app.packageName, app.name)
                            }
                        }
                    )
                }
            }

            if (gridApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))

                // Full App Library Grid (Real Installed Apps Only, No Duplicates from Dock)
                FullAppGrid(
                    apps = gridApps,
                    onNavigateLeftFromFirstColumn = { isSidebarOpen = true },
                    onAppSelected = { app ->
                        if (app.id == "app-store" || app.isStore || app.packageName == "com.android.vending") {
                            onNavigateToStore()
                        } else if (app.id == "app-settings" || app.isSettings || app.packageName == "com.talotv.settings") {
                            openedFromDockSettings = false
                            onOpenSettings()
                        } else {
                            launchAppPackage(app.packageName, app.name)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // ============================================================
        // FLOATING TOP-RIGHT: Time on left, Current Profile Picture (PP) on right
        // ============================================================
        FloatingProfileTimeWidget(
            activeProfile = activeProfile,
            focusRequester = profileFocusRequester,
            onProfileClick = { isProfileSwitcherOpen = !isProfileSwitcherOpen },
            onNavigateDown = {
                try { dockFirstFocusRequester.requestFocus() } catch (e: Exception) { }
            },
            onNavigateLeft = { isSidebarOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 44.dp)
                .focusProperties { canFocus = !isSettingsOpen }
        )

        // ============================================================
        // OVERLAY 1: FLOATING LEFT SIDEBAR DRAWER (User Image 1)
        // ============================================================
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            TvSidebarDrawer(
                currentDestination = NavDestination.HOME,
                activeProfile = activeProfile,
                onNavigate = { dest ->
                    isSidebarOpen = false
                    when (dest) {
                        NavDestination.SEARCH -> onNavigateToSearch()
                        NavDestination.SETTINGS -> onOpenSettings()
                        NavDestination.HOME -> { /* already on home */ }
                        NavDestination.CINEMA -> {
                            activeSpotlight = dockApps.getOrNull(0)?.spotlight ?: ContentRepository.fallbackSpotlight
                        }
                        NavDestination.LIVE_TV -> {
                            launchAppPackage("net.freebox.oqee", "Freebox OQEE")
                        }
                        NavDestination.ARCADE -> {
                            activeSpotlight = dockApps.getOrNull(1)?.spotlight ?: ContentRepository.fallbackSpotlight
                        }
                    }
                },
                onAppClick = { app ->
                    isSidebarOpen = false
                    if (app.id == "app-store" || app.packageName == "com.android.vending") {
                        onNavigateToStore()
                    } else if (app.id == "dock-settings" || app.id == "app-settings" || app.packageName == "com.talotv.settings") {
                        onOpenSettings()
                    } else {
                        launchAppPackage(app.packageName, app.name)
                    }
                },
                onProfileClick = {
                    isSidebarOpen = false
                    isProfileSwitcherOpen = true
                },
                onDismiss = { isSidebarOpen = false }
            )
        }

        // ============================================================
        // OVERLAY 2: PROFILE SWITCHER DIALOG
        // ============================================================
        AnimatedVisibility(
            visible = isProfileSwitcherOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 44.dp, top = 76.dp)
        ) {
            ProfileSwitcherDialog(
                profiles = allProfiles,
                activeProfile = activeProfile,
                onSelectProfile = { profile ->
                    userProfileManager.switchProfile(profile)
                    isProfileSwitcherOpen = false
                    Toast.makeText(context, "Profil « ${profile.name} » activé", Toast.LENGTH_SHORT).show()
                },
                onAddProfileClick = {
                    isProfileSwitcherOpen = false
                    onOpenSettings()
                },
                onDismiss = { isProfileSwitcherOpen = false }
            )
        }

        // ============================================================
        // OVERLAY 3: DIMMING SCRIM FOR SETTINGS SIDE-PANEL
        // ============================================================
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onCloseSettings()
                    }
            )
        }

        // ============================================================
        // OVERLAY 4: SETTINGS SIDE-PANEL (VOLET LATÉRAL DROIT ~35% WIDTH)
        // ============================================================
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260)
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(220)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsSidePanel(
                onClose = onCloseSettings,
                onOpenProfileSwitcher = {
                    onCloseSettings()
                    isProfileSwitcherOpen = true
                }
            )
        }
    }
}
