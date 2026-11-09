package com.koda.dev.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.PROJECTS
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute == Routes.PROJECTS || currentRoute == Routes.TERMINAL || currentRoute == Routes.SETTINGS
    val isKeyboardOpen = WindowInsets.isImeVisible

    // Hardware backdrop capture state for floating liquid glass navigation bar
    val navBackdropState = rememberBackdropState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // Main Screen NavHost with backdrop capture for real-time glass refraction
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(navBackdropState)
        ) {
            composable(Routes.PROJECTS) {
                ProjectsScreen(
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
                currentRoute = currentRoute,
                backdropState = navBackdropState,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Routes.PROJECTS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Floating Liquid Glass Navigation Bar with 26dp curved corners,
 * real-time physical backdrop blur, AGSL lens refraction, and spring-press tactile tabs.
 */
@Composable
private fun FloatingLiquidGlassNavBar(
    currentRoute: String?,
    backdropState: BackdropState?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarShape = RoundedCornerShape(26.dp)

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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val navItems = listOf(
                Triple(Routes.PROJECTS, "Projects", Icons.Default.Folder),
                Triple(Routes.TERMINAL, "Terminal", Icons.Default.Terminal),
                Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings)
            )

            navItems.forEach { (route, label, icon) ->
                val isSelected = currentRoute == route
                val itemShape = RoundedCornerShape(20.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(itemShape)
                        .then(
                            if (isSelected) {
                                Modifier
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
                            } else {
                                Modifier
                            }
                        )
                        .springPressClickable { onNavigate(route) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(19.dp),
                            tint = if (isSelected) Color(0xFF79C0FF) else Color(0xFF7D8590)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF7D8590)
                        )
                    }
                }
            }
        }
    }
}
