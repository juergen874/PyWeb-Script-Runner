package com.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.AppTab
import com.example.ui.ServerState
import com.example.ui.TerminalLine
import com.example.ui.TerminalLineType
import com.example.ui.UiState
import com.example.ui.components.CodeEditorView
import com.example.ui.components.TerminalView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun editor_screenshot() {
        val sampleCode = """
            # Python Localhost Web Server
            import web
            import time

            print("Starting Python Runner Web Server...")
            html = "<h1>Hello from Python!</h1>"
            web.serve_html(html, port=8080)
        """.trimIndent()

        val mockState = UiState(
            currentTab = AppTab.EDITOR,
            scriptTitle = "Flask Localhost Web Dashboard",
            scriptCode = sampleCode,
            scriptCategory = "Web UI"
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CodeEditorView(
                        uiState = mockState,
                        onCodeChange = {},
                        onRunScript = {},
                        onStopScript = {},
                        onSaveClick = {},
                        onNewScriptClick = {}
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/editor.png")
    }

    @Test
    fun terminal_screenshot() {
        val mockState = UiState(
            currentTab = AppTab.TERMINAL,
            terminalLines = listOf(
                TerminalLine(text = "Python 3.11 Embedded Engine", type = TerminalLineType.SYSTEM, timestamp = "12:00:00"),
                TerminalLine(text = ">>> fib(7)", type = TerminalLineType.REPL_IN, timestamp = "12:00:01"),
                TerminalLine(text = "13", type = TerminalLineType.REPL_OUT, timestamp = "12:00:01"),
                TerminalLine(text = "Server running on http://127.0.0.1:8080", type = TerminalLineType.STDOUT, timestamp = "12:00:02")
            ),
            serverState = ServerState(isRunning = true, url = "http://127.0.0.1:8080", port = 8080)
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TerminalView(
                        uiState = mockState,
                        onUserInputTextChange = {},
                        onSendInput = {},
                        onReplInputTextChange = {},
                        onExecuteRepl = {},
                        onClearTerminal = {},
                        onFontSizeChange = {}
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/terminal.png")
    }
}
