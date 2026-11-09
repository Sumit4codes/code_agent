package com.koda.dev.feature.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koda.dev.core.model.Project
import com.koda.dev.core.ui.glass.BackdropState
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.captureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop
import com.koda.dev.core.ui.glass.rememberBackdropState
import com.koda.dev.core.ui.glass.springPressClickable
import java.io.File

fun hasFullStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

fun requestFullStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = hiltViewModel(),
    onProjectSelected: ((Project) -> Unit)? = null,
    onChatWithProject: ((Project) -> Unit)? = null
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(hasFullStoragePermission(context)) }
    var showFolderDialog by remember { mutableStateOf(false) }

    // Dynamic heights of floating liquid glass capsules
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    var bottomDockHeightPx by remember { mutableFloatStateOf(0f) }

    // Hardware backdrop capture state for optical lens blur and refraction
    val backdropState = rememberBackdropState()

    LifecycleResumeEffect(Unit) {
        hasPermission = hasFullStoragePermission(context)
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)) // Deep optical slate canvas
    ) {
        // Scrolling Project Content Stream (captured into backdrop state for real-time glass refraction)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(backdropState),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 8.dp else 90.dp,
                bottom = if (bottomDockHeightPx > 0f) with(LocalDensity.current) { bottomDockHeightPx.toDp() } + 16.dp else 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Permission Alert Banner if full device storage access is missing
            if (!hasPermission) {
                item(key = "permission_banner") {
                    StorageAccessBanner(
                        onRequestPermission = { requestFullStoragePermission(context) }
                    )
                }
            }

            // Empty State
            if (projects.isEmpty()) {
                item(key = "empty_state") {
                    EmptyProjectsState(
                        onOpenFolderClick = {
                            if (!hasPermission) {
                                requestFullStoragePermission(context)
                            } else {
                                showFolderDialog = true
                            }
                        }
                    )
                }
            } else {
                // Section Header
                item(key = "workspace_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RECENT PROJECTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color(0xFF7D8590)
                        )
                        Text(
                            text = "${projects.size} ${if (projects.size == 1) "directory" else "directories"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF58A6FF)
                        )
                    }
                }

                // Project List Items
                items(projects, key = { it.id }) { project ->
                    LiquidGlassProjectItem(
                        project = project,
                        onBrowse = { onProjectSelected?.invoke(project) },
                        onChat = { onChatWithProject?.invoke(project) ?: onProjectSelected?.invoke(project) },
                        onDelete = { viewModel.removeProject(project) }
                    )
                }
            }
        }

        // Floating Top Bar: 24dp curved Liquid Glass capsule floating over scrolling content
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
                                    tint = Color(0xFF58A6FF)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Koda Dev",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC),
                                maxLines = 1
                            )
                            Text(
                                text = "Local AI Workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    // Quick Folder Picker Action
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.08f))
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                                shape = CircleShape
                            )
                            .springPressClickable {
                                if (!hasPermission) {
                                    requestFullStoragePermission(context)
                                } else {
                                    showFolderDialog = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open project folder",
                            tint = Color(0xFFF0F6FC),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }

        // Floating Bottom Dock: 24dp curved Liquid Glass capsule floating over scrolling content
        val bottomDockShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coords ->
                    bottomDockHeightPx = coords.size.height.toFloat()
                }
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Storage access & project status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.05f))
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (!hasPermission) {
                                requestFullStoragePermission(context)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (hasPermission) Color(0xFF238636) else Color(0xFFD29922))
                    )
                    Text(
                        text = if (hasPermission) "Storage Active" else "Grant Storage",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (hasPermission) Color(0xFF3FB950) else Color(0xFFE3B341)
                    )
                }

                // Primary CTA: "+ Open Folder" Action Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF1F6FEB).copy(alpha = 0.40f),
                                    Color(0xFF238636).copy(alpha = 0.30f)
                                )
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.liquidGlassBorder(
                                topSpecularAlpha = 0.70f,
                                dispersionAlpha = 0.35f
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .springPressClickable {
                            if (!hasPermission) {
                                requestFullStoragePermission(context)
                            } else {
                                showFolderDialog = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF79C0FF)
                    )
                    Text(
                        text = "Open Folder",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC)
                    )
                }
            }
        }
    }

    if (showFolderDialog) {
        DirectoryPickerDialog(
            onDismiss = { showFolderDialog = false },
            onFolderSelected = { selectedDir ->
                showFolderDialog = false
                viewModel.addProject(selectedDir.absolutePath, selectedDir.name)
            }
        )
    }
}

/**
 * Exquisite Micro-Glass Project Card with tactile spring press feedback,
 * specular edge lighting, and fast chat/browse actions.
 */
@Composable
private fun LiquidGlassProjectItem(
    project: Project,
    onBrowse: () -> Unit,
    onChat: () -> Unit,
    onDelete: () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF161B22).copy(alpha = 0.88f),
                        Color(0xFF0D1117).copy(alpha = 0.94f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                shape = cardShape
            )
            .springPressClickable(onClick = onChat)
    ) {
        // Internal Top Sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(GlassDesign.internalGlassSheen())
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leading Frosted Chat Disc
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.microGlassBorder(topAlpha = 0.45f, bottomAlpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat with ${project.name}",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF58A6FF)
                )
            }

            // Project Name & Clean Path
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF0F6FC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFF7D8590)
                    )
                    Text(
                        text = project.treeUri.removePrefix("file://"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF8B949E)
                    )
                }
            }

            // Action Buttons (Browse & Delete)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Browse Files Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f),
                            shape = CircleShape
                        )
                        .springPressClickable(onClick = onBrowse),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Browse files",
                        tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete Project Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                        contentDescription = "Delete project",
                        tint = Color(0xFFF85149).copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Storage Permission Warning Banner styled with micro-glass aesthetics.
 */
@Composable
private fun StorageAccessBanner(
    onRequestPermission: () -> Unit
) {
    val bannerShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF382010).copy(alpha = 0.85f),
                        Color(0xFF241508).copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFE3B341).copy(alpha = 0.50f),
                        Color(0xFFD29922).copy(alpha = 0.15f)
                    )
                ),
                shape = bannerShape
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF0883E),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Full Storage Access Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF0F6FC)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Koda Dev needs All Files Access so AI coding agents, terminal tools, and git can read and edit your projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC9D1D9)
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0883E),
                    contentColor = Color(0xFF0D1117)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Grant All Files Access",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Empty Workspace State card styled with micro-glass aesthetics.
 */
@Composable
private fun EmptyProjectsState(
    onOpenFolderClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF161B22).copy(alpha = 0.50f),
                        Color(0xFF0D1117).copy(alpha = 0.60f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = GlassDesign.microGlassBorder(topAlpha = 0.20f, bottomAlpha = 0.05f),
                shape = shape
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                    .border(
                        width = 0.75.dp,
                        brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF79C0FF)
                )
            }

            Text(
                text = "No projects yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF0F6FC)
            )

            Text(
                text = "Tap Open Folder below or + to select a project directory on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onOpenFolderClick,
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.15f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF79C0FF)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Open Folder")
            }
        }
    }
}

@Composable
fun DirectoryPickerDialog(
    onDismiss: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    val defaultDir = remember {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) ext else File("/storage/emulated/0")
    }

    var currentDir by remember { mutableStateOf(defaultDir) }
    var pathInput by remember { mutableStateOf(defaultDir.absolutePath) }

    val subdirectories = remember(currentDir) {
        try {
            currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Project Folder",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF0F6FC)
            )
        },
        containerColor = Color(0xFF161B22),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Direct Path Input
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = {
                        pathInput = it
                        val f = File(it)
                        if (f.exists() && f.isDirectory) {
                            currentDir = f
                        }
                    },
                    label = { Text("Folder Path", color = Color(0xFF8B949E)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF0F6FC)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Quick jump shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ext = Environment.getExternalStorageDirectory()
                    val docs = File(ext, "Documents")
                    val download = File(ext, "Download")

                    SuggestionChip(
                        onClick = {
                            currentDir = ext
                            pathInput = ext.absolutePath
                        },
                        label = { Text("Root", style = MaterialTheme.typography.labelSmall) }
                    )
                    if (docs.exists()) {
                        SuggestionChip(
                            onClick = {
                                currentDir = docs
                                pathInput = docs.absolutePath
                            },
                            label = { Text("Documents", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (download.exists()) {
                        SuggestionChip(
                            onClick = {
                                currentDir = download
                                pathInput = download.absolutePath
                            },
                            label = { Text("Download", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Subdirectories listing
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        // Parent directory item
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentDir = parent
                                            pathInput = parent.absolutePath
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Go up",
                                        tint = Color(0xFF8B949E)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ".. (Up)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        }

                        if (subdirectories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No subdirectories",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        } else {
                            items(subdirectories) { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentDir = dir
                                            pathInput = dir.absolutePath
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF58A6FF)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        dir.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFF0F6FC),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = File(pathInput)
                    if (target.exists() && target.isDirectory) {
                        onFolderSelected(target)
                    } else if (currentDir.exists() && currentDir.isDirectory) {
                        onFolderSelected(currentDir)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF238636),
                    contentColor = Color.White
                )
            ) {
                Text("Select Folder")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF8B949E)
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
