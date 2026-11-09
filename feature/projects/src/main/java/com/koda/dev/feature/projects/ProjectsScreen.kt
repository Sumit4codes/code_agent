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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    // Dynamic heights of floating liquid glass capsules
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }

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
        val layoutDirection = LocalLayoutDirection.current
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val navBarsBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Scrolling Project Content Stream (captured into backdrop state for real-time glass refraction)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .captureBackdrop(backdropState),
            contentPadding = PaddingValues(
                start = 14.dp + safeInsets.calculateStartPadding(layoutDirection),
                end = 14.dp + safeInsets.calculateEndPadding(layoutDirection),
                top = if (topBarHeightPx > 0f) with(LocalDensity.current) { topBarHeightPx.toDp() } + 8.dp else 90.dp,
                bottom = 86.dp + navBarsBottomInset // Dynamic clearance for floating bottom navigation bar
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
                // Section Header with Storage Status Badge
                item(key = "workspace_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
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

                        // Storage status badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    }
                }

                // Project List Items
                items(projects, key = { it.id }) { project ->
                    LiquidGlassProjectItem(
                        project = project,
                        onBrowse = { onProjectSelected?.invoke(project) },
                        onChat = { onChatWithProject?.invoke(project) ?: onProjectSelected?.invoke(project) },
                        onDelete = { projectToDelete = project }
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                        // Frosted Koda Dev App Icon Logo Disc
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                                    .border(
                                        width = 0.75.dp,
                                        brush = GlassDesign.microGlassBorder(topAlpha = 0.45f, bottomAlpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.koda.dev.app.core.ui.R.drawable.ic_koda_logo),
                                    contentDescription = "Koda Dev Logo",
                                    modifier = Modifier.size(24.dp)
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

    projectToDelete?.let { project ->
        DeleteProjectConfirmationDialog(
            project = project,
            onDismiss = { projectToDelete = null },
            onConfirm = {
                viewModel.removeProject(project)
                projectToDelete = null
            }
        )
    }
}

/**
 * Liquid Glass Delete Project Confirmation Dialog.
 * Prompts user to confirm deletion before removing the workspace from the app.
 */
@Composable
private fun DeleteProjectConfirmationDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dialogShape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
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
                    brush = GlassDesign.liquidGlassBorder(
                        topSpecularAlpha = 0.70f,
                        dispersionAlpha = 0.35f
                    ),
                    shape = dialogShape
                )
        ) {
            // Internal Top Refraction Sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Crimson Disc and Close Disc
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x26F85149))
                                .border(
                                    width = 0.75.dp,
                                    brush = GlassDesign.liquidGlassBorder(
                                        topSpecularAlpha = 0.60f,
                                        dispersionAlpha = 0.25f
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF7B72),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Delete Project?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF7B72)
                            )
                            Text(
                                text = "Remove from workspaces",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                                shape = CircleShape
                            )
                            .springPressClickable { onDismiss() },
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Project Details / Warning Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AF85149))
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x40F85149), Color(0x20F85149))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Are you sure you want to remove \"${project.name}\" from Koda Dev?",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFF0F6FC),
                            lineHeight = 20.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF8B949E),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = project.treeUri.removePrefix("file://"),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF8B949E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your files on disk will not be deleted.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8B949E)
                        )
                    }
                }

                // Action Buttons (Cancel + Delete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f),
                                shape = cancelShape
                            )
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val deleteShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(deleteShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFDA3633), Color(0xFFF85149))
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = GlassDesign.liquidGlassBorder(0.70f, 0.35f),
                                shape = deleteShape
                            )
                            .springPressClickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
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

    val dialogShape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
                    brush = GlassDesign.liquidGlassBorder(
                        topSpecularAlpha = 0.70f,
                        dispersionAlpha = 0.35f
                    ),
                    shape = dialogShape
                )
        ) {
            // Internal Top Refraction Sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Frosted Folder Avatar Disc
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF58A6FF)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Select Project Folder",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Choose root directory for workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f),
                                shape = CircleShape
                            )
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

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
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF0F6FC)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedContainerColor = Color(0xFF0D1117).copy(alpha = 0.90f),
                        unfocusedContainerColor = Color(0xFF0D1117).copy(alpha = 0.80f)
                    )
                )

                // Quick jump shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ext = Environment.getExternalStorageDirectory()
                    val docs = File(ext, "Documents")
                    val download = File(ext, "Download")

                    val shortcuts = listOf(
                        "Root" to ext,
                        "Documents" to docs,
                        "Download" to download
                    )

                    shortcuts.forEach { (label, dir) ->
                        if (dir.exists()) {
                            val isCurrent = currentDir.absolutePath == dir.absolutePath
                            val chipShape = RoundedCornerShape(10.dp)

                            Box(
                                modifier = Modifier
                                    .clip(chipShape)
                                    .then(
                                        if (isCurrent) {
                                            Modifier.background(Color(0xFF1F6FEB).copy(alpha = 0.35f))
                                        } else {
                                            Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                        }
                                    )
                                    .border(
                                        width = 0.75.dp,
                                        brush = if (isCurrent) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.7f, dispersionAlpha = 0.35f)
                                        else GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f),
                                        shape = chipShape
                                    )
                                    .springPressClickable {
                                        currentDir = dir
                                        pathInput = dir.absolutePath
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isCurrent) Color(0xFF79C0FF) else Color(0xFFC9D1D9)
                                )
                            }
                        }
                    }
                }

                // Subdirectories listing card
                val listCardShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(listCardShape)
                        .background(Color(0xFF0D1117).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF30363D), listCardShape)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                        // Parent directory item
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .springPressClickable {
                                            currentDir = parent
                                            pathInput = parent.absolutePath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Go up",
                                        tint = Color(0xFF8B949E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ".. (Up)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        }

                        if (subdirectories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No subdirectories found",
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .springPressClickable {
                                            currentDir = dir
                                            pathInput = dir.absolutePath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF58A6FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = dir.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFF0F6FC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .springPressClickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8B949E)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Select Folder button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1F6FEB).copy(alpha = 0.50f),
                                        Color(0xFF238636).copy(alpha = 0.40f)
                                    )
                                )
                            )
                            .border(
                                width = 0.75.dp,
                                brush = GlassDesign.liquidGlassBorder(
                                    topSpecularAlpha = 0.80f,
                                    dispersionAlpha = 0.40f
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .springPressClickable {
                                val target = File(pathInput)
                                if (target.exists() && target.isDirectory) {
                                    onFolderSelected(target)
                                } else if (currentDir.exists() && currentDir.isDirectory) {
                                    onFolderSelected(currentDir)
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF79C0FF)
                        )
                        Text(
                            text = "Select Folder",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFF0F6FC)
                        )
                    }
                }
            }
        }
    }
}
