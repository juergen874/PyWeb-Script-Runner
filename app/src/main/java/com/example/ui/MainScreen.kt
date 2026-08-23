package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CodeEditorView
import com.example.ui.components.PipManagerView
import com.example.ui.components.ScriptManagerView
import com.example.ui.components.TerminalView
import com.example.ui.components.WebPreviewView
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Save dialog form state
    var editTitle by remember(uiState.scriptTitle) { mutableStateOf(uiState.scriptTitle) }
    var editCategory by remember(uiState.scriptCategory) { mutableStateOf(uiState.scriptCategory) }

    // Handle snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PyDarkSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Py",
                                    color = PyBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Runner",
                                    color = PyYellow,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = when (uiState.currentTab) {
                                    AppTab.EDITOR -> "Editor • ${uiState.scriptTitle}"
                                    AppTab.TERMINAL -> "Terminal & REPL"
                                    AppTab.WEB_UI -> "Localhost Web UI"
                                    AppTab.PACKAGES -> "Pip & Pakete"
                                    AppTab.SCRIPTS -> "Vorlagen & Skripte"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    // Server Online indicator
                    AssistChip(
                        onClick = { viewModel.selectTab(AppTab.WEB_UI) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (uiState.serverState.isRunning) TermGreen else TermRed,
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = ":${uiState.serverState.port}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = null,
                        modifier = Modifier
                            .height(28.dp)
                            .padding(end = 4.dp)
                    )

                    // Quick Run Icon in Top Bar
                    if (uiState.isRunning) {
                        IconButton(
                            onClick = { viewModel.stopExecution() },
                            modifier = Modifier.testTag("top_stop_action")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stoppen", tint = TermRed)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.runScript() },
                            modifier = Modifier.testTag("top_run_action")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ausführen", tint = TermGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.EDITOR,
                    onClick = { viewModel.selectTab(AppTab.EDITOR) },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
                    label = { Text("Editor") },
                    modifier = Modifier.testTag("nav_editor")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.TERMINAL,
                    onClick = { viewModel.selectTab(AppTab.TERMINAL) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.isWaitingForInput) {
                                    Badge(containerColor = TermYellow) { Text("!") }
                                } else if (uiState.isRunning) {
                                    Badge(containerColor = TermGreen)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Terminal")
                        }
                    },
                    label = { Text("Terminal") },
                    modifier = Modifier.testTag("nav_terminal")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.WEB_UI,
                    onClick = { viewModel.selectTab(AppTab.WEB_UI) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.serverState.isRunning) {
                                    Badge(containerColor = TermGreen)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Web UI")
                        }
                    },
                    label = { Text("Web UI") },
                    modifier = Modifier.testTag("nav_web_ui")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.PACKAGES,
                    onClick = { viewModel.selectTab(AppTab.PACKAGES) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.isInstallingPip) {
                                    Badge(containerColor = TermYellow) { Text("…") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = "Pip Pakete")
                        }
                    },
                    label = { Text("Pip") },
                    modifier = Modifier.testTag("nav_packages")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.SCRIPTS,
                    onClick = { viewModel.selectTab(AppTab.SCRIPTS) },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Skripte") },
                    label = { Text("Skripte") },
                    modifier = Modifier.testTag("nav_scripts")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "TabCrossfade"
        ) { tab ->
            when (tab) {
                AppTab.EDITOR -> {
                    CodeEditorView(
                        uiState = uiState,
                        onCodeChange = { viewModel.updateCode(it) },
                        onRunScript = { viewModel.runScript() },
                        onStopScript = { viewModel.stopExecution() },
                        onSaveClick = {
                            editTitle = uiState.scriptTitle
                            editCategory = uiState.scriptCategory
                            showSaveDialog = true
                        },
                        onNewScriptClick = { viewModel.createNewScript() }
                    )
                }

                AppTab.TERMINAL -> {
                    TerminalView(
                        uiState = uiState,
                        onUserInputTextChange = { viewModel.updateUserInput(it) },
                        onSendInput = { viewModel.sendInput() },
                        onReplInputTextChange = { viewModel.updateReplInput(it) },
                        onExecuteRepl = { viewModel.executeRepl() },
                        onClearTerminal = { viewModel.clearTerminal() },
                        onFontSizeChange = { viewModel.setTerminalFontSize(it) }
                    )
                }

                AppTab.WEB_UI -> {
                    WebPreviewView(
                        uiState = uiState,
                        onRefresh = { viewModel.refreshWebPreview() }
                    )
                }

                AppTab.PACKAGES -> {
                    PipManagerView(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }

                AppTab.SCRIPTS -> {
                    ScriptManagerView(
                        uiState = uiState,
                        onLoadScript = { viewModel.loadScript(it) },
                        onRunScriptDirectly = { script ->
                            viewModel.loadScript(script)
                            viewModel.runScript()
                        },
                        onDeleteScript = { viewModel.deleteScript(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onNewScript = { viewModel.createNewScript() }
                    )
                }
            }
        }
    }

    // Save Script Dialog
    if (showSaveDialog) {
        val categories = listOf("Web UI", "Terminal", "Data & Math", "Games", "System")
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Skript speichern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Titel des Skripts") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Kategorie:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.take(3).forEach { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.drop(3).forEach { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = editTitle.ifBlank { "Unbenannt" }
                        viewModel.saveCurrentScript(title, editCategory)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PyBlueDark)
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
