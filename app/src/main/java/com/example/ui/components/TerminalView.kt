package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TerminalLine
import com.example.ui.TerminalLineType
import com.example.ui.UiState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalView(
    uiState: UiState,
    onUserInputTextChange: (String) -> Unit,
    onSendInput: () -> Unit,
    onReplInputTextChange: (String) -> Unit,
    onExecuteRepl: () -> Unit,
    onClearTerminal: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Auto scroll on new lines
    LaunchedEffect(uiState.terminalLines.size) {
        if (uiState.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.terminalLines.size - 1)
        }
    }

    val filteredLines = remember(uiState.terminalLines, searchQuery) {
        if (searchQuery.isBlank()) uiState.terminalLines
        else uiState.terminalLines.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PyDarkBackground)
    ) {
        // Terminal Header Bar
        Surface(
            color = PyDarkSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when {
                                    uiState.isWaitingForInput -> TermYellow
                                    uiState.isRunning -> TermGreen
                                    else -> PyBlue
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Text(
                        text = "Python Terminal",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (uiState.isRunning) {
                        Text(
                            text = "(Läuft...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TermGreen
                        )
                    } else if (uiState.isWaitingForInput) {
                        Text(
                            text = "(Wartet auf Eingabe...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TermYellow
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Font Size Toggle
                    IconButton(
                        onClick = {
                            val nextSize = when (uiState.terminalFontSize) {
                                11 -> 13
                                13 -> 15
                                else -> 11
                            }
                            onFontSizeChange(nextSize)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Schriftgröße",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Search Toggle
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suchen",
                            tint = if (isSearchActive) PyBlue else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Copy All Output
                    IconButton(
                        onClick = {
                            val allText = uiState.terminalLines.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", allText))
                            Toast.makeText(context, "Terminal-Ausgabe kopiert", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopieren",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Terminal
                    IconButton(
                        onClick = onClearTerminal,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clear_terminal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Löschen",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search Bar (if active)
        AnimatedVisibility(visible = isSearchActive) {
            Surface(
                color = PyDarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Im Terminal suchen...", color = Color.Gray, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PyBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                }
            }
        }

        // Terminal Output Console Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredLines, key = { it.id }) { line ->
                    TerminalLineItem(line = line, fontSize = uiState.terminalFontSize)
                }
            }

            // Scroll to bottom floating button if scrolled up
            if (listState.canScrollForward) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem((filteredLines.size - 1).coerceAtLeast(0))
                        }
                    },
                    containerColor = PyDarkSurfaceVariant,
                    contentColor = PyBlue,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten scrollen")
                }
            }
        }

        // Input Prompt Bar (active when Python script calls input())
        AnimatedVisibility(visible = uiState.isWaitingForInput) {
            Surface(
                color = PyDarkSurfaceVariant,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = TermYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.inputPrompt.isNotEmpty()) uiState.inputPrompt else "Python wartet auf Eingabe (stdin):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = TermYellow
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.userInputText,
                            onValueChange = onUserInputTextChange,
                            placeholder = { Text("Eingabe tippen...", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSendInput() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TermYellow,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("terminal_user_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSendInput,
                            colors = ButtonDefaults.buttonColors(containerColor = TermYellow, contentColor = Color.Black),
                            modifier = Modifier.testTag("terminal_send_input_button")
                        ) {
                            Text("Senden", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Interactive REPL Command Line (>>>)
        Surface(
            color = PyDarkSurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ">>>",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PyBlue,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                OutlinedTextField(
                    value = uiState.replInputText,
                    onValueChange = onReplInputTextChange,
                    placeholder = {
                        Text(
                            "Python Befehl / REPL (z.B. 2+2, math.pi)...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onExecuteRepl() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PyBlue,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_repl_input")
                )

                IconButton(
                    onClick = onExecuteRepl,
                    enabled = uiState.replInputText.isNotBlank(),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("terminal_repl_send")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "REPL Ausführen",
                        tint = if (uiState.replInputText.isNotBlank()) PyBlue else Color.DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalLineItem(line: TerminalLine, fontSize: Int) {
    val (textColor, prefix) = when (line.type) {
        TerminalLineType.STDOUT -> Pair(Color(0xFFE2E8F0), "")
        TerminalLineType.STDERR -> Pair(TermRed, "❌ ")
        TerminalLineType.STDIN -> Pair(TermYellow, "▶ ")
        TerminalLineType.SYSTEM -> Pair(Color(0xFF64748B), "ℹ ")
        TerminalLineType.PROMPT -> Pair(TermCyan, "? ")
        TerminalLineType.REPL_IN -> Pair(PyBlue, ">>> ")
        TerminalLineType.REPL_OUT -> Pair(TermGreen, "=> ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$prefix${line.text}",
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            color = textColor,
            lineHeight = (fontSize + 6).sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
