package com.example.engine

interface PyServerCallback {
    fun serveHtml(html: String, port: Int = 8080)
    fun registerRoute(path: String, method: String = "GET", handler: suspend (params: Map<String, String>, body: String) -> String)
    fun startServer(port: Int = 8080)
    fun stopServer()
    fun getServerPort(): Int
    fun isServerRunning(): Boolean
}

class PyContext(
    val onStdout: (String) -> Unit,
    val onStderr: (String) -> Unit,
    val onInputRequest: suspend (prompt: String) -> String,
    val serverCallback: PyServerCallback? = null
) {
    val globalScope = mutableMapOf<String, PyValue>()
    val scopes = mutableListOf<MutableMap<String, PyValue>>()
    val virtualFiles = mutableMapOf<String, String>()
    var isCancelled = false

    init {
        // Pre-populate some virtual demo files
        virtualFiles["data.json"] = "{\"appName\": \"Python Runner\", \"version\": \"1.0.0\", \"items\": [\"Python\", \"Terminal\", \"Localhost\"]}"
        virtualFiles["welcome.txt"] = "Willkommen beim mobilen Python Runner!\nEntwickle Skripte, starte Web-Apps und nutze das interaktive Terminal.\n"
    }

    fun getVariable(name: String): PyValue {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name)) {
                return scopes[i][name]!!
            }
        }
        if (globalScope.containsKey(name)) {
            return globalScope[name]!!
        }
        throw RuntimeException("NameError: name '$name' is not defined")
    }

    fun setVariable(name: String, value: PyValue) {
        if (scopes.isNotEmpty()) {
            scopes.last()[name] = value
        } else {
            globalScope[name] = value
        }
    }

    fun setGlobal(name: String, value: PyValue) {
        globalScope[name] = value
    }

    fun pushScope(newScope: MutableMap<String, PyValue> = mutableMapOf()) {
        scopes.add(newScope)
    }

    fun popScope() {
        if (scopes.isNotEmpty()) {
            scopes.removeAt(scopes.size - 1)
        }
    }
}
