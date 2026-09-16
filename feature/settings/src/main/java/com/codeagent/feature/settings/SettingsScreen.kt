package com.codeagent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.model.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveMessage) {
        val msg = state.saveMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECTION 1: PROVIDER & CREDENTIALS
            SettingsCard(
                icon = Icons.Default.Cloud,
                title = "AI Provider & Endpoint",
                subtitle = "Choose an AI service or configure your custom endpoint"
            ) {
                // Quick Presets
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderPreset.entries.forEach { preset ->
                        val isSelected = state.baseUrl == preset.baseUrl && state.providerType == preset.providerType
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.label) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Provider Protocol
                Text(
                    text = "API Protocol",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.providerType == ProviderType.OPENAI_COMPATIBLE,
                        onClick = { viewModel.updateProviderType(ProviderType.OPENAI_COMPATIBLE) },
                        label = { Text("OpenAI / Compatible") }
                    )
                    FilterChip(
                        selected = state.providerType == ProviderType.ANTHROPIC,
                        onClick = { viewModel.updateProviderType(ProviderType.ANTHROPIC) },
                        label = { Text("Anthropic") }
                    )
                }

                // Base URL
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it) },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // API Key
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { viewModel.updateApiKey(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (state.apiKey.isBlank()) "Required for cloud providers. Keys stored securely." else "Key set. Tap eye to view.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Test Connection Button
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !state.isTestingConnection && state.baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testing Connection…")
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }

                // Connection Result Banner
                AnimatedVisibility(
                    visible = state.connectionTestResult != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val result = state.connectionTestResult
                    if (result != null) {
                        val isSuccess = result.success
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: MODEL & HYPERPARAMETERS
            SettingsCard(
                icon = Icons.Default.Tune,
                title = "Model & Parameters",
                subtitle = "Tune response reasoning, tokens, and model identifier"
            ) {
                // Model Name
                OutlinedTextField(
                    value = state.model,
                    onValueChange = { viewModel.updateModel(it) },
                    label = { Text("Model Name") },
                    leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Model Suggestions
                val suggestions = when (state.providerType) {
                    ProviderType.ANTHROPIC -> listOf(
                        "claude-3-7-sonnet-20250219",
                        "claude-3-5-sonnet-20241022",
                        "claude-3-5-haiku-20241022"
                    )
                    ProviderType.OPENAI_COMPATIBLE -> listOf(
                        "gpt-4o",
                        "gpt-4o-mini",
                        "deepseek-chat",
                        "gemini-2.5-flash",
                        "qwen2.5-coder"
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.updateModel(suggestion) },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Temperature Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Temperature",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = String.format("%.2f", state.temperature),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = state.temperature,
                        onValueChange = { viewModel.updateTemperature(it) },
                        valueRange = 0.0f..1.0f,
                        steps = 19
                    )
                    Text(
                        text = "0.0 = Precise & deterministic (best for code) · 1.0 = Creative reasoning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Max Tokens
                OutlinedTextField(
                    value = state.maxTokens,
                    onValueChange = { viewModel.updateMaxTokens(it) },
                    label = { Text("Max Output Tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Max Token Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2048", "4096", "8192", "16384").forEach { tokens ->
                        SuggestionChip(
                            onClick = { viewModel.updateMaxTokens(tokens) },
                            label = { Text(tokens, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // SECTION 3: SYSTEM PROMPT / INSTRUCTIONS
            SettingsCard(
                icon = Icons.Default.Terminal,
                title = "Global Instructions",
                subtitle = "Custom prompt appended to all sessions across your projects"
            ) {
                OutlinedTextField(
                    value = state.systemPrompt,
                    onValueChange = { viewModel.updateSystemPrompt(it) },
                    label = { Text("System Prompt (optional)") },
                    placeholder = { Text("e.g. Always write idiomatic Kotlin. Keep diffs concise and test your logic.") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick prompt templates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            val addition = "Follow modern Android architecture with Kotlin, Jetpack Compose, Coroutines, and Hilt."
                            viewModel.updateSystemPrompt(
                                if (state.systemPrompt.isBlank()) addition else "${state.systemPrompt}\n$addition"
                            )
                        },
                        label = { Text("+ Android Expert", style = MaterialTheme.typography.labelSmall) }
                    )
                    SuggestionChip(
                        onClick = {
                            val addition = "Be concise and accurate. Do not modify existing comments or delete unchanged code."
                            viewModel.updateSystemPrompt(
                                if (state.systemPrompt.isBlank()) addition else "${state.systemPrompt}\n$addition"
                            )
                        },
                        label = { Text("+ Concise & Safe", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // SECTION 4: STORAGE & PRIVACY INFO
            SettingsCard(
                icon = Icons.Default.Info,
                title = "About & Local Privacy",
                subtitle = "Storage & security overview"
            ) {
                Text(
                    text = "• All project files, session chats, and history are saved strictly on your device.\n• API keys are encrypted in Android EncryptedSharedPreferences.\n• No analytics or remote telemetry are collected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // SAVE BUTTON
            Button(
                onClick = { viewModel.saveSettings() },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Settings", style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            content()
        }
    }
}
