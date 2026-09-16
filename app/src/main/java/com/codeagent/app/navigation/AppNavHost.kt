package com.codeagent.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.feature.chat.ChatScreen
import com.codeagent.feature.chat.ChatViewModel
import com.codeagent.feature.editor.DiffReviewScreen
import com.codeagent.feature.editor.EditorScreen
import com.codeagent.feature.projects.ProjectsScreen
import com.codeagent.feature.settings.SettingsScreen

object Routes {
    const val PROJECTS = "projects"
    const val CHAT = "chat"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
    const val DIFF_REVIEW = "diff_review"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.PROJECTS
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute == Routes.PROJECTS || currentRoute == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("Projects") },
                        selected = currentRoute == Routes.PROJECTS,
                        onClick = {
                            if (currentRoute != Routes.PROJECTS) {
                                navController.navigate(Routes.PROJECTS) {
                                    popUpTo(Routes.PROJECTS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            if (currentRoute != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(Routes.PROJECTS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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
    }
}
