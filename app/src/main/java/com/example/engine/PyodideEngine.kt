package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

enum class EngineState {
    UNINITIALIZED, LOADING, READY, RUNNING, ERROR
}

data class InstalledPackage(
    val name: String,
    val version: String,
    val summary: String = "",
    val isBuiltin: Boolean = false
)

class PyodideEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val socketManager: NativeSocketManager
) {
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _installedPackages = MutableStateFlow<List<InstalledPackage>>(emptyList())
    val installedPackages: StateFlow<List<InstalledPackage>> = _installedPackages.asStateFlow()

    private val _pipLogs = MutableStateFlow<List<String>>(emptyList())
    val pipLogs: StateFlow<List<String>> = _pipLogs.asStateFlow()

    private var currentExecutionDeferred: CompletableDeferred<PyExecutionResult>? = null
    private var currentPipDeferred: CompletableDeferred<Pair<Boolean, String>>? = null

    private var onStdoutListener: ((String) -> Unit)? = null
    private var onStderrListener: ((String) -> Unit)? = null

    data class PyExecutionResult(
        val success: Boolean,
        val output: String,
        val error: String? = null,
        val durationMs: Long = 0
    )

    init {
        mainHandler.post {
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        _engineState.value = EngineState.LOADING
        try {
            val wv = WebView(context.applicationContext)
            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            wv.addJavascriptInterface(AndroidJsBridge(), "AndroidBridge")

            val htmlBootstrap = buildBootstrapHtml()
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e("PyodideEngine", "WebView error: $description ($errorCode)")
                }
            }

            wv.loadDataWithBaseURL("https://pyodide-cdn.org/", htmlBootstrap, "text/html", "UTF-8", null)
            webView = wv
        } catch (e: Exception) {
            Log.e("PyodideEngine", "Failed to initialize WebView", e)
            _engineState.value = EngineState.ERROR
        }
    }

    private fun buildBootstrapHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/pyodide/v0.26.2/full/pyodide.js"></script>
</head>
<body>
<script>
    let pyodide = null;
    let micropip = null;

    async function initPyodideRuntime() {
        try {
            window.AndroidBridge.onPipLog("🚀 Lade Python 3.11 CPython Engine (Pyodide WebAssembly)...");
            pyodide = await loadPyodide({
                stdout: (text) => window.AndroidBridge.onStdout(text + "\n"),
                stderr: (text) => window.AndroidBridge.onStderr(text + "\n")
            });

            window.AndroidBridge.onPipLog("📦 Initialisiere micropip Paket-Manager...");
            await pyodide.loadPackage("micropip");
            micropip = pyodide.pyimport("micropip");

            // Setup Socket & OS Bridge in Python
            await pyodide.runPythonAsync(`
import sys
import js
import json

# Custom Android Network Socket Bridge
class AndroidSocket:
    AF_INET = 2
    SOCK_STREAM = 1
    SOCK_DGRAM = 2

    def __init__(self, family=2, type=1, proto=0, fileno=None):
        self.family = family
        self.type = type
        self.socket_id = None
        self.timeout_ms = 5000

    def connect(self, address):
        host, port = address
        self.socket_id = js.AndroidBridge.openSocketSync(str(host), int(port), int(self.timeout_ms))
        if not self.socket_id or self.socket_id == "":
            raise ConnectionError(f"Verbindung zu {host}:{port} fehlgeschlagen.")

    def settimeout(self, timeout):
        if timeout is not None:
            self.timeout_ms = int(timeout * 1000)

    def send(self, data):
        if isinstance(data, (bytes, bytearray)):
            hex_data = data.hex()
        elif isinstance(data, str):
            hex_data = data.encode('utf-8').hex()
        else:
            hex_data = ""
        sent = js.AndroidBridge.sendHexSync(self.socket_id, hex_data)
        return int(sent)

    def sendall(self, data):
        return self.send(data)

    def recv(self, bufsize=1024):
        hex_resp = js.AndroidBridge.recvHexSync(self.socket_id, int(bufsize), int(self.timeout_ms))
        if not hex_resp:
            return b""
        return bytes.fromhex(str(hex_resp))

    def close(self):
        if self.socket_id:
            js.AndroidBridge.closeSocketSync(self.socket_id)
            self.socket_id = None

# Expose socket class in global sys modules if needed
import types
android_socket_mod = types.ModuleType("socket")
android_socket_mod.AF_INET = 2
android_socket_mod.SOCK_STREAM = 1
android_socket_mod.socket = AndroidSocket
sys.modules["_android_socket"] = android_socket_mod
sys.modules["socket"] = android_socket_mod
            `);

            window.AndroidBridge.onPipLog("✅ Python 3.11 Engine & Pip einsatzbereit!");
            window.AndroidBridge.onEngineReady();
        } catch (err) {
            window.AndroidBridge.onEngineError(err.toString());
        }
    }

    async function runPythonCode(code, execId) {
        if (!pyodide) {
            window.AndroidBridge.onExecResult(execId, false, "", "Engine not ready");
            return;
        }
        const t0 = performance.now();
        try {
            const result = await pyodide.runPythonAsync(code);
            const duration = Math.round(performance.now() - t0);
            const resStr = result !== undefined && result !== null ? result.toString() : "";
            window.AndroidBridge.onExecResult(execId, true, resStr, duration);
        } catch (err) {
            const duration = Math.round(performance.now() - t0);
            window.AndroidBridge.onExecResult(execId, false, "", err.toString(), duration);
        }
    }

    async function installPackage(pkgName) {
        try {
            window.AndroidBridge.onPipLog(`⬇️ Lade '${'$'}{pkgName}' von PyPI herunter...`);
            await micropip.install(pkgName);
            window.AndroidBridge.onPipLog(`✔ '${'$'}{pkgName}' erfolgreich installiert!`);
            
            // Query installed packages list
            const listJson = await pyodide.runPythonAsync(`
import micropip
import json
installed = micropip.list()
res = [{"name": k, "version": str(v)} for k, v in installed.items()]
json.dumps(res)
            `);
            window.AndroidBridge.onPipSuccess(pkgName, listJson);
        } catch (err) {
            window.AndroidBridge.onPipError(pkgName, err.toString());
        }
    }

    async function getInstalledPackages() {
        try {
            const listJson = await pyodide.runPythonAsync(`
import micropip
import json
installed = micropip.list()
res = [{"name": k, "version": str(v)} for k, v in installed.items()]
json.dumps(res)
            `);
            window.AndroidBridge.onInstalledListResult(listJson);
        } catch (err) {
            window.AndroidBridge.onInstalledListResult("[]");
        }
    }

    initPyodideRuntime();
</script>
</body>
</html>
        """.trimIndent()
    }

    inner class AndroidJsBridge {

        @JavascriptInterface
        fun onStdout(text: String) {
            scope.launch(Dispatchers.Main) {
                onStdoutListener?.invoke(text)
            }
        }

        @JavascriptInterface
        fun onStderr(text: String) {
            scope.launch(Dispatchers.Main) {
                onStderrListener?.invoke(text)
            }
        }

        @JavascriptInterface
        fun onEngineReady() {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.READY
                addPipLog("Pyodide CPython 3.11 Runtime aktiv")
                refreshInstalledPackagesList()
            }
        }

        @JavascriptInterface
        fun onEngineError(err: String) {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.ERROR
                addPipLog("❌ Engine Fehler: $err")
            }
        }

        @JavascriptInterface
        fun onPipLog(log: String) {
            scope.launch(Dispatchers.Main) {
                addPipLog(log)
            }
        }

        @JavascriptInterface
        fun onPipSuccess(pkgName: String, listJson: String) {
            scope.launch(Dispatchers.Main) {
                parseInstalledList(listJson)
                currentPipDeferred?.complete(Pair(true, "Paket '$pkgName' erfolgreich installiert"))
                currentPipDeferred = null
            }
        }

        @JavascriptInterface
        fun onPipError(pkgName: String, error: String) {
            scope.launch(Dispatchers.Main) {
                addPipLog("❌ Fehler bei '$pkgName': $error")
                currentPipDeferred?.complete(Pair(false, error))
                currentPipDeferred = null
            }
        }

        @JavascriptInterface
        fun onInstalledListResult(listJson: String) {
            scope.launch(Dispatchers.Main) {
                parseInstalledList(listJson)
            }
        }

        @JavascriptInterface
        fun onExecResult(execId: String, success: Boolean, output: String, durationOrError: String, durationMs: Long = 0) {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.READY
                if (success) {
                    currentExecutionDeferred?.complete(
                        PyExecutionResult(
                            success = true,
                            output = output,
                            durationMs = durationOrError.toLongOrNull() ?: durationMs
                        )
                    )
                } else {
                    currentExecutionDeferred?.complete(
                        PyExecutionResult(
                            success = false,
                            output = output,
                            error = durationOrError,
                            durationMs = durationMs
                        )
                    )
                }
                currentExecutionDeferred = null
            }
        }

        // --- Synchronous TCP Socket Bridge for Python ---

        @JavascriptInterface
        fun openSocketSync(host: String, port: Int, timeoutMs: Int): String {
            return try {
                var res = ""
                val deferred = CompletableDeferred<String>()
                scope.launch(Dispatchers.IO) {
                    val result = socketManager.openSocket(host, port, timeoutMs)
                    deferred.complete(result.getOrDefault(""))
                }
                // Run blocking wait since JS expects synchronous socket
                kotlinx.coroutines.runBlocking {
                    deferred.await()
                }
            } catch (e: Exception) {
                Log.e("PyBridge", "openSocketSync failed", e)
                ""
            }
        }

        @JavascriptInterface
        fun sendHexSync(socketId: String, hexData: String): Int {
            return try {
                val bytes = if (hexData.isNotEmpty()) {
                    hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else ByteArray(0)

                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    socketManager.sendBytes(socketId, bytes).getOrDefault(0)
                }
            } catch (e: Exception) {
                Log.e("PyBridge", "sendHexSync failed", e)
                0
            }
        }

        @JavascriptInterface
        fun recvHexSync(socketId: String, maxBytes: Int, timeoutMs: Int): String {
            return try {
                val bytes = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    socketManager.receiveBytes(socketId, maxBytes, timeoutMs).getOrDefault(ByteArray(0))
                }
                bytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e("PyBridge", "recvHexSync failed", e)
                ""
            }
        }

        @JavascriptInterface
        fun closeSocketSync(socketId: String) {
            socketManager.closeSocket(socketId)
        }
    }

    private fun addPipLog(log: String) {
        val list = (_pipLogs.value + log).takeLast(200)
        _pipLogs.value = list
    }

    private fun parseInstalledList(json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<InstalledPackage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    InstalledPackage(
                        name = obj.getString("name"),
                        version = obj.optString("version", "latest")
                    )
                )
            }
            // Add default built-ins for reference
            val builtins = listOf(
                InstalledPackage("micropip", "0.6.0", "PyPI Package Installer", true),
                InstalledPackage("socket", "3.11", "Android TCP Network Bridge", true),
                InstalledPackage("struct", "3.11", "Binary pack/unpack", true),
                InstalledPackage("json", "3.11", "JSON Encoder & Decoder", true),
                InstalledPackage("math", "3.11", "Math functions", true)
            )
            val combined = (builtins + list).distinctBy { it.name }
            _installedPackages.value = combined
        } catch (e: Exception) {
            Log.e("PyodideEngine", "parseInstalledList error", e)
        }
    }

    fun refreshInstalledPackagesList() {
        mainHandler.post {
            webView?.evaluateJavascript("getInstalledPackages();", null)
        }
    }

    suspend fun executeCode(
        code: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): PyExecutionResult {
        onStdoutListener = onStdout
        onStderrListener = onStderr

        val deferred = CompletableDeferred<PyExecutionResult>()
        currentExecutionDeferred = deferred

        withContext(Dispatchers.Main) {
            _engineState.value = EngineState.RUNNING
            val execId = System.currentTimeMillis().toString()
            val encodedCode = Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val js = "runPythonCode(new TextDecoder().decode(Uint8Array.from(atob('$encodedCode'), c => c.charCodeAt(0))), '$execId');"
            webView?.evaluateJavascript(js, null)
        }

        return deferred.await()
    }

    suspend fun installPackage(packageName: String): Pair<Boolean, String> {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return Pair(false, "Paketname ist leer")

        val deferred = CompletableDeferred<Pair<Boolean, String>>()
        currentPipDeferred = deferred

        withContext(Dispatchers.Main) {
            addPipLog("▶ pip install $trimmed")
            webView?.evaluateJavascript("installPackage('$trimmed');", null)
        }

        return deferred.await()
    }

    fun clearPipLogs() {
        _pipLogs.value = emptyList()
    }
}
