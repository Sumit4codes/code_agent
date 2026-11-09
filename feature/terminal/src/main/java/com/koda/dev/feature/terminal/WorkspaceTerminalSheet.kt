package com.koda.dev.feature.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceTerminalSheet(
    projectId: String? = null,
    projectName: String? = null,
    initialCommand: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var terminalViewInstance by remember { mutableStateOf<TerminalView?>(null) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    val isKeyboardOpen = WindowInsets.isImeVisible

    val keyboardController = LocalSoftwareKeyboardController.current

    // Back handler: if keyboard is open, close keyboard first; otherwise dismiss the sheet
    BackHandler {
        if (isKeyboardOpen) {
            keyboardController?.hide()
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(projectId) {
        if (!projectId.isNullOrBlank()) {
            viewModel.openProject(projectId)
        }
    }

    LaunchedEffect(initialCommand) {
        if (!initialCommand.isNullOrBlank()) {
            // Give session brief moment to bind if just opening, then execute
            kotlinx.coroutines.delay(200)
            viewModel.sendCommandToSession(initialCommand)
        }
    }

    // Drag down to dismiss on handle / header
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAccumulator += delta
        if (dragAccumulator > 120f) {
            onDismiss()
        }
    }

    val isExpanded = isFullScreen || isKeyboardOpen
    val sheetHeightFraction = if (isExpanded) 1.0f else 0.58f

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim above sheet when in half-height mode and keyboard is closed
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Terminal Liquid Glass Sheet Panel
        val sheetShape = if (isExpanded) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeightFraction)
                .shadow(
                    elevation = if (isExpanded) 0.dp else 24.dp,
                    shape = sheetShape,
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x800F172A)
                )
                .clip(sheetShape)
                .background(GlassDesign.liquidGlassBackground())
                .border(
                    width = if (isExpanded) 0.dp else 1.dp,
                    brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.35f),
                    shape = sheetShape
                )
        ) {
            // Internal Top Refraction Sheen
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(GlassDesign.internalGlassSheen())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isExpanded) Modifier.statusBarsPadding() else Modifier)
                    .then(if (!isKeyboardOpen) Modifier.navigationBarsPadding() else Modifier)
            ) {
                // Drag Handle (clickable to toggle half/full height, draggable to dismiss)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { dragAccumulator = 0f }
                        )
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color(0xFF8B949E).copy(alpha = 0.6f), CircleShape)
                    )
                }

                // Terminal Sheet Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                        .border(
                            width = 0.5.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.06f),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Terminal Icon + Title + Environment Status Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = TerminalPrompt,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Terminal",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF0F6FC)
                                )

                                // Environment status badge
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
                                val chipLabel = when {
                                    uiState.isBootstrapping -> "Installing..."
                                    isUbuntu -> "Ubuntu 24.04"
                                    isAlpine -> "Alpine"
                                    else -> "Ubuntu 24.04"
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.height(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(
                                                    color = if (isUbuntu || !isAlpine) Color(0xFFE95420) else Color(0xFF3FB950),
                                                    shape = RoundedCornerShape(2.5.dp)
                                                )
                                        )
                                        Text(
                                            text = chipLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isUbuntu || !isAlpine) Color(0xFFFF8A50) else Color(0xFF56D364)
                                        )
                                    }
                                }
                            }

                            // Subtitle: CWD or Project Name
                            val displayPath = when {
                                uiState.workingDirectory.isNotBlank() && uiState.workingDirectory != "/" -> uiState.workingDirectory
                                !projectName.isNullOrBlank() -> projectName
                                else -> uiState.projectName
                            }
                            Text(
                                text = displayPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B949E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right: Actions (Fullscreen toggle, Keyboard, Font size, Restart, Close)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Toggle Fullscreen / Half-height
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable { isFullScreen = !isFullScreen },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullScreen) "Collapse to half screen" else "Expand to full screen",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Soft keyboard toggle
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable {
                                    if (isKeyboardOpen) {
                                        keyboardController?.hide()
                                    } else {
                                        val view = terminalViewInstance
                                        if (view != null) {
                                            view.isFocusable = true
                                            view.isFocusableInTouchMode = true
                                            view.requestFocus()
                                        }
                                        keyboardController?.show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Toggle Keyboard",
                                tint = if (isKeyboardOpen) Color(0xFF58A6FF) else Color(0xFFC9D1D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Font size adjuster
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable { showFontSizeDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Adjust Font Size",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Restart session
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable { viewModel.restartSession() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Session",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Close sheet
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Terminal",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)

                // Optional banner when Linux environment is not ready
                if (!uiState.isUbuntuReady && !uiState.isAlpineReady && !uiState.isRunning && !uiState.isBootstrapping) {
                    Surface(
                        color = Color(0xFF1E1712),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ubuntu environment setup required",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE95420),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.installUbuntuEnvironment() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE95420),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Active Ubuntu bootstrap progress banner
                if (uiState.isBootstrapping) {
                    Surface(
                        color = Color(0xFF1E1712),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Bootstrapping Ubuntu 24.04...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (uiState.bootstrapProgress >= 0f) uiState.bootstrapProgress else 0f },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = Color(0xFFE95420),
                                trackColor = Color(0xFF3B1F0E)
                            )
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
                            android.util.Log.e("WorkspaceTerminalSheet", "Error updating TerminalView session", e)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Quick Accessory Key Bar
                AccessoryKeyboardBar(
                    onKeyPress = {
                        viewModel.sendAccessoryKey(it)
                        terminalViewInstance?.requestFocus()
                    },
                    isControlActive = viewModel.viewClient.isControlKeyPressed
                )
            }
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
