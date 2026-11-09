package com.koda.dev.feature.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koda.dev.core.ui.glass.BackdropState
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.captureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop
import com.koda.dev.core.ui.glass.rememberBackdropState
import com.koda.dev.core.ui.glass.springPressClickable
import com.termux.view.TerminalView

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

    // Floating capsule heights
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    var bottomDockHeightPx by remember { mutableFloatStateOf(0f) }

    // Hardware backdrop capture state for optical lens blur and refraction
    val backdropState = rememberBackdropState()

    LaunchedEffect(projectId) {
        if (!projectId.isNullOrBlank()) {
            viewModel.openProject(projectId)
        }
    }

    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        // Terminal Content Canvas (captured into backdrop state for real-time glass refraction)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(backdropState)
        ) {
            // Dynamic top spacing so terminal output clears the floating top bar
            Spacer(
                modifier = Modifier.height(
                    if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 4.dp else 88.dp
                )
            )

            // Optional banner when Linux environment is not yet installed
            if (!uiState.isUbuntuReady && !uiState.isAlpineReady && !uiState.isRunning && !uiState.isBootstrapping) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2E170C).copy(alpha = 0.85f),
                                    Color(0xFF1E1008).copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = "Enable apt for GCC, Python 3, OpenJDK 21, Node.js, Rust, Go, and build tools.",
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Setup Ubuntu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Active Ubuntu bootstrap progress banner
            if (uiState.isBootstrapping) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2E170C).copy(alpha = 0.85f),
                                    Color(0xFF1E1008).copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
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
                        LinearProgressIndicator(
                            progress = { if (uiState.bootstrapProgress >= 0f) uiState.bootstrapProgress else 0.5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFE95420),
                            trackColor = Color(0xFF3B1F0E)
                        )
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
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
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

            // Dynamic bottom spacing so terminal output clears the floating accessory dock
            Spacer(
                modifier = Modifier.height(
                    if (bottomDockHeightPx > 0f) with(LocalDensity.current) { bottomDockHeightPx.toDp() } + 6.dp else 70.dp
                )
            )
        }

        // Floating Top Bar: 24dp curved Liquid Glass capsule floating over terminal
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
            // Internal Top Refractive Sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
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
                        // Frosted Terminal Avatar Disc
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
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = TerminalPrompt
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = uiState.projectName.ifBlank { "Terminal" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFF0F6FC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Environment status badge
                                val isUbuntu = uiState.isUbuntuReady
                                val isAlpine = uiState.isAlpineReady
                                val chipDot = when {
                                    isUbuntu -> Color(0xFFE95420)
                                    isAlpine -> Color(0xFF3FB950)
                                    else -> Color(0xFFE95420)
                                }
                                val chipLabel = when {
                                    uiState.isBootstrapping -> "Installing..."
                                    isUbuntu -> "Ubuntu 24.04"
                                    isAlpine -> "Alpine"
                                    else -> "Ubuntu 24.04"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.06f))
                                        .border(
                                            width = 0.75.dp,
                                            brush = GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                .clip(CircleShape)
                                                .background(chipDot)
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

                            val displayPath = when {
                                uiState.projectId != null -> uiState.workingDirectory
                                uiState.workingDirectory.isNotBlank() && uiState.workingDirectory != "/" -> uiState.workingDirectory
                                else -> null
                            }
                            if (displayPath != null) {
                                Text(
                                    text = displayPath,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF8B949E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Font size adjuster
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                .border(
                                    width = 0.75.dp,
                                    brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                                    shape = CircleShape
                                )
                                .springPressClickable { showFontSizeDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Adjust Font Size",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Soft keyboard toggle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isKeyboardOpen) Modifier.background(Color(0xFF1F6FEB).copy(alpha = 0.40f))
                                    else Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                )
                                .border(
                                    width = 0.75.dp,
                                    brush = if (isKeyboardOpen) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.8f, dispersionAlpha = 0.4f)
                                    else GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                                    shape = CircleShape
                                )
                                .springPressClickable {
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
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Toggle Keyboard",
                                tint = if (isKeyboardOpen) Color(0xFF79C0FF) else Color(0xFFC9D1D9),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Restart session
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                .border(
                                    width = 0.75.dp,
                                    brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                                    shape = CircleShape
                                )
                                .springPressClickable { viewModel.restartSession() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Session",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                }
            )
        }

        // Floating Accessory Dock: 24dp curved Liquid Glass capsule floating over terminal
        val bottomDockShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coords ->
                    bottomDockHeightPx = coords.size.height.toFloat()
                }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = bottomDockShape,
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x800F172A)
                )
                .liquidGlassBackdrop(
                    backdropState = backdropState,
                    shape = bottomDockShape,
                    blurRadius = 24.dp,
                    tintColor = Color(0xD010141E),
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
                    .height(28.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            AccessoryKeyboardBar(
                onKeyPress = {
                    viewModel.sendAccessoryKey(it)
                    terminalViewInstance?.requestFocus()
                },
                isControlActive = viewModel.viewClient.isControlKeyPressed,
                modifier = Modifier.background(Color.Transparent)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
            .border(
                width = 0.75.dp,
                brush = GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .focusProperties { canFocus = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.forEach { tab ->
                val isSelected = tab.id == activeSessionId
                val tabShape = RoundedCornerShape(10.dp)

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(tabShape)
                        .then(
                            if (isSelected) Modifier.background(Color(0xFF1F6FEB).copy(alpha = 0.35f))
                            else Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.08f, bottomAlpha = 0.03f))
                        )
                        .border(
                            width = 0.75.dp,
                            brush = if (isSelected) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.7f, dispersionAlpha = 0.35f)
                            else GlassDesign.microGlassBorder(topAlpha = 0.18f, bottomAlpha = 0.05f),
                            shape = tabShape
                        )
                        .springPressClickable { onTabClick(tab.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (tab.isRunning) TerminalPrompt else Color(0xFF8B949E))
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
                                .clip(CircleShape)
                                .springPressClickable { onCloseClick(tab.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close ${tab.title}",
                                tint = if (isSelected) Color.White.copy(alpha = 0.85f) else AccessoryKeyContent.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Add session button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.08f))
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                        shape = CircleShape
                    )
                    .springPressClickable { onAddSession() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Terminal Session",
                    tint = AccessoryKeyContent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
