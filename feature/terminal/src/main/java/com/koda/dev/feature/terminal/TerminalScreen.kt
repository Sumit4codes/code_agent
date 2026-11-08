package com.koda.dev.feature.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView

// Terminal colors and shared accessory components are defined in TerminalComponents.kt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    projectId: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var terminalViewInstance by remember { mutableStateOf<TerminalView?>(null) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardOpen = WindowInsets.isImeVisible

    LaunchedEffect(projectId) {
        if (!projectId.isNullOrBlank()) {
            viewModel.openProject(projectId)
        }
    }

    LaunchedEffect(Unit) {
        // Switching to terminal page should not open the keyboard automatically; hide if already open
        keyboardController?.hide()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFC9D1D9)
                                )
                            }
                        }
                    },
                    title = {
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = uiState.projectName.ifBlank { "Terminal" },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF0F6FC)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Environment status chip
                                val isUbuntu = uiState.isUbuntuReady
                                val isAlpine = uiState.isAlpineReady
                                val chipBg = when {
                                    isUbuntu -> Color(0xFF2E170C)
                                    isAlpine -> Color(0xFF152A1E)
                                    else -> Color(0xFF2E170C)
                                }
                                val chipBorder = when {
                                    isUbuntu -> Color(0xFFE95420).copy(alpha = 0.45f)
                                    isAlpine -> Color(0xFF2EA043).copy(alpha = 0.45f)
                                    else -> Color(0xFFE95420).copy(alpha = 0.45f)
                                }
                                val chipDot = when {
                                    isUbuntu -> Color(0xFFE95420)
                                    isAlpine -> Color(0xFF3FB950)
                                    else -> Color(0xFFE95420)
                                }
                                val chipLabel = when {
                                    uiState.isBootstrapping -> "Installing Ubuntu..."
                                    isUbuntu -> "Ubuntu 24.04 LTS"
                                    isAlpine -> "Alpine Linux"
                                    else -> "Ubuntu 24.04 LTS"
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (uiState.isBootstrapping) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(8.dp),
                                                strokeWidth = 1.5.dp,
                                                color = Color(0xFFE95420)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        color = chipDot,
                                                        shape = RoundedCornerShape(3.dp)
                                                    )
                                            )
                                        }
                                        Text(
                                            text = chipLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isUbuntu || !isAlpine) Color(0xFFFF8A50) else Color(0xFF56D364)
                                        )
                                    }
                                }
                            }

                            // Subtitle: only show meaningful path when inside a project or non-root working dir
                            val displayPath = when {
                                uiState.projectId != null -> uiState.workingDirectory
                                uiState.workingDirectory.isNotBlank() && uiState.workingDirectory != "/" -> uiState.workingDirectory
                                else -> null
                            }
                            if (displayPath != null) {
                                Text(
                                    text = displayPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B949E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Font size adjuster
                        IconButton(
                            onClick = { showFontSizeDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Adjust Font Size",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Soft keyboard toggle
                        IconButton(
                            onClick = {
                                val view = terminalViewInstance
                                if (view != null) {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    if (isKeyboardOpen) {
                                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                    } else {
                                        view.isFocusable = true
                                        view.isFocusableInTouchMode = true
                                        view.requestFocus()
                                        imm?.showSoftInput(view, 0)
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Toggle Keyboard",
                                tint = if (isKeyboardOpen) Color(0xFF58A6FF) else Color(0xFFC9D1D9),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Restart interactive shell session
                        IconButton(
                            onClick = { viewModel.restartSession() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Session",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF161B22)
                    )
                )
                HorizontalDivider(
                    color = Color(0xFF30363D),
                    thickness = 0.5.dp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .background(TerminalBg)
                .imePadding()
        ) {
            // Optional banner when Linux environment is not yet installed
            if (!uiState.isUbuntuReady && !uiState.isAlpineReady && !uiState.isRunning && !uiState.isBootstrapping) {
                Surface(
                    color = Color(0xFF1E1712),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ubuntu 24.04 LTS (glibc)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE95420)
                            )
                            Text(
                                text = "Enable apt to install GCC, Python 3, OpenJDK 21, Node.js, Rust, Go, and build tools.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccessoryKeyContent
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.installUbuntuEnvironment() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE95420),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Setup Ubuntu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Active Ubuntu bootstrap progress banner
            if (uiState.isBootstrapping) {
                Surface(
                    color = Color(0xFF1E1712),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Bootstrapping Ubuntu 24.04 LTS...",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE95420)
                            )
                            if (uiState.bootstrapProgress >= 0f) {
                                Text(
                                    text = "${(uiState.bootstrapProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE95420)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (uiState.bootstrapProgress >= 0f) {
                            LinearProgressIndicator(
                                progress = { uiState.bootstrapProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = Color(0xFFE95420),
                                trackColor = Color(0xFF3B1F0E)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = Color(0xFFE95420),
                                trackColor = Color(0xFF3B1F0E)
                            )
                        }
                        if (uiState.bootstrapMessage.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = uiState.bootstrapMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccessoryKeyContent
                            )
                        }
                    }
                }
            }

            // Terminal Session Tab Bar
            if (uiState.sessions.isNotEmpty()) {
                TerminalSessionTabBar(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.activeSessionId,
                    onTabClick = { sessionId ->
                        viewModel.switchSession(sessionId)
                        terminalViewInstance?.requestFocus()
                    },
                    onCloseClick = { sessionId ->
                        viewModel.closeSession(sessionId)
                        terminalViewInstance?.requestFocus()
                    },
                    onAddSession = {
                        viewModel.addNewSession()
                        terminalViewInstance?.requestFocus()
                    }
                )
            }

            // Real Linux Terminal Emulator Canvas (Termux TerminalView)
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        val density = ctx.resources.displayMetrics.scaledDensity
                        val initialPx = (uiState.fontSizeSp * density).toInt().coerceAtLeast(16)
                        setTextSize(initialPx)
                        tag = initialPx
                        setTypeface(Typeface.MONOSPACE)
                        setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))
                        viewModel.registerTerminalView(this)
                        terminalViewInstance = this
                        setOnTouchListener { v, event ->
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                v.isFocusable = true
                                v.isFocusableInTouchMode = true
                                v.requestFocus()
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(v, 0)
                            }
                            false
                        }
                    }
                },
                update = { view ->
                    try {
                        val density = view.context.resources.displayMetrics.scaledDensity
                        val targetPx = (uiState.fontSizeSp * density).toInt().coerceAtLeast(16)
                        val currentPx = (view.tag as? Int) ?: -1
                        if (currentPx != targetPx) {
                            view.setTextSize(targetPx)
                            view.tag = targetPx
                        }

                        val session = viewModel.terminalSession
                        if (session != null) {
                            if (view.currentSession != session) {
                                view.attachSession(session)
                            } else if (view.mEmulator == null && view.width > 0 && view.height > 0) {
                                view.updateSize()
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("TerminalScreen", "Error updating TerminalView session", e)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Quick Accessory Bar
            AccessoryKeyboardBar(
                onKeyPress = {
                    viewModel.sendAccessoryKey(it)
                    terminalViewInstance?.requestFocus()
                },
                isControlActive = viewModel.viewClient.isControlKeyPressed
            )
        }
    }

    if (showFontSizeDialog) {
        TerminalFontSizeDialog(
            currentFontSizeSp = uiState.fontSizeSp,
            onFontSizeChange = { viewModel.setFontSize(it) },
            onDismiss = { showFontSizeDialog = false }
        )
    }
}

@Composable
fun TerminalSessionTabBar(
    sessions: List<TerminalSessionTab>,
    activeSessionId: String?,
    onTabClick: (String) -> Unit,
    onCloseClick: (String) -> Unit,
    onAddSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF161B22),
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sessions.forEach { tab ->
                    val isSelected = tab.id == activeSessionId
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF21262D) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF388BFD)) else null,
                        modifier = Modifier
                            .height(30.dp)
                            .clickable { onTabClick(tab.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Status dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (tab.isRunning) TerminalPrompt else Color(0xFF8B949E),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )

                            // Title
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color.White else AccessoryKeyContent,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Close button
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onCloseClick(tab.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close ${tab.title}",
                                    tint = if (isSelected) Color.White.copy(alpha = 0.8f) else AccessoryKeyContent.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Add session button
                FilledTonalIconButton(
                    onClick = onAddSession,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF21262D),
                        contentColor = AccessoryKeyContent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Terminal Session",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF30363D)
            )
        }
    }
}

