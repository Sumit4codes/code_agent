package com.koda.dev.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koda.dev.core.data.sync.SyncAccountInfo
import com.koda.dev.core.model.WebSearchEngine
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

internal enum class SettingsDetailSheet {
    AGENT,
    TERMINAL,
    WEB_SEARCH,
    SYNC,
    ABOUT
}

@Composable
internal fun SettingsPreferenceRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .springPressClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                .border(
                    width = 0.75.dp,
                    brush = GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF0F6FC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B949E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF6E7681),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SettingsDetailDialog(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val dialogShape = RoundedCornerShape(24.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = dialogShape,
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x800F172A)
                )
                .clip(dialogShape)
                .background(GlassDesign.liquidGlassBackground())
                .border(
                    width = 1.dp,
                    brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.35f),
                    shape = dialogShape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.45f, bottomAlpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), CircleShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun AgentPreferencesDialogContent(
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    maxTokens: String,
    onMaxTokensChange: (String) -> Unit,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
) {
    val textFieldColors = darkSettingsFieldColors()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Temperature",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF0F6FC)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F6FEB).copy(alpha = 0.20f))
                    .border(0.75.dp, Color(0xFF388BFD).copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format("%.2f", temperature),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF79C0FF)
                )
            }
        }
        Slider(
            value = temperature,
            onValueChange = onTemperatureChange,
            valueRange = 0.0f..1.0f,
            steps = 19
        )
        Text(
            text = "0.0 = Deterministic code · 1.0 = Creative reasoning",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B949E)
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = maxTokens,
            onValueChange = onMaxTokensChange,
            label = { Text("Max Output Tokens") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("2048", "4096", "8192", "16384").forEach { tokens ->
                SuggestionChip(
                    onClick = { onMaxTokensChange(tokens) },
                    label = { Text(tokens, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            label = { Text("Global System Instructions (optional)") },
            placeholder = { Text("e.g. Always write idiomatic Kotlin. Keep diffs concise.") },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip(
                onClick = {
                    val add = "Follow modern Android architecture with Kotlin, Jetpack Compose, Coroutines, and Hilt."
                    onSystemPromptChange(if (systemPrompt.isBlank()) add else "$systemPrompt\n$add")
                },
                label = { Text("+ Android Expert", style = MaterialTheme.typography.labelSmall) }
            )
            SuggestionChip(
                onClick = {
                    val add = "Be concise and accurate. Do not modify comments or delete unchanged code."
                    onSystemPromptChange(if (systemPrompt.isBlank()) add else "$systemPrompt\n$add")
                },
                label = { Text("+ Concise & Safe", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
internal fun TerminalPreferencesDialogContent(
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF0F6FC)
            )
            val label = when {
                fontSizeSp <= 11 -> "Compact"
                fontSizeSp == 14 -> "Default"
                fontSizeSp in 15..17 -> "Medium"
                fontSizeSp in 18..21 -> "Large"
                else -> "Extra Large"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F6FEB).copy(alpha = 0.20f))
                    .border(0.75.dp, Color(0xFF388BFD).copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$fontSizeSp sp · $label",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF79C0FF)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                        shape = CircleShape
                    )
                    .springPressClickable(enabled = fontSizeSp > 9) { onFontSizeChange(fontSizeSp - 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease font size",
                    tint = if (fontSizeSp > 9) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }

            Slider(
                value = fontSizeSp.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 9f..28f,
                steps = 18,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                        shape = CircleShape
                    )
                    .springPressClickable(enabled = fontSizeSp < 28) { onFontSizeChange(fontSizeSp + 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase font size",
                    tint = if (fontSizeSp < 28) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                11 to "Compact",
                14 to "Default",
                16 to "Medium",
                18 to "Large",
                22 to "Huge"
            ).forEach { (size, presetLabel) ->
                val isSelected = fontSizeSp == size
                val chipShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .then(
                            if (isSelected) Modifier.background(Color(0xFF1F6FEB).copy(alpha = 0.35f))
                            else Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f))
                        )
                        .border(
                            width = 0.75.dp,
                            brush = if (isSelected) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.75f, dispersionAlpha = 0.35f)
                            else GlassDesign.microGlassBorder(topAlpha = 0.22f, bottomAlpha = 0.06f),
                            shape = chipShape
                        )
                        .springPressClickable { onFontSizeChange(size) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$size sp ($presetLabel)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = if (isSelected) Color(0xFF79C0FF) else Color(0xFFC9D1D9)
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF8B949E)
        )
        Surface(
            color = Color(0xFF0D1117),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.75.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "koda:~/workspace$ ",
                        color = Color(0xFF39D353),
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ls -la",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp
                    )
                }
                Text(
                    text = "drwxr-xr-x 4 user user 4096 src/\n-rw-r--r-- 1 user user 1820 main.py",
                    color = Color(0xFFC9D1D9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WebSearchPreferencesDialogContent(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    currentEngine: WebSearchEngine,
    onEngineChange: (WebSearchEngine) -> Unit,
    searxngUrl: String,
    onSearxngUrlChange: (String) -> Unit,
    apiKey: String,
    onSaveApiKey: (WebSearchEngine, String) -> Unit
) {
    var keyInput by remember(apiKey, currentEngine) { mutableStateOf(apiKey) }
    var showPassword by remember { mutableStateOf(false) }
    val textFieldColors = darkSettingsFieldColors()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable Web Access",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF0F6FC)
            )
            Text(
                text = "Allow agent to search online docs and fetch web pages",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B949E)
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }

    if (enabled) {
        Text(
            text = "Search Engine",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF58A6FF)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WebSearchEngine.entries.forEach { engine ->
                FilterChip(
                    selected = currentEngine == engine,
                    onClick = { onEngineChange(engine) },
                    label = { Text(engine.displayName) },
                    leadingIcon = if (currentEngine == engine) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        when (currentEngine) {
            WebSearchEngine.DUCKDUCKGO -> {
                Surface(
                    color = Color(0xFF161B22),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.75.dp, Color(0xFF30363D))
                ) {
                    Text(
                        text = "DuckDuckGo is free and works without an API key.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF8B949E)
                    )
                }
            }
            WebSearchEngine.TAVILY, WebSearchEngine.BRAVE -> {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("${currentEngine.displayName} API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide key" else "Show key"
                            )
                        }
                    }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { onSaveApiKey(currentEngine, keyInput) },
                        enabled = keyInput.isNotBlank()
                    ) {
                        Text("Save Key")
                    }
                }
            }
            WebSearchEngine.SEARXNG -> {
                OutlinedTextField(
                    value = searxngUrl,
                    onValueChange = onSearxngUrlChange,
                    label = { Text("SearXNG Instance URL") },
                    placeholder = { Text("https://searx.example.com") },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
internal fun CloudSyncDialogContent(
    syncAccount: SyncAccountInfo,
    isSyncing: Boolean,
    onConnectClick: () -> Unit,
    onSyncClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    if (syncAccount.isConnected) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF161B22),
            border = BorderStroke(0.75.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(34.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${syncAccount.username}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF0F6FC)
                    )
                    val lastSyncTime = syncAccount.lastSyncedAt
                    val lastSyncText = if (lastSyncTime != null) {
                        val diff = System.currentTimeMillis() - lastSyncTime
                        when {
                            diff < 60_000 -> "Just now"
                            diff < 3600_000 -> "${diff / 60_000}m ago"
                            else -> "${diff / 3600_000}h ago"
                        }
                    } else {
                        "Never"
                    }
                    Text(
                        text = "Last synced: $lastSyncText",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                }

                IconButton(onClick = onDisconnectClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Disconnect",
                        tint = Color(0xFFF85149)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync Now", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }

            OutlinedButton(
                onClick = onRestoreClick,
                enabled = !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restore", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
        }
    } else {
        Text(
            text = "Sync AI providers and encrypted API keys across devices using a private GitHub Gist (AES-256-GCM).",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B949E)
        )

        Button(
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Connect GitHub Sync")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onBackupClick) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Offline Encrypted Backup", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun AboutPrivacyDialogContent() {
    Text(
        text = "• API keys are encrypted via Android EncryptedSharedPreferences (Hardware Keystore).\n" +
            "• Each provider maintains its own separate API key and endpoint configuration.\n" +
            "• Projects, code, and chat history remain strictly local on device.",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFC9D1D9),
        lineHeight = 20.sp
    )
}

@Composable
private fun darkSettingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF58A6FF),
    unfocusedBorderColor = Color(0xFF30363D),
    focusedContainerColor = Color(0xFF0D1117).copy(alpha = 0.90f),
    unfocusedContainerColor = Color(0xFF0D1117).copy(alpha = 0.75f),
    focusedTextColor = Color(0xFFF0F6FC),
    unfocusedTextColor = Color(0xFFF0F6FC),
    focusedLabelColor = Color(0xFF58A6FF),
    unfocusedLabelColor = Color(0xFF8B949E),
    focusedPlaceholderColor = Color(0xFF6E7681),
    unfocusedPlaceholderColor = Color(0xFF6E7681),
    focusedTrailingIconColor = Color(0xFF58A6FF),
    unfocusedTrailingIconColor = Color(0xFF8B949E)
)
