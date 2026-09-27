package com.codeagent.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TerminalBg = Color(0xFF0D1117)
private val TerminalPrompt = Color(0xFF39D353)
private val TerminalStdout = Color(0xFFE6EDF3)
private val TerminalStderr = Color(0xFFF85149)
private val TerminalSystem = Color(0xFF58A6FF)
private val AccessoryKeyBg = Color(0xFF21262D)
private val AccessoryKeyContent = Color(0xFFC9D1D9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    projectId: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) {
        if (!projectId.isNullOrBlank()) {
            viewModel.openProject(projectId)
        }
    }

    // Auto-scroll when new output arrives
    LaunchedEffect(uiState.entries.size) {
        if (uiState.entries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.entries.size - 1)
        }
    }

    Scaffold(
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
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Terminal"
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
                .background(TerminalBg)
        ) {
            // Scrollable Console Log
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        TerminalLineItem(entry = entry)
                    }
                }
            }

            // Quick Accessory Bar
            AccessoryKeyboardBar(
                onKeyPress = { viewModel.insertAccessoryKey(it) },
                onHistoryUp = { viewModel.navigateHistoryPrevious() },
                onHistoryDown = { viewModel.navigateHistoryNext() },
                isRunning = uiState.isRunning,
                onCancel = { viewModel.cancelRunningCommand() }
            )

            // Command Input Line
            TerminalInputBar(
                input = uiState.commandInput,
                onInputChange = { viewModel.onCommandInputChange(it) },
                onExecute = { viewModel.executeCommand() },
                isRunning = uiState.isRunning,
                onCancel = { viewModel.cancelRunningCommand() }
            )
        }
    }
}

@Composable
private fun TerminalLineItem(entry: TerminalEntry) {
    val color = when (entry.type) {
        TerminalEntryType.COMMAND -> TerminalPrompt
        TerminalEntryType.STDOUT -> TerminalStdout
        TerminalEntryType.STDERR -> TerminalStderr
        TerminalEntryType.SYSTEM -> TerminalSystem
    }

    Text(
        text = entry.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = if (entry.type == TerminalEntryType.COMMAND) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun AccessoryKeyboardBar(
    onKeyPress: (String) -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    isRunning: Boolean,
    onCancel: () -> Unit
) {
    val scrollState = rememberScrollState()
    val keys = listOf("TAB", "|", "&&", ";", "-", "--", "/", "~", "git", "ls -la", "pwd", "clear")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ctrl-C / Cancel key
        Button(
            onClick = {
                if (isRunning) onCancel() else onKeyPress("CTRL-C")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) TerminalStderr else AccessoryKeyBg,
                contentColor = if (isRunning) Color.White else AccessoryKeyContent
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

        // History Up
        IconButton(
            onClick = onHistoryUp,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous Command",
                tint = AccessoryKeyContent
            )
        }

        // History Down
        IconButton(
            onClick = onHistoryDown,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next Command",
                tint = AccessoryKeyContent
            )
        }

        // Quick keys
        keys.forEach { key ->
            Button(
                onClick = { onKeyPress(key) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccessoryKeyBg,
                    contentColor = AccessoryKeyContent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = key,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun TerminalInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    isRunning: Boolean,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF161B22),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                color = TerminalPrompt,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Enter command...",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onExecute() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TerminalStdout,
                    fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = TerminalPrompt
                )
            )

            if (isRunning) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop command",
                        tint = TerminalStderr
                    )
                }
            } else {
                IconButton(
                    onClick = onExecute,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Run command",
                        tint = if (input.isNotBlank()) TerminalPrompt else Color(0xFF484F58)
                    )
                }
            }
        }
    }
}
