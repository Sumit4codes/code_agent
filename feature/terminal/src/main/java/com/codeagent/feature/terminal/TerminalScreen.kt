package com.codeagent.feature.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView

private val TerminalBg = Color(0xFF0D1117)
private val TerminalPrompt = Color(0xFF39D353)
private val TerminalStderr = Color(0xFFF85149)
private val AccessoryKeyBg = Color(0xFF21262D)
private val AccessoryKeyContent = Color(0xFFC9D1D9)
private val EscKeyBg = Color(0xFF1F6FEB)

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
    val focusRequester = remember { FocusRequester() }
    val isKeyboardOpen = WindowInsets.isImeVisible

    LaunchedEffect(projectId) {
        if (!projectId.isNullOrBlank()) {
            viewModel.openProject(projectId)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.projectName.ifBlank { "Terminal" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            // Environment status chip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isAlpineReady) Color(0xFF1F3D2C) else Color(0xFF252D38),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (uiState.isAlpineReady) TerminalPrompt else Color(0xFFE3B341),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.isAlpineReady) "Alpine Linux" else "Toybox Shell",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (uiState.isAlpineReady) TerminalPrompt else Color(0xFFE3B341)
                                    )
                                }
                            }
                            if (uiState.isRunning) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = TerminalPrompt
                                )
                            }
                        }
                        if (uiState.workingDirectory.isNotBlank()) {
                            Text(
                                text = uiState.workingDirectory,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
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
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Toggle Keyboard"
                        )
                    }

                    // Restart interactive shell session
                    IconButton(onClick = { viewModel.restartSession() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Session"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
            // Optional banner when Alpine Linux is not yet installed
            if (!uiState.isAlpineReady && !uiState.isRunning && !uiState.isBootstrapping) {
                Surface(
                    color = Color(0xFF161B22),
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
                                text = "Alpine Linux Environment",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TerminalPrompt
                            )
                            Text(
                                text = "Enable apk to install git, python3, gcc, g++, make, and vim.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccessoryKeyContent
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.installAlpineEnvironment() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TerminalPrompt,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Real Linux Terminal Emulator Canvas (Termux TerminalView)
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTextSize(14)
                        setTypeface(Typeface.MONOSPACE)
                        setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))
                        viewModel.registerTerminalView(this)
                        terminalViewInstance = this
                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(this, 0)
                            }
                        }
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
                        post {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(this, 0)
                        }
                    }
                },
                update = { view ->
                    val session = viewModel.terminalSession
                    if (session != null) {
                        if (view.currentSession != session) {
                            view.attachSession(session)
                        } else if (view.mEmulator == null && view.width > 0 && view.height > 0) {
                            view.updateSize()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
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
}

@Composable
private fun AccessoryKeyboardBar(
    onKeyPress: (String) -> Unit,
    isControlActive: Boolean
) {
    val scrollState = rememberScrollState()
    val quickTokens = listOf(
        "|", "&&", ";", "-", "--", "/", "~", "$", ":", "'", "\"",
        "apk", "git", "python3", "gcc", "make", "vim", "ls -la", "pwd", "clear"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .horizontalScroll(scrollState)
            .focusProperties { canFocus = false }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ESC key (crucial for Vim mode switching)
        Button(
            onClick = { onKeyPress("ESC") },
            colors = ButtonDefaults.buttonColors(
                containerColor = EscKeyBg,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "ESC",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // TAB key (autocompletion)
        Button(
            onClick = { onKeyPress("TAB") },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccessoryKeyBg,
                contentColor = AccessoryKeyContent
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "TAB",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // CTRL toggle modifier
        Button(
            onClick = { onKeyPress("CTRL") },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isControlActive) TerminalPrompt else AccessoryKeyBg,
                contentColor = if (isControlActive) Color.Black else AccessoryKeyContent
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "CTRL",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // CTRL-C interrupt key
        Button(
            onClick = { onKeyPress("CTRL-C") },
            colors = ButtonDefaults.buttonColors(
                containerColor = TerminalStderr,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "CTRL-C",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Arrow Left
        IconButton(
            onClick = { onKeyPress("LEFT") },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Left Arrow",
                tint = AccessoryKeyContent
            )
        }

        // Arrow Up
        IconButton(
            onClick = { onKeyPress("UP") },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up Arrow",
                tint = AccessoryKeyContent
            )
        }

        // Arrow Down
        IconButton(
            onClick = { onKeyPress("DOWN") },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down Arrow",
                tint = AccessoryKeyContent
            )
        }

        // Arrow Right
        IconButton(
            onClick = { onKeyPress("RIGHT") },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Right Arrow",
                tint = AccessoryKeyContent
            )
        }

        // Quick keys & tokens
        quickTokens.forEach { token ->
            Button(
                onClick = { onKeyPress(token) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccessoryKeyBg,
                    contentColor = AccessoryKeyContent
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = token,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
