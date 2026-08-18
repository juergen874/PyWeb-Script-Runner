package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun CodeEditorView(
    uiState: UiState,
    onCodeChange: (String) -> Unit,
    onRunScript: () -> Unit,
    onStopScript: () -> Unit,
    onSaveClick: () -> Unit,
    onNewScriptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickSymbols = listOf(
        "TAB" to "    ",
        ":" to ":",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "{" to "{",
        "}" to "}",
        "\"" to "\"",
        "'" to "'",
        "=" to "=",
        "==" to "==",
        "+" to "+",
        "-" to "-",
        "*" to "*",
        "/" to "/",
        "def" to "def ",
        "import" to "import ",
        "print" to "print(",
        "input" to "input(",
        "web" to "import web\n",
        "html" to "web.serve_html(\"\"\"<h1>Titel</h1>\"\"\")\n"
    )

    val lineCount = remember(uiState.scriptCode) {
        uiState.scriptCode.count { it == '\n' } + 1
    }

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar with Title, Category, Save and Action buttons
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Python Code",
                        tint = PyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = uiState.scriptTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.scriptCategory} • $lineCount Zeilen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onNewScriptClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("new_script_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Neues Skript",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("save_script_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Speichern",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Run / Stop Button
                    if (uiState.isRunning) {
                        FilledTonalButton(
                            onClick = onStopScript,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = TermRed.copy(alpha = 0.2f),
                                contentColor = TermRed
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("stop_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stoppen",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stopp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onRunScript,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PyBlueDark,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("run_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Ausführen",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ausführen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Symbol Accessory Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickSymbols.forEach { (label, insertion) ->
                    SuggestionChip(
                        onClick = {
                            onCodeChange(uiState.scriptCode + insertion)
                        },
                        label = {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // Editor Area with Line Numbers + Scrollable Monospace Code
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                // Line Numbering Column
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = i.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Python Code Input Field
                BasicTextField(
                    value = uiState.scriptCode,
                    onValueChange = onCodeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .testTag("code_editor_input"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(PyBlue),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                        autoCorrectEnabled = false
                    ),
                    visualTransformation = PythonSyntaxVisualTransformation()
                )
            }
        }
    }
}

/**
 * Lightweight VisualTransformation that colors Python keywords, strings, comments, and numbers.
 */
class PythonSyntaxVisualTransformation : VisualTransformation {
    private val keywords = setOf(
        "def", "class", "if", "elif", "else", "while", "for", "in", "return", "break",
        "continue", "pass", "import", "from", "as", "try", "except", "finally", "raise",
        "assert", "and", "or", "not", "is", "lambda", "True", "False", "None", "with"
    )

    private val builtins = setOf(
        "print", "input", "len", "range", "sum", "min", "max", "abs", "round",
        "int", "float", "str", "bool", "list", "dict", "set", "tuple", "open", "web", "serve_html"
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        val keywordColor = SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold) // Bright Cyan
        val builtinColor = SpanStyle(color = Color(0xFFFBBF24)) // Yellow Gold
        val stringColor = SpanStyle(color = Color(0xFF4ADE80)) // Green
        val commentColor = SpanStyle(color = Color(0xFF64748B)) // Slate gray
        val numberColor = SpanStyle(color = Color(0xFFC084FC)) // Purple

        var i = 0
        while (i < raw.length) {
            val c = raw[i]

            // Comment #
            if (c == '#') {
                val start = i
                while (i < raw.length && raw[i] != '\n') {
                    i++
                }
                builder.addStyle(commentColor, start, i)
                continue
            }

            // String "..." or '...'
            if (c == '"' || c == '\'') {
                val quote = c
                val start = i
                i++
                while (i < raw.length && raw[i] != quote && raw[i] != '\n') {
                    if (raw[i] == '\\' && i + 1 < raw.length) i++
                    i++
                }
                if (i < raw.length && raw[i] == quote) i++
                builder.addStyle(stringColor, start, i)
                continue
            }

            // Numbers
            if (c.isDigit()) {
                val start = i
                while (i < raw.length && (raw[i].isDigit() || raw[i] == '.')) {
                    i++
                }
                builder.addStyle(numberColor, start, i)
                continue
            }

            // Identifiers / Keywords
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_')) {
                    i++
                }
                val word = raw.substring(start, i)
                if (keywords.contains(word)) {
                    builder.addStyle(keywordColor, start, i)
                } else if (builtins.contains(word)) {
                    builder.addStyle(builtinColor, start, i)
                }
                continue
            }

            i++
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
