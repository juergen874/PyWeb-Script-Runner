package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ScriptEntity
import com.example.data.ScriptRepository
import com.example.engine.*
import com.example.server.HttpRequestLog
import com.example.server.LocalHttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class AppTab {
    EDITOR, TERMINAL, WEB_UI, PACKAGES, SCRIPTS
}

enum class EngineMode {
    AUTO, PYODIDE, NATIVE
}

enum class TerminalLineType {
    STDOUT, STDERR, STDIN, SYSTEM, PROMPT, REPL_IN, REPL_OUT
}

data class TerminalLine(
    val id: Long = System.currentTimeMillis() + Random().nextLong(1000),
    val text: String,
    val type: TerminalLineType,
    val timestamp: String
)

data class ServerState(
    val isRunning: Boolean = false,
    val url: String = "http://127.0.0.1:8080",
    val port: Int = 8080,
    val requestLogs: List<HttpRequestLog> = emptyList(),
    val registeredRoutes: List<String> = listOf("/"),
    val refreshCounter: Int = 0
)

data class UiState(
    val currentTab: AppTab = AppTab.EDITOR,
    val scriptId: Long? = null,
    val scriptTitle: String = "Neues Skript",
    val scriptCode: String = "",
    val scriptCategory: String = "Web UI",
    val isRunning: Boolean = false,
    val terminalLines: List<TerminalLine> = emptyList(),
    val isWaitingForInput: Boolean = false,
    val inputPrompt: String = "",
    val userInputText: String = "",
    val replInputText: String = "",
    val terminalFontSize: Int = 13,
    val serverState: ServerState = ServerState(),
    val scripts: List<ScriptEntity> = emptyList(),
    val snackbarMessage: String? = null,
    val isAutoSwitchToWeb: Boolean = true,
    val engineMode: EngineMode = EngineMode.AUTO,
    val pipSearchQuery: String = "",
    val isInstallingPip: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScriptRepository
    private val localServer: LocalHttpServer
    val socketManager: NativeSocketManager = NativeSocketManager()
    val pyodideEngine: PyodideEngine

    private var executionJob: Job? = null
    private var inputContinuation: Continuation<String>? = null
    private var activeContext: PyContext? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ScriptRepository(db.scriptDao())
        pyodideEngine = PyodideEngine(application, viewModelScope, socketManager)

        localServer = LocalHttpServer(viewModelScope) { log ->
            _uiState.update { state ->
                val newLogs = (listOf(log) + state.serverState.requestLogs).take(50)
                state.copy(serverState = state.serverState.copy(requestLogs = newLogs))
            }
        }

        // Start localhost server by default
        localServer.start(8080)
        _uiState.update {
            it.copy(
                serverState = it.serverState.copy(
                    isRunning = true,
                    url = "http://127.0.0.1:8080",
                    port = 8080
                )
            )
        }

        // Initialize default script templates & observe scripts
        viewModelScope.launch {
            repository.ensureDefaultTemplates()
            repository.allScripts.collect { scriptsList ->
                _uiState.update { state ->
                    val updatedState = state.copy(scripts = scriptsList)
                    if (state.scriptCode.isEmpty() && scriptsList.isNotEmpty()) {
                        val first = scriptsList.first()
                        updatedState.copy(
                            scriptId = first.id,
                            scriptTitle = first.title,
                            scriptCode = first.code,
                            scriptCategory = first.category
                        )
                    } else {
                        updatedState
                    }
                }
            }
        }

        // Initial welcome terminal lines
        addTerminalLine("🐍 Python 3.11.4 Embedded Environment bereit", TerminalLineType.SYSTEM)
        addTerminalLine("🌐 Localhost Web Server läuft auf http://127.0.0.1:8080", TerminalLineType.SYSTEM)
        addTerminalLine("Tippe auf 'Ausführen' (▶) oder wechsle zu 'Web UI' für die Live-Vorschau.\n", TerminalLineType.SYSTEM)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateCode(newCode: String) {
        _uiState.update { it.copy(scriptCode = newCode) }
    }

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(scriptTitle = newTitle) }
    }

    fun updateCategory(newCat: String) {
        _uiState.update { it.copy(scriptCategory = newCat) }
    }

    fun updateUserInput(text: String) {
        _uiState.update { it.copy(userInputText = text) }
    }

    fun updateReplInput(text: String) {
        _uiState.update { it.copy(replInputText = text) }
    }

    fun setTerminalFontSize(size: Int) {
        _uiState.update { it.copy(terminalFontSize = size) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refreshWebPreview() {
        _uiState.update {
            it.copy(
                serverState = it.serverState.copy(
                    refreshCounter = it.serverState.refreshCounter + 1
                )
            )
        }
    }

    fun runScript() {
        val codeToRun = _uiState.value.scriptCode
        if (codeToRun.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Skript ist leer.") }
            return
        }

        executionJob?.cancel()

        addTerminalLine("─── ▶ Führe Skript aus: '${_uiState.value.scriptTitle}' ───", TerminalLineType.SYSTEM)
        _uiState.update { it.copy(isRunning = true) }

        var launchedWebServer = false

        val serverCallback = object : PyServerCallback {
            override fun serveHtml(html: String, port: Int) {
                launchedWebServer = true
                localServer.setHtml(html)
                localServer.start(port)
                _uiState.update {
                    it.copy(
                        serverState = it.serverState.copy(
                            isRunning = true,
                            url = "http://127.0.0.1:$port",
                            port = port,
                            refreshCounter = it.serverState.refreshCounter + 1
                        )
                    )
                }
            }

            override fun registerRoute(
                path: String,
                method: String,
                handler: suspend (params: Map<String, String>, body: String) -> String
            ) {
                launchedWebServer = true
                localServer.registerRoute(path, handler)
                _uiState.update {
                    it.copy(
                        serverState = it.serverState.copy(
                            registeredRoutes = localServer.getRegisteredRoutes(),
                            refreshCounter = it.serverState.refreshCounter + 1
                        )
                    )
                }
            }

            override fun startServer(port: Int) {
                launchedWebServer = true
                localServer.start(port)
                _uiState.update {
                    it.copy(
                        serverState = it.serverState.copy(
                            isRunning = true,
                            url = "http://127.0.0.1:$port",
                            port = port,
                            refreshCounter = it.serverState.refreshCounter + 1
                        )
                    )
                }
            }

            override fun stopServer() {
                localServer.stop()
                _uiState.update {
                    it.copy(serverState = it.serverState.copy(isRunning = false))
                }
            }

            override fun getServerPort(): Int = localServer.currentPort
            override fun isServerRunning(): Boolean = localServer.isRunning()
        }

        val ctx = PyContext(
            onStdout = { text -> addTerminalLine(text, TerminalLineType.STDOUT) },
            onStderr = { text -> addTerminalLine(text, TerminalLineType.STDERR) },
            onInputRequest = { prompt ->
                suspendCoroutine { cont ->
                    inputContinuation = cont
                    _uiState.update {
                        it.copy(
                            isWaitingForInput = true,
                            inputPrompt = prompt,
                            currentTab = AppTab.TERMINAL
                        )
                    }
                }
            },
            serverCallback = serverCallback
        )
        activeContext = ctx

        executionJob = viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val mode = _uiState.value.engineMode
            val usePyodide = when (mode) {
                EngineMode.PYODIDE -> true
                EngineMode.NATIVE -> false
                EngineMode.AUTO -> pyodideEngine.engineState.value == EngineState.READY &&
                        (codeToRun.contains("micropip") || codeToRun.contains("numpy") || codeToRun.contains("requests") || codeToRun.contains("pandas") || codeToRun.contains("matplotlib"))
            }

            if (usePyodide && pyodideEngine.engineState.value == EngineState.READY) {
                withContext(Dispatchers.Main) {
                    addTerminalLine("🚀 Starte Ausführung in Pyodide CPython 3.11 Engine...\n", TerminalLineType.SYSTEM)
                }
                val res = pyodideEngine.executeCode(
                    code = codeToRun,
                    onStdout = { text -> addTerminalLine(text, TerminalLineType.STDOUT) },
                    onStderr = { text -> addTerminalLine(text, TerminalLineType.STDERR) }
                )
                val duration = res.durationMs.coerceAtLeast(System.currentTimeMillis() - startTime)
                withContext(Dispatchers.Main) {
                    if (res.success) {
                        if (res.output.isNotEmpty() && res.output != "None") {
                            addTerminalLine("${res.output}\n", TerminalLineType.REPL_OUT)
                        }
                        addTerminalLine("✔ Skript erfolgreich beendet in ${duration}ms (Pyodide)\n", TerminalLineType.SYSTEM)
                    } else {
                        addTerminalLine("❌ ${res.error ?: "Fehler bei der Ausführung"}\n", TerminalLineType.STDERR)
                        addTerminalLine("⏹ Beendet mit Fehler nach ${duration}ms\n", TerminalLineType.SYSTEM)
                    }
                    _uiState.update { it.copy(isRunning = false, isWaitingForInput = false) }
                    if (launchedWebServer && _uiState.value.isAutoSwitchToWeb) {
                        _uiState.update { it.copy(currentTab = AppTab.WEB_UI) }
                    }
                }
                return@launch
            }

            try {
                val lexer = PyLexer(codeToRun)
                val tokens = lexer.tokenize()
                val parser = PyParser(tokens)
                val statements = parser.parse()

                val interpreter = PyInterpreter(ctx)
                interpreter.execute(statements)

                val duration = System.currentTimeMillis() - startTime
                withContext(Dispatchers.Main) {
                    addTerminalLine("✔ Skript erfolgreich beendet in ${duration}ms\n", TerminalLineType.SYSTEM)
                    _uiState.update { it.copy(isRunning = false, isWaitingForInput = false) }

                    // If a web server/UI was served and user prefers, switch to Web UI tab
                    if (launchedWebServer && _uiState.value.isAutoSwitchToWeb) {
                        _uiState.update { it.copy(currentTab = AppTab.WEB_UI) }
                    }
                }
            } catch (ce: CancellationException) {
                withContext(Dispatchers.Main) {
                    addTerminalLine("⏹ Ausführung abgebrochen.", TerminalLineType.SYSTEM)
                    _uiState.update { it.copy(isRunning = false, isWaitingForInput = false) }
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                withContext(Dispatchers.Main) {
                    val errMsg = e.message ?: e.toString()
                    addTerminalLine("❌ Traceback (most recent call last):\n$errMsg", TerminalLineType.STDERR)
                    addTerminalLine("⏹ Beendet mit Fehler nach ${duration}ms\n", TerminalLineType.SYSTEM)
                    _uiState.update { it.copy(isRunning = false, isWaitingForInput = false) }
                }
            }
        }
    }

    fun setEngineMode(mode: EngineMode) {
        _uiState.update { it.copy(engineMode = mode) }
        val modeName = when (mode) {
            EngineMode.AUTO -> "Auto (Empfohlen)"
            EngineMode.PYODIDE -> "Pyodide WebAssembly (CPython 3.11 + Pip)"
            EngineMode.NATIVE -> "Nativ (Integrierte Kotlin VM)"
        }
        addTerminalLine("⚙️ Engine-Modus gewechselt zu: $modeName\n", TerminalLineType.SYSTEM)
    }

    fun setPipSearchQuery(query: String) {
        _uiState.update { it.copy(pipSearchQuery = query) }
    }

    fun installPipPackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return

        _uiState.update { it.copy(isInstallingPip = true, currentTab = AppTab.PACKAGES) }
        viewModelScope.launch {
            addTerminalLine("📦 Starte Pip-Installation von '$trimmed'...\n", TerminalLineType.SYSTEM)
            val result = pyodideEngine.installPackage(trimmed)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    _uiState.update {
                        it.copy(
                            isInstallingPip = false,
                            snackbarMessage = "✔ $trimmed erfolgreich installiert!"
                        )
                    }
                    addTerminalLine("✔ Pip: ${result.second}\n", TerminalLineType.SYSTEM)
                } else {
                    _uiState.update {
                        it.copy(
                            isInstallingPip = false,
                            snackbarMessage = "❌ Fehler: ${result.second}"
                        )
                    }
                    addTerminalLine("❌ Pip Fehler: ${result.second}\n", TerminalLineType.STDERR)
                }
            }
        }
    }

    fun stopExecution() {
        activeContext?.isCancelled = true
        executionJob?.cancel()
        inputContinuation?.resume("")
        inputContinuation = null
        _uiState.update {
            it.copy(
                isRunning = false,
                isWaitingForInput = false
            )
        }
        addTerminalLine("⏹ Ausführung gestoppt.", TerminalLineType.SYSTEM)
    }

    fun sendInput() {
        val input = _uiState.value.userInputText
        _uiState.update {
            it.copy(
                isWaitingForInput = false,
                userInputText = ""
            )
        }
        inputContinuation?.resume(input)
        inputContinuation = null
    }

    fun executeRepl() {
        val command = _uiState.value.replInputText.trim()
        if (command.isEmpty()) return

        _uiState.update { it.copy(replInputText = "") }
        addTerminalLine(">>> $command", TerminalLineType.REPL_IN)

        if (command.startsWith("pip install ")) {
            val pkg = command.removePrefix("pip install ").trim()
            installPipPackage(pkg)
            return
        } else if (command == "pip list" || command == "pip") {
            val list = pyodideEngine.installedPackages.value
            val text = "Installierte Pip-Pakete:\n" + list.joinToString("\n") { " - ${it.name} (${it.version})" }
            addTerminalLine("$text\n", TerminalLineType.SYSTEM)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val ctx = activeContext ?: PyContext(
                    onStdout = { text -> addTerminalLine(text, TerminalLineType.STDOUT) },
                    onStderr = { text -> addTerminalLine(text, TerminalLineType.STDERR) },
                    onInputRequest = { "" }
                ).also { activeContext = it }

                val lexer = PyLexer(command)
                val tokens = lexer.tokenize()
                val parser = PyParser(tokens)

                // Try parsing as expression first for immediate evaluation
                val interpreter = PyInterpreter(ctx)
                try {
                    val expr = PyParser(tokens).parseExpression()
                    val result = interpreter.evaluate(expr)
                    if (result !is PyValue.NoneVal) {
                        withContext(Dispatchers.Main) {
                            addTerminalLine(result.toDisplayString(), TerminalLineType.REPL_OUT)
                        }
                    }
                } catch (e: Exception) {
                    // Otherwise parse as statement
                    val stmts = parser.parse()
                    interpreter.execute(stmts)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addTerminalLine("❌ ${e.message}", TerminalLineType.STDERR)
                }
            }
        }
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalLines = emptyList()) }
        addTerminalLine("Terminal geleert.", TerminalLineType.SYSTEM)
    }

    fun loadScript(script: ScriptEntity) {
        _uiState.update {
            it.copy(
                scriptId = script.id,
                scriptTitle = script.title,
                scriptCode = script.code,
                scriptCategory = script.category,
                currentTab = AppTab.EDITOR,
                snackbarMessage = "'${script.title}' geladen"
            )
        }
    }

    fun runScriptDirectly(script: ScriptEntity) {
        _uiState.update {
            it.copy(
                scriptId = script.id,
                scriptTitle = script.title,
                scriptCode = script.code,
                scriptCategory = script.category
            )
        }
        runScript()
    }

    fun createNewScript() {
        val newCode = """# Neues Python Skript
import math
import time

print("Hallo aus PyRunner!")
"""
        _uiState.update {
            it.copy(
                scriptId = null,
                scriptTitle = "Unbenanntes Skript",
                scriptCode = newCode,
                scriptCategory = "Web UI",
                currentTab = AppTab.EDITOR,
                snackbarMessage = "Neues Skript erstellt"
            )
        }
    }

    fun saveCurrentScript(title: String, category: String) {
        val code = _uiState.value.scriptCode
        viewModelScope.launch {
            val currentId = _uiState.value.scriptId
            if (currentId != null && currentId > 0) {
                repository.update(
                    ScriptEntity(
                        id = currentId,
                        title = title,
                        description = "Zuletzt bearbeitet: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())}",
                        code = code,
                        category = category,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _uiState.update {
                    it.copy(
                        scriptTitle = title,
                        scriptCategory = category,
                        snackbarMessage = "'$title' gespeichert"
                    )
                }
            } else {
                val newId = repository.insert(
                    ScriptEntity(
                        title = title,
                        description = "Erstellt am ${SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date())}",
                        code = code,
                        category = category
                    )
                )
                _uiState.update {
                    it.copy(
                        scriptId = newId,
                        scriptTitle = title,
                        scriptCategory = category,
                        snackbarMessage = "'$title' neu gespeichert"
                    )
                }
            }
        }
    }

    fun deleteScript(script: ScriptEntity) {
        viewModelScope.launch {
            repository.delete(script)
            _uiState.update { it.copy(snackbarMessage = "'${script.title}' gelöscht") }
        }
    }

    fun toggleFavorite(script: ScriptEntity) {
        viewModelScope.launch {
            repository.update(script.copy(isFavorite = !script.isFavorite))
        }
    }

    fun duplicateScript(script: ScriptEntity) {
        viewModelScope.launch {
            val copyTitle = "${script.title} (Kopie)"
            val newId = repository.insert(
                ScriptEntity(
                    title = copyTitle,
                    description = "Dupliziert von ${script.title}",
                    code = script.code,
                    category = script.category
                )
            )
            _uiState.update {
                it.copy(
                    scriptId = newId,
                    scriptTitle = copyTitle,
                    scriptCode = script.code,
                    scriptCategory = script.category,
                    snackbarMessage = "Skript dupliziert: '$copyTitle'"
                )
            }
        }
    }

    fun importScriptFromUri(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "Importiertes Skript.py"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                }

                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: ""

                if (content.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(snackbarMessage = "Datei ist leer!") }
                    }
                    return@launch
                }

                val cleanTitle = fileName.removeSuffix(".py").removeSuffix(".txt")
                val isWeb = content.contains("web.") || content.contains("html")
                val category = if (isWeb) "Web UI" else "Importiert"

                val newId = repository.insert(
                    ScriptEntity(
                        title = cleanTitle,
                        description = "Importiert aus $fileName",
                        code = content,
                        category = category
                    )
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            scriptId = newId,
                            scriptTitle = cleanTitle,
                            scriptCode = content,
                            scriptCategory = category,
                            currentTab = AppTab.EDITOR,
                            snackbarMessage = "✔ Datei '$fileName' erfolgreich geladen!"
                        )
                    }
                    addTerminalLine("📂 Skript '$fileName' in Editor geladen.\n", TerminalLineType.SYSTEM)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(snackbarMessage = "Fehler beim Laden: ${e.localizedMessage}") }
                    addTerminalLine("❌ Import-Fehler: ${e.message}\n", TerminalLineType.STDERR)
                }
            }
        }
    }

    fun importScriptFromText(title: String, code: String, category: String = "Eigene Skripte") {
        viewModelScope.launch {
            val newId = repository.insert(
                ScriptEntity(
                    title = title,
                    description = "Erstellt am ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())}",
                    code = code,
                    category = category
                )
            )
            _uiState.update {
                it.copy(
                    scriptId = newId,
                    scriptTitle = title,
                    scriptCode = code,
                    scriptCategory = category,
                    currentTab = AppTab.EDITOR,
                    snackbarMessage = "✔ '$title' importiert"
                )
            }
        }
    }

    fun importScriptFromUrl(urlString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlString.trim())
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"

                if (conn.responseCode in 200..299) {
                    val code = conn.inputStream.bufferedReader().use { it.readText() }
                    val pathSegments = url.path.split("/")
                    val rawName = pathSegments.lastOrNull()?.ifBlank { null } ?: "Online_Skript.py"
                    val title = rawName.removeSuffix(".py").removeSuffix(".txt")

                    val newId = repository.insert(
                        ScriptEntity(
                            title = title,
                            description = "Geladen von: $urlString",
                            code = code,
                            category = "Online"
                        )
                    )

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                scriptId = newId,
                                scriptTitle = title,
                                scriptCode = code,
                                scriptCategory = "Online",
                                currentTab = AppTab.EDITOR,
                                snackbarMessage = "✔ '$title' von URL heruntergeladen!"
                            )
                        }
                        addTerminalLine("🌐 Skript von $urlString geladen.\n", TerminalLineType.SYSTEM)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(snackbarMessage = "Download fehlgeschlagen (HTTP ${conn.responseCode})") }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(snackbarMessage = "URL Fehler: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun exportScriptToUri(uri: Uri, context: Context, code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(code.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(snackbarMessage = "✔ Skript erfolgreich exportiert!") }
                    addTerminalLine("💾 Skript erfolgreich exportiert.\n", TerminalLineType.SYSTEM)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(snackbarMessage = "Export-Fehler: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun resetOrUpdateTemplates() {
        viewModelScope.launch {
            repository.resetOrUpdateDefaultTemplates()
            val list = repository.allScripts
            _uiState.update {
                it.copy(snackbarMessage = "✔ Alle Standard-Vorlagen wurden aktualisiert!")
            }
        }
    }

    private fun addTerminalLine(text: String, type: TerminalLineType) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        // If text contains newlines, we can either split or keep
        val cleanText = text.trimEnd('\n')
        if (cleanText.isEmpty()) return

        val line = TerminalLine(
            text = cleanText,
            type = type,
            timestamp = timestamp
        )

        _uiState.update {
            val updated = (it.terminalLines + line).takeLast(500)
            it.copy(terminalLines = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
    }
}
