package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScriptEntity
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun ScriptManagerView(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedCategory by remember { mutableStateOf("Alle") }
    var searchQuery by remember { mutableStateOf("") }
    var scriptToDelete by remember { mutableStateOf<ScriptEntity?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var newScriptTitle by remember { mutableStateOf("") }
    var newScriptCategory by remember { mutableStateOf("Eigene Skripte") }

    // File Open Launcher (SAF)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importScriptFromUri(it, context) }
    }

    // File Export Launcher (SAF)
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportScriptToUri(it, context, uiState.scriptCode) }
    }

    val categories = listOf("Alle", "IoT & Hardware", "Web UI", "Games", "Data & Math", "System", "Importiert")

    val filteredScripts = remember(uiState.scripts, selectedCategory, searchQuery) {
        uiState.scripts.filter { script ->
            val matchesCategory = (selectedCategory == "Alle" || script.category.equals(selectedCategory, ignoreCase = true))
            val matchesSearch = searchQuery.isBlank() ||
                    script.title.contains(searchQuery, ignoreCase = true) ||
                    script.description.contains(searchQuery, ignoreCase = true) ||
                    script.code.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Bar: Dateimanager Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openFileLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/x-python",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PyBlueDark),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("file_manager_open_file")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Datei laden", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val fileName = "${uiState.scriptTitle.replace(" ", "_")}.py"
                                exportFileLauncher.launch(fileName)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("file_manager_export_file")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportieren", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Tools Row (URL, Clipboard, Refresh Templates)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { showUrlDialog = true },
                            label = { Text("🌐 Von URL / GitHub", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )

                        SuggestionChip(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    viewModel.importScriptFromText("Eingefügtes Skript", clip)
                                }
                            },
                            label = { Text("📋 Einfügen", fontSize = 12.sp) }
                        )

                        SuggestionChip(
                            onClick = { viewModel.resetOrUpdateTemplates() },
                            label = { Text("🔄 Vorlagen aktualisieren", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Skripte & Vorlagen durchsuchen...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Löschen")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("script_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 12.sp) },
                                leadingIcon = if (selectedCategory == category) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Script List
            if (filteredScripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CodeOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keine Skripte gefunden",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lade eine .py-Datei oder erstelle ein neues Skript.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredScripts, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            isCurrent = script.id == uiState.scriptId,
                            onLoad = { viewModel.loadScript(script) },
                            onRun = { viewModel.runScriptDirectly(script) },
                            onDelete = { scriptToDelete = script },
                            onToggleFavorite = { viewModel.toggleFavorite(script) },
                            onDuplicate = { viewModel.duplicateScript(script) },
                            onShare = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TITLE, "${script.title}.py")
                                    putExtra(Intent.EXTRA_TEXT, script.code)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Skript teilen")
                                context.startActivity(shareIntent)
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button to create new script
        FloatingActionButton(
            onClick = { showNewScriptDialog = true },
            containerColor = PyBlueDark,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_new_script_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neues Skript erstellen")
        }
    }

    // New Script Dialog
    if (showNewScriptDialog) {
        AlertDialog(
            onDismissRequest = { showNewScriptDialog = false },
            title = { Text("Neues Python-Skript") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newScriptTitle,
                        onValueChange = { newScriptTitle = it },
                        label = { Text("Dateiname / Titel") },
                        placeholder = { Text("z.B. deye_solar_live.py") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newScriptCategory,
                        onValueChange = { newScriptCategory = it },
                        label = { Text("Kategorie") },
                        placeholder = { Text("Web UI, IoT, Data...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = if (newScriptTitle.isNotBlank()) newScriptTitle.trim() else "Neues Skript"
                        val defaultHeader = "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n# ${title}\n\nprint('Hallo aus $title!')\n"
                        viewModel.importScriptFromText(title, defaultHeader, newScriptCategory)
                        showNewScriptDialog = false
                        newScriptTitle = ""
                    }
                ) {
                    Text("Erstellen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewScriptDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Download from URL Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Skript von URL laden") },
            text = {
                Column {
                    Text("Gib eine direkte URL zu einer .py-Datei oder GitHub Raw ein:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://raw.githubusercontent.com/.../script.py") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.importScriptFromUrl(urlInput.trim())
                            showUrlDialog = false
                            urlInput = ""
                        }
                    },
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Herunterladen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (scriptToDelete != null) {
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            title = { Text("Skript löschen?") },
            text = { Text("Möchtest du '${scriptToDelete?.title}' wirklich unwiderruflich löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        scriptToDelete?.let { viewModel.deleteScript(it) }
                        scriptToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun ScriptCard(
    script: ScriptEntity,
    isCurrent: Boolean,
    onLoad: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLoad() }
            .testTag("script_card_${script.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = when {
                        script.category.contains("IoT", ignoreCase = true) || script.category.contains("Hardware", ignoreCase = true) -> Icons.Default.Sensors
                        script.category.contains("Web", ignoreCase = true) -> Icons.Default.Language
                        script.category.contains("Game", ignoreCase = true) -> Icons.Default.SportsEsports
                        script.category.contains("Data", ignoreCase = true) -> Icons.Default.Analytics
                        else -> Icons.Default.Terminal
                    }
                    val iconColor = when {
                        script.category.contains("IoT", ignoreCase = true) -> TermYellow
                        script.category.contains("Web", ignoreCase = true) -> PyBlue
                        script.category.contains("Game", ignoreCase = true) -> TermGreen
                        else -> PyYellow
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = script.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        val linesCount = script.code.lines().size
                        val bytesCount = script.code.toByteArray().size
                        Text(
                            text = "$linesCount Zeilen • ${bytesCount} B",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Teilen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplizieren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (script.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorit",
                            tint = if (script.isFavorite) PyYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (script.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Code Preview Snippet Box
            Surface(
                color = PyDarkBackground,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = script.code.lines().take(3).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(script.category, fontSize = 11.sp) },
                    modifier = Modifier.height(26.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onLoad,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRun,
                        colors = ButtonDefaults.buttonColors(containerColor = PyBlueDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ausführen", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
