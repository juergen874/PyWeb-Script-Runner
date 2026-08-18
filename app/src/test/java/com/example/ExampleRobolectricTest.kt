package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Python Runner", appName)
    }

    @Test
    fun `test python interpreter basic execution`() = runBlocking {
        val stdoutList = mutableListOf<String>()
        val ctx = PyContext(
            onStdout = { stdoutList.add(it.trimEnd('\n')) },
            onStderr = {},
            onInputRequest = { "" }
        )

        val code = """
            def fib(n):
                if n <= 1:
                    return n
                return fib(n - 1) + fib(n - 2)

            res = fib(7)
            print(f"Fibonacci 7 is {res}")
            primes = [x for x in range(2, 15) if x % 2 != 0]
            print(f"Primes: {primes}")
        """.trimIndent()

        val lexer = PyLexer(code)
        val tokens = lexer.tokenize()
        val parser = PyParser(tokens)
        val statements = parser.parse()

        val interpreter = PyInterpreter(ctx)
        interpreter.execute(statements)

        assertEquals(2, stdoutList.size)
        assertEquals("Fibonacci 7 is 13", stdoutList[0])
        assertTrue(stdoutList[1].contains("3, 5, 7, 9, 11, 13"))
    }

    @Test
    fun `test python interpreter web module`() = runBlocking {
        var servedHtml: String? = null
        var servedPort: Int? = null

        val serverCallback = object : PyServerCallback {
            override fun serveHtml(html: String, port: Int) {
                servedHtml = html
                servedPort = port
            }
            override fun registerRoute(path: String, method: String, handler: suspend (Map<String, String>, String) -> String) {}
            override fun startServer(port: Int) {}
            override fun stopServer() {}
            override fun getServerPort(): Int = 8080
            override fun isServerRunning(): Boolean = true
        }

        val ctx = PyContext(
            onStdout = {},
            onStderr = {},
            onInputRequest = { "" },
            serverCallback = serverCallback
        )

        val code = """
            import web
            web.serve_html("<h1>Hallo Welt</h1>", port=8080)
        """.trimIndent()

        val lexer = PyLexer(code)
        val parser = PyParser(lexer.tokenize())
        val statements = parser.parse()
        val interpreter = PyInterpreter(ctx)
        interpreter.execute(statements)

        assertEquals("<h1>Hallo Welt</h1>", servedHtml)
        assertEquals(8080, servedPort)
    }
}
