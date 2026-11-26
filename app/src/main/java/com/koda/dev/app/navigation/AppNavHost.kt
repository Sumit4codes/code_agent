package com.koda.dev.app.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.koda.dev.core.ui.glass.BackdropState
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.LocalFloatingNavBarClearance
import com.koda.dev.core.ui.glass.captureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop
import com.koda.dev.core.ui.glass.rememberBackdropState
import com.koda.dev.core.ui.glass.springPressClickable
import com.koda.dev.feature.chat.ChatScreen
import com.koda.dev.feature.chat.ChatViewModel
import com.koda.dev.feature.editor.DiffReviewScreen
import com.koda.dev.feature.editor.EditorScreen
import com.koda.dev.feature.projects.ProjectsScreen
import com.koda.dev.feature.settings.SettingsScreen
import com.koda.dev.feature.terminal.TerminalScreen

object Routes {
    const val PROJECTS = "projects"
    const val CHAT = "chat"
    const val EDITOR = "editor"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
    const val DIFF_REVIEW = "diff_review"
}

private enum class TopLevelTab(val label: String, val icon: ImageVector) {
    PROJECTS("Projects", Icons.Default.Folder),
    TERMINAL("Terminal", Icons.Default.Terminal),
    SETTINGS("Settings", Icons.Default.Settings)
}

private fun isTopLevel(route: String?): Boolean {
    return route == Routes.PROJECTS || route == Routes.TERMINAL || route == Routes.SETTINGS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.PROJECTS
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = isTopLevel(currentRoute)
    val isKeyboardOpen = WindowInsets.isImeVisible

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                Routes.TERMINAL -> TopLevelTab.TERMINAL.ordinal
                Routes.SETTINGS -> TopLevelTab.SETTINGS.ordinal
                else -> TopLevelTab.PROJECTS.ordinal
            }
        )
    }
    val activeTabIndex = when (currentRoute) {
        Routes.TERMINAL -> TopLevelTab.TERMINAL.ordinal
        Routes.SETTINGS -> TopLevelTab.SETTINGS.ordinal
        else -> selectedTabIndex
    }
    // Single shared Transition drives both tab content and bottom-bar sliding pill in lockstep
    val tabTransition = updateTransition(targetState = activeTabIndex, label = "top_level_tab")

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(activeTabIndex, currentRoute) {
        if (currentRoute == Routes.PROJECTS && activeTabIndex != TopLevelTab.TERMINAL.ordinal) {
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = currentRoute == Routes.PROJECTS && selectedTabIndex != TopLevelTab.PROJECTS.ordinal) {
        selectedTabIndex = TopLevelTab.PROJECTS.ordinal
    }

    // Hardware backdrop capture state for floating liquid glass navigation bar
    val navBackdropState = rememberBackdropState()
    val bottomNavClearance = if (isTopLevelRoute && !isKeyboardOpen) 82.dp else 0.dp

    CompositionLocalProvider(LocalFloatingNavBarClearance provides bottomNavClearance) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
        ) {
            // Main Screen NavHost with backdrop capture for real-time glass refraction
            // and fluid physical page & screen transitions
            NavHost(
            navController = navController,
            startDestination = Routes.PROJECTS,
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(navBackdropState),
            enterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (isTopLevel(fromRoute) && isTopLevel(toRoute)) {
                    fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing))
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (isTopLevel(fromRoute) && isTopLevel(toRoute)) {
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        targetOffset = { it / 5 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                }
            },
            popEnterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (isTopLevel(fromRoute) && isTopLevel(toRoute)) {
                    fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        initialOffset = { it / 5 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))
                }
            },
            popExitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (isTopLevel(fromRoute) && isTopLevel(toRoute)) {
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                }
            }
        ) {
            composable(Routes.PROJECTS) {
                TopLevelTabContainer(
                    selectedTabIndex = activeTabIndex,
                    tabTransition = tabTransition,
                    onProjectSelected = { project ->
                        navController.navigate("${Routes.EDITOR}/${project.id}")
                    },
                    onChatWithProject = { project ->
                        navController.navigate("${Routes.CHAT}/${project.id}")
                    }
                )
            }
            composable(
                route = "${Routes.EDITOR}/{projectId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                EditorScreen(
                    projectId = projectId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenTerminal = {
                        if (projectId.isNotBlank()) {
                            navController.navigate("${Routes.TERMINAL}/${Uri.encode(projectId)}")
                        } else {
                            navController.navigate(Routes.TERMINAL)
                        }
                    }
                )
            }
            composable(Routes.TERMINAL) {
                TerminalScreen()
            }
            composable(
                route = "${Routes.TERMINAL}/{projectId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val rawProjectId = backStackEntry.arguments?.getString("projectId")
                val projectId = rawProjectId?.let { Uri.decode(it) }
                TerminalScreen(
                    projectId = projectId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Routes.CHAT}/{projectId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val chatViewModel: ChatViewModel = hiltViewModel(backStackEntry)
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                ChatScreen(
                    projectId = projectId,
                    viewModel = chatViewModel,
                    onReviewDiffs = {
                        val sid = chatState.sessionId ?: "active"
                        navController.navigate("${Routes.DIFF_REVIEW}/$sid")
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Routes.DIFF_REVIEW}/{sessionId}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    try {
                        navController.getBackStackEntry("${Routes.CHAT}/{projectId}")
                    } catch (_: Exception) {
                        null
                    }
                }
                val chatViewModel: ChatViewModel = if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                DiffReviewScreen(
                    pendingChanges = chatState.pendingChanges,
                    onApprove = { chatViewModel.approveChange(it) },
                    onReject = { chatViewModel.rejectChange(it) },
                    onApproveAll = { chatViewModel.approveAllChanges() },
                    onRejectAll = { chatViewModel.rejectAllChanges() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }

        // Floating Liquid Glass Bottom Navigation Bar
        AnimatedVisibility(
            visible = isTopLevelRoute && !isKeyboardOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingLiquidGlassNavBar(
                tabTransition = tabTransition,
                backdropState = navBackdropState,
                onSelectTab = { index ->
                    selectedTabIndex = index
                    if (currentRoute != Routes.PROJECTS) {
                        navController.popBackStack(Routes.PROJECTS, inclusive = false)
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        }
    }
}

private val TopLevelTabSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 700f
)

private val TopLevelOffsetSpring = spring<androidx.compose.ui.unit.Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 700f
)

/**
 * Keeps the three top-level tab compositions warm so switching tabs never tears down
 * or re-inflates Views (notably TerminalView), while animating alpha and subtle directional
 * translation on the RenderNode layer in sync with the bottom bar indicator pill.
 */
@Composable
private fun TopLevelTabContainer(
    selectedTabIndex: Int,
    tabTransition: Transition<Int>,
    onProjectSelected: (com.koda.dev.core.model.Project) -> Unit,
    onChatWithProject: (com.koda.dev.core.model.Project) -> Unit
) {
    val density = LocalDensity.current
    val composedTabs = remember { mutableStateListOf(true, false, false) }

    // Ensure any directly-selected tab composes immediately
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex in composedTabs.indices) {
            composedTabs[selectedTabIndex] = true
        }
    }
    // Stagger pre-warming of Terminal and Settings right after the initial frame
    // so the very first tab tap transitions purely on the GPU with zero inflation hitch.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        composedTabs[TopLevelTab.TERMINAL.ordinal] = true
        withFrameNanos { }
        composedTabs[TopLevelTab.SETTINGS.ordinal] = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TopLevelTab.entries.forEach { tab ->
            val index = tab.ordinal
            if (!composedTabs[index]) return@forEach

            val isSelected = selectedTabIndex == index
            val tabAlpha by tabTransition.animateFloat(
                transitionSpec = { TopLevelTabSpring },
                label = "tab_alpha_$index"
            ) { target -> if (target == index) 1f else 0f }

            val tabOffsetDp by tabTransition.animateDp(
                transitionSpec = { TopLevelOffsetSpring },
                label = "tab_offset_$index"
            ) { target ->
                when {
                    target == index -> 0.dp
                    index < target -> (-18).dp
                    else -> 18.dp
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isSelected) 1f else 0f)
                    .graphicsLayer {
                        alpha = tabAlpha
                        translationX = with(density) { tabOffsetDp.toPx() }
                    }
                    .focusProperties { canFocus = isSelected }
                    .then(
                        if (!isSelected) {
                            Modifier
                                .clearAndSetSemantics { }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
            ) {
                when (tab) {
                    TopLevelTab.PROJECTS -> ProjectsScreen(
                        onProjectSelected = onProjectSelected,
                        onChatWithProject = onChatWithProject
                    )
                    TopLevelTab.TERMINAL -> TerminalScreen()
                    TopLevelTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

/**
 * Floating Liquid Glass Navigation Bar with 26dp curved corners,
 * real-time physical backdrop blur, AGSL lens refraction, and a spring-animated sliding pill
 * synchronized with [tabTransition].
 */
@Composable
private fun FloatingLiquidGlassNavBar(
    tabTransition: Transition<Int>,
    backdropState: BackdropState?,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarShape = RoundedCornerShape(26.dp)
    val navItems = TopLevelTab.entries
    val density = LocalDensity.current

    val indicatorPosition by tabTransition.animateFloat(
        transitionSpec = { TopLevelTabSpring },
        label = "nav_pill_position"
    ) { target -> target.toFloat() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                elevation = 20.dp,
                shape = navBarShape,
                ambientColor = Color(0x40000000),
                spotColor = Color(0x800F172A)
            )
            .liquidGlassBackdrop(
                backdropState = backdropState,
                shape = navBarShape,
                blurRadius = 24.dp,
                tintColor = Color(0xD410141E),
                specularBorder = GlassDesign.liquidGlassBorder(
                    topSpecularAlpha = 0.65f,
                    dispersionAlpha = 0.28f
                ),
                borderWidth = 1.dp
            )
    ) {
        // Internal Top Refractive Sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(GlassDesign.internalGlassSheen())
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val itemWidth = maxWidth / navItems.size
            val itemShape = RoundedCornerShape(20.dp)

            // Single sliding Liquid Glass selection pill driven by the shared tabTransition
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = with(density) { (itemWidth * indicatorPosition).toPx() }
                    }
                    .clip(itemShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1F6FEB).copy(alpha = 0.35f),
                                Color(0xFF238636).copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.liquidGlassBorder(
                            topSpecularAlpha = 0.70f,
                            dispersionAlpha = 0.35f
                        ),
                        shape = itemShape
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, tab ->
                    val iconTint by tabTransition.animateColor(
                        transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f) },
                        label = "nav_icon_tint_$index"
                    ) { target ->
                        if (target == index) Color(0xFF79C0FF) else Color(0xFF7D8590)
                    }
                    val labelColor by tabTransition.animateColor(
                        transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f) },
                        label = "nav_label_color_$index"
                    ) { target ->
                        if (target == index) Color(0xFFF0F6FC) else Color(0xFF7D8590)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(itemShape)
                            .springPressClickable { onSelectTab(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(19.dp),
                                tint = iconTint
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = labelColor
                            )
                        }
                    }
                }
            }
        }
    }
}
