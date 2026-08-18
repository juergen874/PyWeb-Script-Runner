package com.example.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class HttpRequestLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val clientIp: String
)

class LocalHttpServer(
    private val scope: CoroutineScope,
    private val onLog: (HttpRequestLog) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    var currentPort: Int = 8080
        private set

    private var defaultHtml: String = """
        <!DOCTYPE html>
        <html lang="de">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>PyRunner Localhost Web UI</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: #0f172a;
                    color: #f8fafc;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    padding: 20px;
                }
                .card {
                    background: #1e293b;
                    border: 1px solid #334155;
                    border-radius: 16px;
                    padding: 32px;
                    max-width: 520px;
                    width: 100%;
                    box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5);
                    text-align: center;
                }
                .badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    background: rgba(56, 189, 248, 0.15);
                    color: #38bdf8;
                    padding: 6px 14px;
                    border-radius: 999px;
                    font-size: 13px;
                    font-weight: 600;
                    margin-bottom: 20px;
                }
                .dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; }
                h1 { font-size: 26px; font-weight: 700; margin-bottom: 12px; color: #fff; }
                p { color: #94a3b8; font-size: 15px; line-height: 1.6; margin-bottom: 24px; }
                .code-box {
                    background: #090d16;
                    border: 1px solid #1e293b;
                    border-radius: 8px;
                    padding: 16px;
                    text-align: left;
                    font-family: monospace;
                    font-size: 13px;
                    color: #38bdf8;
                    margin-bottom: 24px;
                    overflow-x: auto;
                }
                .btn {
                    display: inline-block;
                    background: #3b82f6;
                    color: #fff;
                    font-weight: 600;
                    padding: 12px 24px;
                    border-radius: 10px;
                    text-decoration: none;
                    transition: background 0.2s;
                }
                .btn:hover { background: #2563eb; }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="badge"><span class="dot"></span> Localhost Web Server Aktiv</div>
                <h1>Python Web UI Bereit</h1>
                <p>Führe ein Python-Skript aus, um eine interaktive Web-Oberfläche auf <code>http://127.0.0.1:8080</code> zu hosten.</p>
                <div class="code-box">
# Beispiel Python Web-App:<br>
import web<br>
html = "&lt;h1&gt;Hallo von Python!&lt;/h1&gt;"<br>
web.serve_html(html)
                </div>
                <a href="/api/info" class="btn">JSON API testen</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    private val dynamicRoutes = ConcurrentHashMap<String, suspend (params: Map<String, String>, body: String) -> String>()

    init {
        // Default API route
        dynamicRoutes["/api/info"] = { _, _ ->
            """{"status": "online", "server": "PyRunner Localhost", "timestamp": ${System.currentTimeMillis()}, "engine": "Python 3.11 Embedded"}"""
        }
    }

    fun setHtml(html: String) {
        this.defaultHtml = html
    }

    fun registerRoute(path: String, handler: suspend (params: Map<String, String>, body: String) -> String) {
        dynamicRoutes[path] = handler
    }

    fun clearRoutes() {
        dynamicRoutes.clear()
        dynamicRoutes["/api/info"] = { _, _ ->
            """{"status": "online", "server": "PyRunner Localhost", "timestamp": ${System.currentTimeMillis()}}"""
        }
    }

    fun getRegisteredRoutes(): List<String> = dynamicRoutes.keys.toList()

    fun isRunning(): Boolean = isRunning.get()

    fun start(port: Int = 8080): Boolean {
        if (isRunning.get() && currentPort == port) return true
        stop()

        currentPort = port
        return try {
            val s = ServerSocket(port)
            serverSocket = s
            isRunning.set(true)

            scope.launch(Dispatchers.IO) {
                while (isRunning.get() && !s.isClosed) {
                    try {
                        val clientSocket = s.accept()
                        scope.launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning.set(false)
            false
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
        try {
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output: OutputStream = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = if (rawPath.contains("?")) rawPath.substringBefore("?") else rawPath
            val queryString = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""

            // Parse headers
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Parse body if any
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            // Parse query parameters
            val params = mutableMapOf<String, String>()
            if (queryString.isNotEmpty()) {
                queryString.split("&").forEach { pair ->
                    val kv = pair.split("=")
                    if (kv.isNotEmpty()) {
                        val k = java.net.URLDecoder.decode(kv[0], "UTF-8")
                        val v = if (kv.size > 1) java.net.URLDecoder.decode(kv[1], "UTF-8") else ""
                        params[k] = v
                    }
                }
            }

            // Also parse form body parameters if URL-encoded
            if (body.isNotEmpty() && body.contains("=")) {
                body.split("&").forEach { pair ->
                    val kv = pair.split("=")
                    if (kv.isNotEmpty()) {
                        val k = java.net.URLDecoder.decode(kv[0], "UTF-8")
                        val v = if (kv.size > 1) java.net.URLDecoder.decode(kv[1], "UTF-8") else ""
                        params[k] = v
                    }
                }
            }

            // Check dynamic routes
            val handler = dynamicRoutes[path]
            val responseContent: String
            val contentType: String
            val statusCode: Int

            if (handler != null) {
                val res = handler(params, body)
                responseContent = res
                contentType = if (res.trim().startsWith("{") || res.trim().startsWith("[")) "application/json; charset=UTF-8" else "text/html; charset=UTF-8"
                statusCode = 200
            } else if (path == "/" || path == "/index.html") {
                responseContent = defaultHtml
                contentType = "text/html; charset=UTF-8"
                statusCode = 200
            } else {
                responseContent = """
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family:sans-serif; background:#0f172a; color:#fff; padding:40px; text-align:center;">
                        <h2>404 - Seite nicht gefunden</h2>
                        <p>Die Route <code>$path</code> wurde im Python-Server nicht registriert.</p>
                        <a href="/" style="color:#38bdf8;">Zur Startseite</a>
                    </body>
                    </html>
                """.trimIndent()
                contentType = "text/html; charset=UTF-8"
                statusCode = 404
            }

            val responseBytes = responseContent.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $statusCode OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"

            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(responseBytes)
            output.flush()

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            onLog(
                HttpRequestLog(
                    timestamp = sdf.format(Date()),
                    method = method,
                    path = path,
                    statusCode = statusCode,
                    clientIp = clientIp
                )
            )
        } catch (e: Exception) {
            // Socket handled
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
