package com.koda.dev.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koda.dev.core.data.sync.SyncAccountInfo
import com.koda.dev.core.model.PopularProvider
import com.koda.dev.core.model.PopularProviders
import com.koda.dev.core.model.ProviderType
import com.koda.dev.core.model.WebSearchEngine
import com.koda.dev.core.ui.glass.BackdropState
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.captureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop
import com.koda.dev.core.ui.glass.rememberBackdropState
import com.koda.dev.core.ui.glass.springPressClickable
import com.koda.dev.feature.settings.qr.QrScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var providerToDelete by remember { mutableStateOf<ProviderUiModel?>(null) }
    var preferencesExpanded by remember { mutableStateOf(false) }
    var terminalPreferencesExpanded by remember { mutableStateOf(true) }
    var webSearchExpanded by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(state.userMessage) {
        val msg = state.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissUserMessage()
        }
    }

    // Dynamic heights of floating liquid glass capsules
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }

    // Hardware backdrop capture state for optical lens blur and refraction
    val backdropState = rememberBackdropState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)) // Deep optical slate canvas
    ) {
        // Scrolling Settings Content Stream (captured into backdrop state for real-time glass refraction)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(backdropState)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 8.dp else 90.dp,
                    bottom = 90.dp // Clearance for floating bottom navigation bar
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ACTIVE PROVIDER BANNER
            ActiveProviderBanner(
                activeProvider = state.activeProvider,
                onEditClick = { active ->
                    viewModel.openEditProviderDialog(active)
                },
                onQuickModelSelect = { providerId, model ->
                    viewModel.quickSelectModel(providerId, model)
                }
            )

            // 2. CONFIGURED PROVIDERS SECTION
            ProvidersSection(
                providers = state.providers,
                activeProviderId = state.activeProvider?.id,
                onSelectActive = { viewModel.setActiveProvider(it) },
                onEdit = { viewModel.openEditProviderDialog(it) },
                onDelete = { providerToDelete = it },
                onAddProvider = { viewModel.openAddProviderDialog(PopularProviders.KILO_FREE) },
                onAddSpecificPreset = { preset -> viewModel.openAddProviderDialog(preset) },
                onScanQrCode = { showQrScanner = true }
            )

            // 3. AGENT PREFERENCES (COLLAPSIBLE / ACCORDION)
            AgentPreferencesCard(
                isExpanded = preferencesExpanded,
                onToggleExpand = { preferencesExpanded = !preferencesExpanded },
                temperature = state.globalTemperature,
                onTemperatureChange = { viewModel.updateGlobalTemperature(it) },
                maxTokens = state.globalMaxTokens,
                onMaxTokensChange = { viewModel.updateGlobalMaxTokens(it) },
                systemPrompt = state.globalSystemPrompt,
                onSystemPromptChange = { viewModel.updateGlobalSystemPrompt(it) }
            )

            // 4. TERMINAL DISPLAY & FONT SIZE PREFERENCES
            TerminalPreferencesCard(
                isExpanded = terminalPreferencesExpanded,
                onToggleExpand = { terminalPreferencesExpanded = !terminalPreferencesExpanded },
                fontSizeSp = state.terminalFontSizeSp,
                onFontSizeChange = { viewModel.updateTerminalFontSize(it) }
            )

            // 5. WEB SEARCH & LIVE DOCUMENTATION PREFERENCES
            WebSearchPreferencesCard(
                isExpanded = webSearchExpanded,
                onToggleExpand = { webSearchExpanded = !webSearchExpanded },
                enabled = state.webSearchEnabled,
                onEnabledChange = { viewModel.updateWebSearchEnabled(it) },
                currentEngine = state.webSearchEngine,
                onEngineChange = { viewModel.updateWebSearchEngine(it) },
                searxngUrl = state.searxngUrl,
                onSearxngUrlChange = { viewModel.updateSearxngUrl(it) },
                apiKey = state.searchApiKey,
                onSaveApiKey = { engine, key -> viewModel.saveSearchApiKey(engine, key) }
            )

            // 6. CROSS-DEVICE CLOUD SYNC CARD
            CloudSyncCard(
                syncAccount = state.syncAccount,
                isSyncing = state.isSyncing,
                onConnectClick = { viewModel.openConnectSyncDialog() },
                onSyncClick = { viewModel.triggerCloudSync() },
                onRestoreClick = { viewModel.triggerCloudRestore() },
                onDisconnectClick = { viewModel.disconnectSync() },
                onBackupClick = { viewModel.openBackupDialog() }
            )

            // 7. STORAGE & PRIVACY INFO
            AboutPrivacyCard()

            Spacer(Modifier.height(16.dp))
        }

        // Floating Top Bar: 24dp curved Liquid Glass capsule floating over settings
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Frosted Tune Avatar Disc
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
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF58A6FF)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC),
                                maxLines = 1
                            )
                            Text(
                                text = "Providers · Agent · Sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick QR Scanner Button
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
                                .springPressClickable { showQrScanner = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = Color(0xFF79C0FF),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Quick Add Provider Button
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                .border(
                                    width = 0.75.dp,
                                    brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                                    shape = CircleShape
                                )
                                .springPressClickable { viewModel.openAddProviderDialog(PopularProviders.KILO_FREE) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Provider",
                                tint = Color(0xFFF0F6FC),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }

    // CONNECT SYNC DIALOG
    if (state.isConnectDialogOpen) {
        ConnectSyncDialog(
            isSyncing = state.isSyncing,
            deviceAuthState = state.deviceAuthState,
            errorMessage = state.syncError,
            onDismiss = { viewModel.closeConnectSyncDialog() },
            onStartDeviceFlow = { passphrase, clientId -> viewModel.startGitHubDeviceFlow(passphrase, clientId) },
            onCheckDeviceFlowNow = { viewModel.checkGitHubDeviceAuthNow() },
            onCancelDeviceFlow = { viewModel.cancelGitHubDeviceFlow() },
            onManualConnect = { token, passphrase -> viewModel.connectSync(token, passphrase) }
        )
    }

    // PASSPHRASE PROMPT DIALOG
    val pendingAction = state.pendingSyncAction
    if (state.isPassphrasePromptOpen && pendingAction != null) {
        PassphrasePromptDialog(
            action = pendingAction,
            isSyncing = state.isSyncing,
            onDismiss = { viewModel.closePassphrasePrompt() },
            onSubmit = { passphrase -> viewModel.submitPassphrasePrompt(passphrase) }
        )
    }

    // OFFLINE BACKUP / RESTORE DIALOG
    if (state.isBackupDialogOpen) {
        BackupDialog(
            exportJsonText = state.backupExportText,
            errorMessage = state.syncError,
            onDismiss = { viewModel.closeBackupDialog() },
            onExport = { passphrase -> viewModel.exportEncryptedBackup(passphrase) },
            onImport = { json, passphrase -> viewModel.importEncryptedBackup(json, passphrase) }
        )
    }

    // ADD / EDIT PROVIDER DIALOG
    if (state.editorState.isOpen) {
        ProviderEditorDialog(
            editorState = state.editorState,
            isSaving = state.isSaving,
            onDismiss = { viewModel.closeEditor() },
            onSelectTemplate = { viewModel.selectPresetTemplate(it) },
            onNameChange = { viewModel.updateEditorName(it) },
            onBaseUrlChange = { viewModel.updateEditorBaseUrl(it) },
            onApiKeyChange = { viewModel.updateEditorApiKey(it) },
            onModelChange = { viewModel.updateEditorModel(it) },
            onProviderTypeChange = { viewModel.updateEditorProviderType(it) },
            onIsDefaultChange = { viewModel.updateEditorIsDefault(it) },
            onFetchModels = { viewModel.fetchModelsForEditor() },
            onSave = { viewModel.saveProviderFromEditor() },
            onScanQrCode = { showQrScanner = true }
        )
    }

    // QR SCANNER DIALOG
    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { rawCode ->
                showQrScanner = false
                viewModel.onQrCodeScanned(rawCode)
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    val deletingProvider = providerToDelete
    if (deletingProvider != null) {
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Provider?") },
            text = {
                Text(
                    "Are you sure you want to delete '${deletingProvider.name}' and its stored API key? " +
                    if (deletingProvider.isDefault) "Another provider will automatically become active." else ""
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProvider(deletingProvider.id)
                        providerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveProviderBanner(
    activeProvider: ProviderUiModel?,
    onEditClick: (ProviderUiModel) -> Unit,
    onQuickModelSelect: (String, String) -> Unit
) {
    val bannerShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1B2434).copy(alpha = 0.88f),
                        Color(0xFF101622).copy(alpha = 0.94f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f),
                shape = bannerShape
            )
    ) {
        // Internal Top Sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(GlassDesign.internalGlassSheen())
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Active Provider",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF58A6FF)
                    )
                }

                if (activeProvider != null) {
                    FilledTonalButton(
                        onClick = { onEditClick(activeProvider) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF21262D),
                            contentColor = Color(0xFFF0F6FC)
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (activeProvider != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProvider.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF0F6FC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeProvider.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val isKiloFree = activeProvider.id.startsWith("kilo") || activeProvider.id.startsWith("community") || activeProvider.baseUrl.contains("kilo.ai")
                    // Key Status Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isKiloFree || activeProvider.hasApiKey) {
                            Color(0xFF238636).copy(alpha = 0.20f)
                        } else {
                            Color(0xFFDA3633).copy(alpha = 0.20f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isKiloFree || activeProvider.hasApiKey) Icons.Default.CheckCircle else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (isKiloFree || activeProvider.hasApiKey) Color(0xFF3FB950) else Color(0xFFF85149),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isKiloFree) "Keyless Free" else if (activeProvider.hasApiKey) "Key configured" else "No key set",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isKiloFree || activeProvider.hasApiKey) Color(0xFF3FB950) else Color(0xFFF85149),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Selected Model Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D1117).copy(alpha = 0.85f),
                    border = BorderStroke(0.75.dp, Color(0xFF30363D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Model:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = activeProvider.model.ifBlank { "(None selected)" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFF0F6FC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = "No active provider selected. Add or select a provider below to enable coding assistance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProvidersSection(
    providers: List<ProviderUiModel>,
    activeProviderId: String?,
    onSelectActive: (String) -> Unit,
    onEdit: (ProviderUiModel) -> Unit,
    onDelete: (ProviderUiModel) -> Unit,
    onAddProvider: () -> Unit,
    onAddSpecificPreset: (PopularProvider) -> Unit,
    onScanQrCode: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI Providers",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (providers.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) { Text(providers.size.toString()) }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onScanQrCode,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scan QR", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onAddProvider,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Provider", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (providers.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No providers configured",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Choose a popular provider to get started quickly:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = onScanQrCode,
                            label = { Text("Scan Antiroute QR", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                        PopularProviders.allPopular.filterNot { it.isCustom }.forEach { preset ->
                            AssistChip(
                                onClick = { onAddSpecificPreset(preset) },
                                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }
        } else {
            providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = provider.id == activeProviderId,
                    onSelect = { onSelectActive(provider.id) },
                    onEdit = { onEdit(provider) },
                    onDelete = { onDelete(provider) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderUiModel,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                if (isActive) Brush.verticalGradient(
                    listOf(
                        Color(0xFF1B2434).copy(alpha = 0.88f),
                        Color(0xFF101622).copy(alpha = 0.94f)
                    )
                ) else Brush.verticalGradient(
                    listOf(
                        Color(0xFF161B22).copy(alpha = 0.75f),
                        Color(0xFF0D1117).copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = if (isActive) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f)
                else GlassDesign.microGlassBorder(topAlpha = 0.24f, bottomAlpha = 0.06f),
                shape = cardShape
            )
            .springPressClickable(onClick = onSelect)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(GlassDesign.internalGlassSheen())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radio Indicator
            RadioButton(
                selected = isActive,
                onClick = onSelect
            )

            // Provider Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF0F6FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1F6FEB).copy(alpha = 0.25f),
                            border = BorderStroke(0.75.dp, Color(0xFF388BFD))
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF79C0FF),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Model, Type & Endpoint
                val isKiloFree = provider.id.startsWith("kilo") || provider.id.startsWith("community") || provider.baseUrl.contains("kilo.ai")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (provider.providerType == ProviderType.ANTIROUTE || provider.id.startsWith("antiroute")) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF238636).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "ANTIROUTE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF3FB950),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else if (isKiloFree) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF238636).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "FREE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF3FB950),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF21262D),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = provider.model.ifBlank { "No model" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC9D1D9),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    Text(
                        text = if (isKiloFree) "• Keyless Free" else if (provider.hasApiKey) "• Key set" else "• No key",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isKiloFree) Color(0xFF3FB950) else if (provider.hasApiKey) Color(0xFF8B949E) else Color(0xFFF85149),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f),
                            shape = CircleShape
                        )
                        .springPressClickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit provider",
                        tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f),
                            shape = CircleShape
                        )
                        .springPressClickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete provider",
                        tint = Color(0xFFF85149).copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentPreferencesCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    maxTokens: String,
    onMaxTokensChange: (String) -> Unit,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
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
            // Header Row (Toggleable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Agent Preferences",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Temperature, output tokens, system prompt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                                    text = String.format("%.2f", temperature),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Max Tokens
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = maxTokens,
                            onValueChange = onMaxTokensChange,
                            label = { Text("Max Output Tokens") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
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

                    // Global Instructions / System Prompt
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = onSystemPromptChange,
                            label = { Text("Global System Instructions (optional)") },
                            placeholder = { Text("e.g. Always write idiomatic Kotlin. Keep diffs concise.") },
                            minLines = 3,
                            maxLines = 5,
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
            }
        }
    }
}

@Composable
private fun TerminalPreferencesCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit
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
            // Header Row (Toggleable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Terminal Display & Font",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Text scale, readability & terminal zoom ($fontSizeSp sp)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Font Size Steppers + Slider
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            val label = when {
                                fontSizeSp <= 11 -> "Compact"
                                fontSizeSp == 14 -> "Default"
                                fontSizeSp in 15..17 -> "Medium"
                                fontSizeSp in 18..21 -> "Large"
                                else -> "Extra Large"
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "$fontSizeSp sp · $label",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { onFontSizeChange(fontSizeSp - 1) },
                                enabled = fontSizeSp > 9,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease font size", modifier = Modifier.size(18.dp))
                            }

                            Slider(
                                value = fontSizeSp.toFloat(),
                                onValueChange = { onFontSizeChange(it.toInt()) },
                                valueRange = 9f..28f,
                                steps = 18,
                                modifier = Modifier.weight(1f)
                            )

                            FilledTonalIconButton(
                                onClick = { onFontSizeChange(fontSizeSp + 1) },
                                enabled = fontSizeSp < 28,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase font size", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Presets
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
                                FilterChip(
                                    selected = fontSizeSp == size,
                                    onClick = { onFontSizeChange(size) },
                                    label = { Text("$size sp ($presetLabel)", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Live Interactive Preview Box
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Live Terminal Preview",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = Color(0xFF0D1117),
                            shape = RoundedCornerShape(8.dp),
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
                                    text = "drwxr-xr-x 4 user user 4096 Oct 2 17:40 src/\n-rw-r--r-- 1 user user 1820 Oct 2 17:40 main.py",
                                    color = Color(0xFFC9D1D9),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "koda:~/workspace$ ",
                                        color = Color(0xFF39D353),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "python3 main.py",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp
                                    )
                                }
                                Text(
                                    text = "Ready to code! Terminal render OK.",
                                    color = Color(0xFF58A6FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tip: Inside the terminal screen, you can also pinch-to-zoom with two fingers or tap the 'TT' text size icon in the top bar to adjust text on the fly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutPrivacyCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Security & Storage Overview",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text(
                text = "• API keys are encrypted via Android EncryptedSharedPreferences (Hardware Keystore).\n• Each provider maintains its own separate API key and endpoint configuration.\n• Projects, code, and chat history remain strictly local on device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    editorState: ProviderEditorState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (PopularProvider) -> Unit,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onProviderTypeChange: (ProviderType) -> Unit,
    onIsDefaultChange: (Boolean) -> Unit,
    onFetchModels: () -> Unit,
    onSave: () -> Unit,
    onScanQrCode: () -> Unit = {}
) {
    var showApiKey by remember { mutableStateOf(false) }
    var showModelSelectorSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentTemplate = PopularProviders.findById(editorState.selectedTemplateId)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editorState.isEditing) "Edit AI Provider" else "Add AI Provider",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Popular Providers Preset Selector
                    Text(
                        text = "Choose Provider Preset",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PopularProviders.allPopular.forEach { preset ->
                            val isSelected = editorState.selectedTemplateId == preset.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectTemplate(preset) },
                                label = { Text(preset.name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    // Antiroute QR Scan Action
                    if (editorState.selectedTemplateId == "antiroute" || editorState.providerType == ProviderType.ANTIROUTE) {
                        OutlinedButton(
                            onClick = onScanQrCode,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Antiroute Tunnel QR Code", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Provider Name
                    OutlinedTextField(
                        value = editorState.name,
                        onValueChange = onNameChange,
                        label = { Text("Provider Name") },
                        placeholder = { Text("e.g. OpenAI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Base URL
                    OutlinedTextField(
                        value = editorState.baseUrl,
                        onValueChange = onBaseUrlChange,
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = onScanQrCode) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Protocol (if custom)
                    if (editorState.selectedTemplateId == "custom") {
                        Text(
                            text = "API Protocol",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = editorState.providerType == ProviderType.OPENAI_COMPATIBLE,
                                onClick = { onProviderTypeChange(ProviderType.OPENAI_COMPATIBLE) },
                                label = { Text("OpenAI Compatible") }
                            )
                            FilterChip(
                                selected = editorState.providerType == ProviderType.ANTHROPIC,
                                onClick = { onProviderTypeChange(ProviderType.ANTHROPIC) },
                                label = { Text("Anthropic") }
                            )
                        }
                    }

                    // API Key
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = editorState.apiKey,
                            onValueChange = onApiKeyChange,
                            label = { Text("API Key") },
                            placeholder = { Text(currentTemplate?.apiKeyPlaceholder ?: "API Key") },
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        val helpUrl = currentTemplate?.apiKeyHelpUrl
                        val isKiloFree = editorState.selectedTemplateId == "kilo_free" || editorState.selectedTemplateId == "community_free" || editorState.baseUrl.contains("kilo.ai")
                        if (isKiloFree) {
                            Text(
                                text = "No API key required. Connects directly to Kilo free models (~200 req/hr per IP).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (!helpUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Get API key from ${currentTemplate.name}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // DYNAMIC MODEL SELECTION & FETCH
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Model Selection",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )

                                FilledTonalButton(
                                    onClick = onFetchModels,
                                    enabled = !editorState.isFetchingModels && editorState.baseUrl.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (editorState.isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Fetching…", style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Fetch Models", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Chosen Model Display / Picker Button
                            OutlinedTextField(
                                value = editorState.model,
                                onValueChange = onModelChange,
                                label = { Text("Selected Model") },
                                placeholder = { Text("e.g. gpt-4o") },
                                trailingIcon = {
                                    if (editorState.availableModels.isNotEmpty()) {
                                        IconButton(onClick = { showModelSelectorSheet = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose model")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick Model Suggestions Chips
                            if (editorState.availableModels.isNotEmpty()) {
                                Text(
                                    text = "Available Models (${editorState.availableModels.size}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    editorState.availableModels.take(8).forEach { modelName ->
                                        val isChosen = editorState.model == modelName
                                        FilterChip(
                                            selected = isChosen,
                                            onClick = { onModelChange(modelName) },
                                            label = { Text(modelName, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                    if (editorState.availableModels.size > 8) {
                                        AssistChip(
                                            onClick = { showModelSelectorSheet = true },
                                            label = { Text("+${editorState.availableModels.size - 8} more", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            // Connection / Fetch Result Banner
                            if (editorState.connectionTestMessage != null) {
                                val isSuccess = editorState.connectionTestSuccess == true
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = editorState.connectionTestMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Set as active provider switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Set as active provider",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Switch(
                            checked = editorState.isDefault,
                            onCheckedChange = onIsDefaultChange
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = !isSaving && editorState.name.isNotBlank() && editorState.baseUrl.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Saving…")
                        } else {
                            Text("Save Provider")
                        }
                    }
                }
            }
        }
    }

    // FULL MODEL SELECTOR DIALOG
    if (showModelSelectorSheet && editorState.availableModels.isNotEmpty()) {
        ModelSelectorDialog(
            models = editorState.availableModels,
            selectedModel = editorState.model,
            onSelect = {
                onModelChange(it)
                showModelSelectorSheet = false
            },
            onDismiss = { showModelSelectorSheet = false }
        )
    }
}

@Composable
private fun ModelSelectorDialog(
    models: List<String>,
    selectedModel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(searchQuery, models) {
        if (searchQuery.isBlank()) models
        else models.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Model (${models.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredModels) { modelName ->
                        val isSelected = modelName == selectedModel
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(modelName) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebSearchPreferencesCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Web Search & Live Docs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (enabled) "${currentEngine.displayName} · Active" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Web Access",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Allow agent to search online docs and fetch web pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange
                        )
                    }

                    if (enabled) {
                        Text(
                            text = "Search Engine Provider",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
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
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "DuckDuckGo is completely free, privacy-first, and works instantly without needing any API key or subscription.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                Button(
                                    onClick = { onSaveApiKey(currentEngine, keyInput) },
                                    modifier = Modifier.align(Alignment.End),
                                    enabled = keyInput.isNotBlank()
                                ) {
                                    Text("Save Key")
                                }
                            }
                            WebSearchEngine.SEARXNG -> {
                                OutlinedTextField(
                                    value = searxngUrl,
                                    onValueChange = onSearxngUrlChange,
                                    label = { Text("SearXNG Instance URL") },
                                    placeholder = { Text("https://searx.example.com") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncCard(
    syncAccount: SyncAccountInfo,
    isSyncing: Boolean,
    onConnectClick: () -> Unit,
    onSyncClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Icon + Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Cross-Device Sync",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Zero-Knowledge E2EE (GitHub Gist)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (syncAccount.isConnected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (syncAccount.isConnected) {
                // Connected info block
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${syncAccount.username}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onDisconnectClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Disconnect",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Action buttons: Sync Now & Restore
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
                            Text(
                                text = "Sync Now",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreClick,
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Restore",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                // Not connected: description & Connect button
                Text(
                    text = "Sync your AI providers and API keys securely across devices using your private GitHub Gist. All data is encrypted with AES-256-GCM using your secret passphrase before leaving your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Offline Backup / Restore Link
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
    }
}

@Composable
private fun ConnectSyncDialog(
    isSyncing: Boolean,
    deviceAuthState: GitHubDeviceAuthState,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onStartDeviceFlow: (passphrase: String, clientId: String?) -> Unit,
    onCheckDeviceFlowNow: () -> Unit,
    onCancelDeviceFlow: () -> Unit,
    onManualConnect: (token: String, passphrase: String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var showManualToken by remember { mutableStateOf(false) }
    var customClientId by remember { mutableStateOf("") }
    var showAdvancedOAuth by remember { mutableStateOf(false) }
    var localValidation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var codeCopied by remember { mutableStateOf(false) }

    // When a user code arrives from GitHub Device Flow, copy it to clipboard and open browser!
    LaunchedEffect(deviceAuthState.userCode) {
        val code = deviceAuthState.userCode
        if (!code.isNullOrBlank()) {
            clipboardManager.setText(AnnotatedString(code))
            codeCopied = true
            val uri = deviceAuthState.verificationUri ?: "https://github.com/login/device"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)
            } catch (_: Exception) {
                // Ignore if browser cannot be launched automatically
            }
        }
    }

    Dialog(onDismissRequest = {
        if (deviceAuthState.isAuthorizing) {
            onCancelDeviceFlow()
        }
        onDismiss()
    }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "GitHub Cloud Sync",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (!deviceAuthState.isAuthorizing) {
                    PrimaryTabRow(selectedTabIndex = tabIndex) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = { Text("1-Tap Browser Sign-In") }
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("Personal Token") }
                        )
                    }
                }

                if (deviceAuthState.isAuthorizing) {
                    // ACTIVE DEVICE FLOW IN PROGRESS
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Your GitHub Authorization Code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val code = deviceAuthState.userCode ?: "..."
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            Text(
                                text = if (codeCopied) "Code copied to clipboard! Paste it on GitHub." else "Copy the code and enter it on GitHub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(code))
                                        codeCopied = true
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy Code")
                                }

                                Button(
                                    onClick = {
                                        val uri = deviceAuthState.verificationUri ?: "https://github.com/login/device"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open GitHub")
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Immediate Check button so user can trigger verification directly upon returning from browser
                            Button(
                                onClick = onCheckDeviceFlowNow,
                                enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("I've Approved on GitHub (Check Now)")
                            }

                            val activeErr = deviceAuthState.error ?: errorMessage
                            if (!activeErr.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = activeErr,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (deviceAuthState.isPolling && activeErr.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Waiting for authorization in browser...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onCancelDeviceFlow) {
                            Text("Cancel")
                        }
                    }
                } else if (tabIndex == 0) {
                    // TAB 0: 1-TAP BROWSER SIGN-IN
                    Text(
                        text = "Sign in via GitHub in your browser with zero manual token generation. Set your encryption passphrase below to secure your API keys with AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Sync Passphrase Field
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            localValidation = null
                        },
                        label = { Text("Encryption Passphrase (min 6 chars)") },
                        placeholder = { Text("Secret passphrase for E2EE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Hide" else "Show"
                                )
                            }
                        }
                    )

                    // Confirm Passphrase Field
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = {
                            confirmPassphrase = it
                            localValidation = null
                        },
                        label = { Text("Confirm Encryption Passphrase") },
                        placeholder = { Text("Re-enter your secret passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation()
                    )

                    // Expandable Advanced OAuth Configuration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedOAuth = !showAdvancedOAuth }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "OAuth App Client ID (Advanced)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (showAdvancedOAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedOAuth) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "GitHub Device Flow requires a registered GitHub OAuth App with 'Enable Device Flow' checked. Enter your Client ID below, or create one for free.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = customClientId,
                                onValueChange = { customClientId = it },
                                label = { Text("Custom GitHub OAuth Client ID") },
                                placeholder = { Text(com.koda.dev.core.data.sync.GitHubGistSyncClient.DEFAULT_CLIENT_ID) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    val url = "https://github.com/settings/applications/new"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Register Free OAuth App on GitHub", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    val activeError = localValidation ?: errorMessage
                    if (!activeError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isSyncing) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (passphrase.length < 6) {
                                    localValidation = "Passphrase must be at least 6 characters"
                                    return@Button
                                }
                                if (passphrase != confirmPassphrase) {
                                    localValidation = "Passphrases do not match"
                                    return@Button
                                }
                                onStartDeviceFlow(passphrase, customClientId.trim().ifBlank { null })
                            },
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sign in with GitHub")
                        }
                    }
                } else {
                    // TAB 1: MANUAL TOKEN
                    Text(
                        text = "Instant 10-second setup with 100% reliability! Paste an existing GitHub Personal Access Token (PAT) with 'gist' scope.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            val url = "https://github.com/settings/tokens/new?scopes=gist&description=KodaDev%20Sync"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Create Token on GitHub (gist scope)", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = {
                            manualToken = it
                            localValidation = null
                        },
                        label = { Text("GitHub Personal Access Token") },
                        placeholder = { Text("ghp_xxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showManualToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showManualToken = !showManualToken }) {
                                Icon(
                                    imageVector = if (showManualToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showManualToken) "Hide" else "Show"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            localValidation = null
                        },
                        label = { Text("Encryption Passphrase (min 6 chars)") },
                        placeholder = { Text("Secret passphrase for E2EE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Hide" else "Show"
                                )
                            }
                        }
                    )

                    val activeError = localValidation ?: errorMessage
                    if (!activeError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isSyncing) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (manualToken.isBlank()) {
                                    localValidation = "Token cannot be empty"
                                    return@Button
                                }
                                if (passphrase.length < 6) {
                                    localValidation = "Passphrase must be at least 6 characters"
                                    return@Button
                                }
                                onManualConnect(manualToken, passphrase)
                            },
                            enabled = !isSyncing
                        ) {
                            Text("Connect & Sync")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassphrasePromptDialog(
    action: SyncAction,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (action == SyncAction.SYNC) "Encrypt & Sync Vault" else "Decrypt & Restore Vault",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = if (action == SyncAction.SYNC)
                        "Enter your sync passphrase to encrypt your provider keys before uploading to GitHub."
                    else
                        "Enter your sync passphrase to decrypt your providers from GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Hide" else "Show"
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSyncing) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(passphrase) },
                        enabled = passphrase.isNotBlank() && !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (action == SyncAction.SYNC) "Sync" else "Restore")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupDialog(
    exportJsonText: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onExport: (passphrase: String) -> Unit,
    onImport: (json: String, passphrase: String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var passphrase by remember { mutableStateOf("") }
    var importJson by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Encrypted Vault Backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                PrimaryTabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("Export") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("Import") }
                    )
                }

                if (tabIndex == 0) {
                    // EXPORT TAB
                    Text(
                        text = "Generate a zero-knowledge encrypted backup payload. You can transfer this text to another device offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption Passphrase (min 6 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Button(
                        onClick = { onExport(passphrase) },
                        enabled = passphrase.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Encrypted Payload")
                    }

                    if (!exportJsonText.isNullOrBlank()) {
                        OutlinedTextField(
                            value = exportJsonText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Encrypted Payload JSON") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(exportJsonText))
                                copied = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copied to Clipboard!" else "Copy Encrypted Payload")
                        }
                    }
                } else {
                    // IMPORT TAB
                    Text(
                        text = "Paste an encrypted backup payload from another device and provide the passphrase used to encrypt it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text("Encrypted Payload JSON") },
                        placeholder = { Text("Paste payload JSON here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Decryption Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Button(
                        onClick = { onImport(importJson, passphrase) },
                        enabled = importJson.isNotBlank() && passphrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Decrypt & Restore Providers")
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
