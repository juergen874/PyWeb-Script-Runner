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
import types
import io
import time

# Socket constants
AF_UNSPEC = 0
AF_UNIX = 1
AF_INET = 2
AF_INET6 = 10

SOCK_STREAM = 1
SOCK_DGRAM = 2
SOCK_RAW = 3
SOCK_RDM = 4
SOCK_SEQPACKET = 5

SOL_SOCKET = 1
SOL_IP = 0
SOL_TCP = 6
SOL_UDP = 17

SO_DEBUG = 1
SO_REUSEADDR = 2
SO_TYPE = 3
SO_ERROR = 4
SO_DONTROUTE = 5
SO_BROADCAST = 6
SO_SNDBUF = 7
SO_RCVBUF = 8
SO_KEEPALIVE = 9
SO_OOBINLINE = 10
SO_RCVTIMEO = 20
SO_SNDTIMEO = 21

IPPROTO_IP = 0
IPPROTO_ICMP = 1
IPPROTO_TCP = 6
IPPROTO_UDP = 17
IPPROTO_RAW = 255
IPPROTO_IPV6 = 41

TCP_NODELAY = 1

SHUT_RD = 0
SHUT_WR = 1
SHUT_RDWR = 2

INADDR_ANY = "0.0.0.0"
INADDR_BROADCAST = "255.255.255.255"
INADDR_NONE = "255.255.255.255"
INADDR_LOOPBACK = "127.0.0.1"

has_ipv6 = True

# Exceptions
error = OSError
herror = OSError
gaierror = OSError
timeout = TimeoutError

def gethostname():
    return "localhost"

def gethostbyname(hostname):
    return str(hostname)

def gethostbyname_ex(hostname):
    return (str(hostname), [], [str(hostname)])

def getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):
    f = family if family != 0 else AF_INET
    t = type if type != 0 else SOCK_STREAM
    p = proto if proto != 0 else (IPPROTO_TCP if t == SOCK_STREAM else IPPROTO_UDP)
    return [(f, t, p, "", (str(host), int(port) if port else 0))]

def getnameinfo(sockaddr, flags=0):
    return (str(sockaddr[0]), str(sockaddr[1]))

def inet_aton(ip_string):
    parts = [int(p) for p in str(ip_string).split('.')]
    return bytes(parts)

def inet_ntoa(packed_ip):
    return '.'.join(str(b) for b in packed_ip)

def htons(x): return x
def htonl(x): return x
def ntohs(x): return x
def ntohl(x): return x

class SocketIO(io.RawIOBase):
    def __init__(self, sock, mode):
        self._sock = sock
        self._mode = mode
        super().__init__()

    def readinto(self, b):
        data = self._sock.recv(len(b))
        n = len(data)
        b[:n] = data
        return n

    def write(self, b):
        return self._sock.send(b)

    def readable(self):
        return 'r' in self._mode or '+' in self._mode

    def writable(self):
        return 'w' in self._mode or '+' in self._mode or 'a' in self._mode

    def seekable(self):
        return False

    def close(self):
        super().close()

# Custom Android Network Socket Bridge
class AndroidSocket:
    AF_INET = 2
    SOCK_STREAM = 1
    SOCK_DGRAM = 2

    def __init__(self, family=2, type=1, proto=0, fileno=None):
        self.family = family
        self.type = type
        self.proto = proto
        self.socket_id = None
        self.timeout_ms = 5000
        self._closed = False
        self._connected = False
        self._bound_addr = None

    def connect(self, address):
        host, port = address
        self.socket_id = js.AndroidBridge.openSocketSync(str(host), int(port), int(self.timeout_ms))
        if not self.socket_id or self.socket_id == "":
            raise ConnectionError(f"Verbindung zu {host}:{port} fehlgeschlagen.")
        self._connected = True

    def connect_ex(self, address):
        try:
            self.connect(address)
            return 0
        except Exception:
            return 111

    def settimeout(self, timeout_sec):
        if timeout_sec is not None:
            self.timeout_ms = int(timeout_sec * 1000)
        else:
            self.timeout_ms = 10000

    def gettimeout(self):
        return self.timeout_ms / 1000.0

    def setblocking(self, flag):
        pass

    def setsockopt(self, level, optname, value):
        pass

    def getsockopt(self, level, optname, buflen=None):
        return 0

    def bind(self, address):
        self._bound_addr = address

    def listen(self, backlog=5):
        pass

    def accept(self):
        return (self, self._bound_addr or ("127.0.0.1", 8080))

    def send(self, data):
        if isinstance(data, (bytes, bytearray, memoryview)):
            hex_data = bytes(data).hex()
        elif isinstance(data, str):
            hex_data = data.encode('utf-8').hex()
        else:
            hex_data = ""
        sent = js.AndroidBridge.sendHexSync(str(self.socket_id), hex_data)
        return int(sent)

    def sendall(self, data):
        return self.send(data)

    def sendto(self, data, address):
        if not self.socket_id:
            self.connect(address)
        return self.send(data)

    def recv(self, bufsize=1024):
        if not self.socket_id:
            return b""
        hex_resp = js.AndroidBridge.recvHexSync(str(self.socket_id), int(bufsize), int(self.timeout_ms))
        if not hex_resp:
            return b""
        return bytes.fromhex(str(hex_resp))

    def recv_into(self, buffer, nbytes=0):
        target_size = nbytes if nbytes > 0 else len(buffer)
        data = self.recv(target_size)
        n = len(data)
        buffer[:n] = data
        return n

    def recvfrom(self, bufsize=1024):
        data = self.recv(bufsize)
        return (data, ("127.0.0.1", 0))

    def close(self):
        if self.socket_id:
            js.AndroidBridge.closeSocketSync(str(self.socket_id))
            self.socket_id = None
        self._closed = True
        self._connected = False

    def shutdown(self, how=SHUT_RDWR):
        self.close()

    def fileno(self):
        return 100

    def dup(self):
        return self

    def makefile(self, mode="r", buffering=None, encoding=None, errors=None, newline=None):
        raw = SocketIO(self, mode)
        if "b" in mode:
            return raw
        return io.TextIOWrapper(raw, encoding=encoding or "utf-8", errors=errors, newline=newline)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False

def create_connection(address, timeout=10.0, source_address=None):
    s = AndroidSocket(AF_INET, SOCK_STREAM)
    if timeout is not None:
        s.settimeout(timeout)
    s.connect(address)
    return s

socket_module = types.ModuleType("socket")
_socket_module = types.ModuleType("_socket")

attrs = {
    "AF_UNSPEC": AF_UNSPEC, "AF_UNIX": AF_UNIX, "AF_INET": AF_INET, "AF_INET6": AF_INET6,
    "SOCK_STREAM": SOCK_STREAM, "SOCK_DGRAM": SOCK_DGRAM, "SOCK_RAW": SOCK_RAW,
    "SOCK_RDM": SOCK_RDM, "SOCK_SEQPACKET": SOCK_SEQPACKET,
    "SOL_SOCKET": SOL_SOCKET, "SOL_IP": SOL_IP, "SOL_TCP": SOL_TCP, "SOL_UDP": SOL_UDP,
    "SO_DEBUG": SO_DEBUG, "SO_REUSEADDR": SO_REUSEADDR, "SO_TYPE": SO_TYPE, "SO_ERROR": SO_ERROR,
    "SO_DONTROUTE": SO_DONTROUTE, "SO_BROADCAST": SO_BROADCAST, "SO_SNDBUF": SO_SNDBUF,
    "SO_RCVBUF": SO_RCVBUF, "SO_KEEPALIVE": SO_KEEPALIVE, "SO_OOBINLINE": SO_OOBINLINE,
    "SO_RCVTIMEO": SO_RCVTIMEO, "SO_SNDTIMEO": SO_SNDTIMEO,
    "IPPROTO_IP": IPPROTO_IP, "IPPROTO_ICMP": IPPROTO_ICMP, "IPPROTO_TCP": IPPROTO_TCP,
    "IPPROTO_UDP": IPPROTO_UDP, "IPPROTO_RAW": IPPROTO_RAW, "IPPROTO_IPV6": IPPROTO_IPV6,
    "TCP_NODELAY": TCP_NODELAY,
    "SHUT_RD": SHUT_RD, "SHUT_WR": SHUT_WR, "SHUT_RDWR": SHUT_RDWR,
    "INADDR_ANY": INADDR_ANY, "INADDR_BROADCAST": INADDR_BROADCAST,
    "INADDR_NONE": INADDR_NONE, "INADDR_LOOPBACK": INADDR_LOOPBACK,
    "has_ipv6": True,
    "error": error, "herror": herror, "gaierror": gaierror, "timeout": timeout,
    "gethostname": gethostname, "gethostbyname": gethostbyname, "gethostbyname_ex": gethostbyname_ex,
    "getaddrinfo": getaddrinfo, "getnameinfo": getnameinfo,
    "inet_aton": inet_aton, "inet_ntoa": inet_ntoa,
    "htons": htons, "htonl": htonl, "ntohs": ntohs, "ntohl": ntohl,
    "socket": AndroidSocket, "SocketType": AndroidSocket,
    "create_connection": create_connection, "SocketIO": SocketIO,
    "_GLOBAL_DEFAULT_TIMEOUT": object()
}

for k, v in attrs.items():
    setattr(socket_module, k, v)
    setattr(_socket_module, k, v)

sys.modules["_socket"] = _socket_module
sys.modules["socket"] = socket_module
            `);

            window.AndroidBridge.onPipLog("✅ Python 3.11 Engine & Pip einsatzbereit!");
            window.AndroidBridge.onEngineReady();
        } catch (err) {
            window.AndroidBridge.onEngineError(err.toString());
        }
    }

    async function runPythonCode(code, execId) {
        if (!pyodide) {
            window.AndroidBridge.onExecFailure(execId, "Engine nicht bereit", 0);
            return;
        }
        const t0 = performance.now();
        try {
            const result = await pyodide.runPythonAsync(code);
            const duration = Math.round(performance.now() - t0);
            const resStr = (result !== undefined && result !== null) ? String(result) : "";
            window.AndroidBridge.onExecSuccess(execId, resStr, duration);
        } catch (err) {
            const duration = Math.round(performance.now() - t0);
            window.AndroidBridge.onExecFailure(execId, err.toString(), duration);
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
        fun onExecSuccess(execId: String, output: String, durationMs: Long) {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.READY
                currentExecutionDeferred?.complete(
                    PyExecutionResult(
                        success = true,
                        output = output,
                        durationMs = durationMs
                    )
                )
                currentExecutionDeferred = null
            }
        }

        @JavascriptInterface
        fun onExecFailure(execId: String, error: String, durationMs: Long) {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.READY
                currentExecutionDeferred?.complete(
                    PyExecutionResult(
                        success = false,
                        output = "",
                        error = error,
                        durationMs = durationMs
                    )
                )
                currentExecutionDeferred = null
            }
        }

        @JavascriptInterface
        fun onExecResult(execId: String, success: Boolean, output: String, durationOrError: String) {
            scope.launch(Dispatchers.Main) {
                _engineState.value = EngineState.READY
                if (success) {
                    currentExecutionDeferred?.complete(
                        PyExecutionResult(
                            success = true,
                            output = output,
                            durationMs = durationOrError.toLongOrNull() ?: 0L
                        )
                    )
                } else {
                    currentExecutionDeferred?.complete(
                        PyExecutionResult(
                            success = false,
                            output = output,
                            error = durationOrError,
                            durationMs = 0L
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
