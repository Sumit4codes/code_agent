package com.koda.dev.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import com.koda.dev.feature.terminal.WorkspaceTerminalSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.model.PendingChange
import com.koda.dev.feature.chat.components.ActiveTasksStrip
import com.koda.dev.feature.chat.components.ChatInputBar
import com.koda.dev.feature.chat.components.ContextUsageDialog
import com.koda.dev.feature.chat.components.EmptyChatState
import com.koda.dev.feature.chat.components.GlassDesign
import com.koda.dev.feature.chat.components.InChatDiffBottomSheet
import com.koda.dev.feature.chat.components.MessageItem
import com.koda.dev.feature.chat.components.PendingChangesBanner
import com.koda.dev.feature.chat.components.SessionHistorySheet
import com.koda.dev.feature.chat.components.StreamingBubble
import com.koda.dev.feature.chat.components.captureBackdrop
import com.koda.dev.feature.chat.components.groupMessagesIntoTurns
import com.koda.dev.feature.chat.components.liquidGlassBackdrop
import com.koda.dev.feature.chat.components.rememberBackdropState
import com.koda.dev.feature.chat.components.springPressClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    projectId: String = "",
    viewModel: ChatViewModel = hiltViewModel(),
    onReviewDiffs: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showDiffBottomSheet by remember { mutableStateOf(false) }
    var showTerminalSheet by remember { mutableStateOf(false) }
    var showContextDialog by remember { mutableStateOf(false) }
    var selectedDiffChange by remember { mutableStateOf<PendingChange?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberTopAppBarState()
    val backdropState = rememberBackdropState()
    var inputBarHeightPx by remember { mutableFloatStateOf(0f) }
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && projectId != "none" && state.projectId != projectId) {
            viewModel.openProjectById(projectId)
        }
    }

    val displayMessages = remember(state.messages) {
        groupMessagesIntoTurns(state.messages)
    }

    // Total item count in LazyColumn
    val totalItemsCount = displayMessages.size + if (state.isStreaming) 1 else 0

    // Track whether the user has manually scrolled up away from bottom
    var userScrolledUp by remember { mutableStateOf(false) }

    // When manual scrolling finishes, check whether the user is positioned away from bottom
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && totalItemsCount > 0) {
                // If the last item is visible in the viewport, the user is looking at the latest message
                if (lastVisible.index == totalItemsCount - 1) {
                    userScrolledUp = false
                } else if (lastVisible.index < totalItemsCount - 1) {
                    userScrolledUp = true
                }
            }
        }
    }

    // Determine if user has scrolled up away from bottom
    val isScrolledUp by remember {
        derivedStateOf {
            userScrolledUp && listState.canScrollForward
        }
    }

    // Smoothly scroll when a new message turn is added
    LaunchedEffect(displayMessages.size) {
        if (totalItemsCount > 0 && !userScrolledUp) {
            listState.animateScrollToItem(totalItemsCount - 1)
        }
    }

    // When streaming finishes (state.isStreaming transitions true -> false):
    // Reset userScrolledUp since user was following the run, and keep the view anchored
    // to the latest completed message through the accordion collapse animation.
    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming && totalItemsCount > 0) {
            userScrolledUp = false
            listState.animateScrollToItem(totalItemsCount - 1)
            // Wait for tool accordion collapse animation (150ms) to settle
            delay(180)
            if (totalItemsCount > 0 && !userScrolledUp) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                if (lastVisible != null && lastVisible.index == totalItemsCount - 1) {
                    val overflow = (lastVisible.offset + lastVisible.size + listState.layoutInfo.afterContentPadding) - listState.layoutInfo.viewportEndOffset
                    if (overflow > 0) {
                        listState.scrollBy(overflow.toFloat())
                    }
                } else if (lastVisible != null && lastVisible.index < totalItemsCount - 1) {
                    listState.scrollToItem(totalItemsCount - 1)
                }
            }
        }
    }

    // While streaming text or updating active tool, gently keep viewport pinned to bottom without locking scroll mutex
    LaunchedEffect(state.streamingText, state.activeTool?.output) {
        if (state.isStreaming && !userScrolledUp && !listState.isScrollInProgress && totalItemsCount > 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && lastVisible.index == totalItemsCount - 1) {
                val overflow = (lastVisible.offset + lastVisible.size + listState.layoutInfo.afterContentPadding) - listState.layoutInfo.viewportEndOffset
                if (overflow > 0) {
                    listState.scrollBy(overflow.toFloat())
                }
            } else if (lastVisible != null && lastVisible.index < totalItemsCount - 1) {
                listState.scrollToItem(totalItemsCount - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {},
            bottomBar = {}
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureBackdrop(backdropState)
                ) {
                    // Pending changes banner
                    if (state.pendingChanges.isNotEmpty()) {
                        PendingChangesBanner(
                            changes = state.pendingChanges,
                            onApproveAll = { viewModel.approveAllChanges() },
                            onRejectAll = { viewModel.rejectAllChanges() },
                            onApprove = { viewModel.approveChange(it) },
                            onReject = { viewModel.rejectChange(it) },
                            onReviewDiffs = {
                                selectedDiffChange = state.pendingChanges.firstOrNull()
                                showDiffBottomSheet = true
                            },
                            modifier = Modifier.padding(top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 4.dp else 88.dp)
                        )
                    }

                    // Error notification banner
                    if (state.error != null) {
                        ErrorBanner(
                            error = state.error ?: "",
                            onDismiss = { viewModel.clearError() },
                            modifier = Modifier.padding(top = if (state.pendingChanges.isEmpty()) (if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 4.dp else 88.dp) else 0.dp)
                        )
                    }

                    // Messages area or Empty State
                    if (displayMessages.isEmpty() && !state.isStreaming) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 8.dp else 88.dp,
                                    bottom = if (inputBarHeightPx > 0f) with(LocalDensity.current) { inputBarHeightPx.toDp() } + 12.dp else 120.dp
                                )
                        ) {
                            EmptyChatState(
                                hasProject = state.projectUri != null,
                                projectName = state.projectName,
                                modelName = state.model,
                                onSuggestionClick = { suggestionPrompt ->
                                    viewModel.sendMessage(suggestionPrompt)
                                }
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 8.dp else 88.dp,
                                bottom = if (inputBarHeightPx > 0f) with(LocalDensity.current) { inputBarHeightPx.toDp() } + 12.dp else 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        items(
                            items = displayMessages,
                            key = { it.id },
                            contentType = { it.role.name }
                        ) { message ->
                            val isLastItem = message.id == displayMessages.lastOrNull()?.id
                            val isLive = state.isStreaming && isLastItem

                            MessageItem(
                                message = message,
                                isLive = isLive,
                                pendingChanges = state.pendingChanges,
                                onApproveChange = { viewModel.approveChange(it) },
                                onRejectChange = { viewModel.rejectChange(it) },
                                onOpenFullReview = { change ->
                                    selectedDiffChange = change ?: state.pendingChanges.firstOrNull()
                                    showDiffBottomSheet = true
                                }
                            )
                        }

                        if (state.isStreaming) {
                            item(key = "streaming_bubble") {
                                StreamingBubble(
                                    streamingText = state.streamingText,
                                    activeTool = state.activeTool,
                                    modelName = state.model
                                )
                            }
                        }
                    }
                }
            }

            // Floating Top Bar (Liquid Glass capsule floating over scrolling messages)
            val topBarShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { coords ->
                        topBarHeightPx = coords.size.height.toFloat()
                    }
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = topBarShape,
                        ambientColor = Color(0x33000000),
                        spotColor = Color(0x660F172A)
                    )
                    .liquidGlassBackdrop(
                        backdropState = backdropState,
                        shape = topBarShape,
                        blurRadius = 24.dp,
                        tintColor = Color(0xC410141E),
                        specularBorder = GlassDesign.liquidGlassBorder(
                            topSpecularAlpha = 0.65f,
                            dispersionAlpha = 0.28f
                        ),
                        borderWidth = 1.dp
                    )
            ) {
                // Internal Top Refraction Sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(GlassDesign.internalGlassSheen())
                )

                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFF0F6FC)
                                )
                            }
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Frosted Robot Avatar Disc
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                                        .border(
                                            width = 0.75.dp,
                                            brush = GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFFC9D1D9)
                                    )
                                }
                                if (state.isStreaming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2F81F7)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = state.sessionTitle.ifBlank { "Koda Dev" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFF0F6FC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (state.isGoalMode) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = if (state.activeStepCount > 0) "GOAL · Step ${state.activeStepCount}" else "GOAL MODE",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (state.projectName.isNotBlank()) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF58A6FF)
                                        )
                                        Text(
                                            text = state.projectName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8B949E),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8B949E)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            state.isStreaming && state.isGoalMode -> "Autonomous Goal running…"
                                            state.isStreaming -> "Working…"
                                            else -> state.model
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (state.isStreaming) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (state.projectUri != null) {
                            IconButton(
                                onClick = { showTerminalSheet = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Project Terminal",
                                    tint = Color(0xFFF0F6FC)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.startNewSession() },
                                enabled = !state.isStreaming
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Chat",
                                    tint = Color(0xFFF0F6FC)
                                )
                            }
                            IconButton(
                                onClick = { showHistorySheet = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Chat History",
                                    tint = Color(0xFFF0F6FC)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }

            // Floating Input Deck (Liquid Glass capsule floating over scrolling messages)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        inputBarHeightPx = coords.size.height.toFloat()
                    }
            ) {
                if (state.activeBackgroundTasks.isNotEmpty()) {
                    ActiveTasksStrip(
                        tasks = state.activeBackgroundTasks,
                        onKillTask = { viewModel.killBackgroundTask(it) },
                        onSendInput = { taskId, input -> viewModel.sendInputToBackgroundTask(taskId, input) },
                        onGetTaskDetails = { taskId -> viewModel.getTaskStatus(taskId) }
                    )
                }
                ChatInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            userScrolledUp = false
                            viewModel.sendMessage(textToSend)
                        }
                    },
                    onCancel = { viewModel.cancelGeneration() },
                    onSteer = {
                        if (inputText.isNotBlank()) {
                            val steerText = inputText
                            inputText = ""
                            userScrolledUp = false
                            viewModel.steerCurrentTask(steerText)
                        }
                    },
                    isStreaming = state.isStreaming,
                    hasProject = state.projectUri != null,
                    projectName = state.projectName,
                    modelName = state.model,
                    availableProviders = state.availableProviders,
                    activeProvider = state.activeProvider,
                    onSelectProvider = { providerId -> viewModel.selectProvider(providerId) },
                    contextUsage = state.contextUsage,
                    onCompact = { viewModel.compactHistory() },
                    onOpenDetails = { showContextDialog = true },
                    onSelectGoal = {
                        val current = inputText.trim()
                        val newText = if (current.startsWith("/goal", ignoreCase = true)) {
                            inputText
                        } else if (current.isBlank()) {
                            "/goal "
                        } else {
                            "/goal $current"
                        }
                        inputText = newText
                    },
                    backdropState = backdropState
                )
            }

            // Scroll to bottom floating button (centered above input bar)
            AnimatedVisibility(
                visible = isScrolledUp,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(LocalDensity.current) { inputBarHeightPx.toDp() } + 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x60000000),
                            spotColor = Color(0x90000000)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF242B38), Color(0xFF151922))
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x66FFFFFF), Color(0x18FFFFFF))
                            ),
                            shape = CircleShape
                        )
                        .springPressClickable {
                            userScrolledUp = false
                            coroutineScope.launch {
                                if (totalItemsCount > 0) {
                                    listState.animateScrollToItem(totalItemsCount - 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFF0F6FC)
                    )
                }
            }
        }
    }

    if (showHistorySheet) {
        SessionHistorySheet(
            sessions = state.sessions,
            currentSessionId = state.sessionId,
            projectName = state.projectName,
            onDismiss = { showHistorySheet = false },
            onSelectSession = { session ->
                viewModel.switchSession(session.id)
            },
            onNewSession = {
                viewModel.startNewSession()
            },
            onRenameSession = { sessionId, newTitle ->
                viewModel.renameSession(sessionId, newTitle)
            },
            onDeleteSession = { sessionId ->
                viewModel.deleteSession(sessionId)
            },
            backdropState = backdropState
        )
    }

    if (showDiffBottomSheet && state.pendingChanges.isNotEmpty()) {
        InChatDiffBottomSheet(
            pendingChanges = state.pendingChanges,
            initialSelectedChange = selectedDiffChange,
            onApprove = { viewModel.approveChange(it) },
            onReject = { viewModel.rejectChange(it) },
            onApproveAll = { viewModel.approveAllChanges() },
            onRejectAll = { viewModel.rejectAllChanges() },
            onDismiss = {
                showDiffBottomSheet = false
                selectedDiffChange = null
            }
        )
    }

    if (showTerminalSheet) {
        val effectiveProjectId = if (state.projectId.isNotBlank()) state.projectId else projectId
        WorkspaceTerminalSheet(
            projectId = effectiveProjectId.ifBlank { null },
            projectName = state.projectName.ifBlank { null },
            onDismiss = { showTerminalSheet = false }
        )
    }

    if (showContextDialog) {
        ContextUsageDialog(
            contextUsage = state.contextUsage,
            modelName = state.model,
            onDismiss = { showContextDialog = false },
            onCompact = {
                showContextDialog = false
                viewModel.compactHistory()
            }
        )
    }
    }
}

@Composable
private fun ErrorBanner(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
