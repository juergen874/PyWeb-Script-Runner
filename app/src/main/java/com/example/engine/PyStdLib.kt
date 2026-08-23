package com.example.engine

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

object PyStdLib {

    fun populateBuiltins(ctx: PyContext) {
        // print(*args, sep=' ', end='\n')
        ctx.globalScope["print"] = PyValue.BuiltinFuncVal("print") { args, kwargs, c ->
            val sep = (kwargs["sep"] as? PyValue.StringVal)?.value ?: " "
            val end = (kwargs["end"] as? PyValue.StringVal)?.value ?: "\n"
            val text = args.joinToString(sep) { it.toDisplayString() } + end
            c.onStdout(text)
            PyValue.NoneVal
        }

        // input(prompt='')
        ctx.globalScope["input"] = PyValue.BuiltinFuncVal("input") { args, _, c ->
            val prompt = if (args.isNotEmpty()) args[0].toDisplayString() else ""
            if (prompt.isNotEmpty()) {
                c.onStdout(prompt)
            }
            val userInput = c.onInputRequest(prompt)
            // Echo input in standard python terminal
            c.onStdout("$userInput\n")
            PyValue.StringVal(userInput)
        }

        // len(x)
        ctx.globalScope["len"] = PyValue.BuiltinFuncVal("len") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: len() takes exactly one argument (0 given)")
            val len = when (val item = args[0]) {
                is PyValue.StringVal -> item.value.length
                is PyValue.ListVal -> item.elements.size
                is PyValue.TupleVal -> item.elements.size
                is PyValue.DictVal -> item.entries.size
                is PyValue.SetVal -> item.elements.size
                else -> throw RuntimeException("TypeError: object of type '${item.typeName()}' has no len()")
            }
            PyValue.IntVal(len.toLong())
        }

        // range(start, stop, step)
        ctx.globalScope["range"] = PyValue.BuiltinFuncVal("range") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: range expected at least 1 argument, got 0")
            var start = 0L
            var stop = 0L
            var step = 1L

            if (args.size == 1) {
                stop = (args[0] as? PyValue.IntVal)?.value ?: throw RuntimeException("TypeError: 'float' object cannot be interpreted as an integer")
            } else if (args.size == 2) {
                start = (args[0] as? PyValue.IntVal)?.value ?: 0L
                stop = (args[1] as? PyValue.IntVal)?.value ?: 0L
            } else {
                start = (args[0] as? PyValue.IntVal)?.value ?: 0L
                stop = (args[1] as? PyValue.IntVal)?.value ?: 0L
                step = (args[2] as? PyValue.IntVal)?.value ?: 1L
            }

            if (step == 0L) throw RuntimeException("ValueError: range() arg 3 must not be zero")

            val list = mutableListOf<PyValue>()
            if (step > 0) {
                var curr = start
                while (curr < stop) {
                    list.add(PyValue.IntVal(curr))
                    curr += step
                }
            } else {
                var curr = start
                while (curr > stop) {
                    list.add(PyValue.IntVal(curr))
                    curr += step
                }
            }
            PyValue.ListVal(list)
        }

        // sum(iterable, start=0)
        ctx.globalScope["sum"] = PyValue.BuiltinFuncVal("sum") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: sum() missing required argument 'iterable'")
            val iterable = when (val item = args[0]) {
                is PyValue.ListVal -> item.elements
                is PyValue.TupleVal -> item.elements
                is PyValue.SetVal -> item.elements.toList()
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
            var sumInt = (args.getOrNull(1) as? PyValue.IntVal)?.value ?: 0L
            var sumFloat = (args.getOrNull(1) as? PyValue.FloatVal)?.value ?: 0.0
            var isFloat = args.getOrNull(1) is PyValue.FloatVal

            for (elem in iterable) {
                when (elem) {
                    is PyValue.IntVal -> {
                        sumInt += elem.value
                        sumFloat += elem.value
                    }
                    is PyValue.FloatVal -> {
                        isFloat = true
                        sumFloat += elem.value
                    }
                    else -> throw RuntimeException("TypeError: unsupported operand type(s) for +: 'int' and '${elem.typeName()}'")
                }
            }
            if (isFloat) PyValue.FloatVal(sumFloat) else PyValue.IntVal(sumInt)
        }

        // min(*args) / min(iterable)
        ctx.globalScope["min"] = PyValue.BuiltinFuncVal("min") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("ValueError: min() arg is an empty sequence")
            val items = if (args.size == 1 && args[0] is PyValue.ListVal) (args[0] as PyValue.ListVal).elements
            else if (args.size == 1 && args[0] is PyValue.TupleVal) (args[0] as PyValue.TupleVal).elements
            else args

            var minVal = items[0]
            for (item in items) {
                if (comparePyValues(item, minVal) < 0) {
                    minVal = item
                }
            }
            minVal
        }

        // max(*args) / max(iterable)
        ctx.globalScope["max"] = PyValue.BuiltinFuncVal("max") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("ValueError: max() arg is an empty sequence")
            val items = if (args.size == 1 && args[0] is PyValue.ListVal) (args[0] as PyValue.ListVal).elements
            else if (args.size == 1 && args[0] is PyValue.TupleVal) (args[0] as PyValue.TupleVal).elements
            else args

            var maxVal = items[0]
            for (item in items) {
                if (comparePyValues(item, maxVal) > 0) {
                    maxVal = item
                }
            }
            maxVal
        }

        // abs(x)
        ctx.globalScope["abs"] = PyValue.BuiltinFuncVal("abs") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: abs() takes exactly one argument")
            when (val item = args[0]) {
                is PyValue.IntVal -> PyValue.IntVal(abs(item.value))
                is PyValue.FloatVal -> PyValue.FloatVal(abs(item.value))
                else -> throw RuntimeException("TypeError: bad operand type for abs(): '${item.typeName()}'")
            }
        }

        // round(x, n=0)
        ctx.globalScope["round"] = PyValue.BuiltinFuncVal("round") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: round() takes at least 1 argument")
            val num = when (val item = args[0]) {
                is PyValue.IntVal -> item.value.toDouble()
                is PyValue.FloatVal -> item.value
                else -> throw RuntimeException("TypeError: type '${item.typeName()}' doesn't define round")
            }
            val digits = (args.getOrNull(1) as? PyValue.IntVal)?.value?.toInt() ?: 0
            if (digits == 0) {
                PyValue.IntVal(Math.round(num))
            } else {
                val factor = 10.0.pow(digits)
                PyValue.FloatVal(Math.round(num * factor) / factor)
            }
        }

        // int(x), float(x), str(x), bool(x), list(x), dict(x), set(x), tuple(x)
        ctx.globalScope["int"] = PyValue.BuiltinFuncVal("int") { args, _, _ ->
            if (args.isEmpty()) PyValue.IntVal(0)
            else when (val item = args[0]) {
                is PyValue.IntVal -> item
                is PyValue.FloatVal -> PyValue.IntVal(item.value.toLong())
                is PyValue.StringVal -> PyValue.IntVal(item.value.trim().toLongOrNull() ?: throw RuntimeException("ValueError: invalid literal for int() with base 10: '${item.value}'"))
                is PyValue.BoolVal -> PyValue.IntVal(if (item.value) 1 else 0)
                else -> throw RuntimeException("TypeError: int() argument must be a string or number, not '${item.typeName()}'")
            }
        }

        ctx.globalScope["float"] = PyValue.BuiltinFuncVal("float") { args, _, _ ->
            if (args.isEmpty()) PyValue.FloatVal(0.0)
            else when (val item = args[0]) {
                is PyValue.FloatVal -> item
                is PyValue.IntVal -> PyValue.FloatVal(item.value.toDouble())
                is PyValue.StringVal -> PyValue.FloatVal(item.value.trim().toDoubleOrNull() ?: throw RuntimeException("ValueError: could not convert string to float: '${item.value}'"))
                is PyValue.BoolVal -> PyValue.FloatVal(if (item.value) 1.0 else 0.0)
                else -> throw RuntimeException("TypeError: float() argument must be a string or number, not '${item.typeName()}'")
            }
        }

        ctx.globalScope["str"] = PyValue.BuiltinFuncVal("str") { args, _, _ ->
            if (args.isEmpty()) PyValue.StringVal("")
            else PyValue.StringVal(args[0].toDisplayString())
        }

        ctx.globalScope["bool"] = PyValue.BuiltinFuncVal("bool") { args, _, _ ->
            if (args.isEmpty()) PyValue.BoolVal(false)
            else PyValue.BoolVal(args[0].isTruthy())
        }

        ctx.globalScope["list"] = PyValue.BuiltinFuncVal("list") { args, _, _ ->
            if (args.isEmpty()) PyValue.ListVal(mutableListOf())
            else when (val item = args[0]) {
                is PyValue.ListVal -> PyValue.ListVal(item.elements.toMutableList())
                is PyValue.TupleVal -> PyValue.ListVal(item.elements.toMutableList())
                is PyValue.SetVal -> PyValue.ListVal(item.elements.toMutableList())
                is PyValue.StringVal -> PyValue.ListVal(item.value.map { PyValue.StringVal(it.toString()) }.toMutableList())
                is PyValue.DictVal -> PyValue.ListVal(item.entries.keys.map { PyValue.StringVal(it) }.toMutableList())
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
        }

        ctx.globalScope["dict"] = PyValue.BuiltinFuncVal("dict") { args, kwargs, _ ->
            val dict = mutableMapOf<String, PyValue>()
            for ((k, v) in kwargs) {
                dict[k] = v
            }
            if (args.isNotEmpty()) {
                val item = args[0]
                if (item is PyValue.DictVal) {
                    dict.putAll(item.entries)
                }
            }
            PyValue.DictVal(dict)
        }

        ctx.globalScope["set"] = PyValue.BuiltinFuncVal("set") { args, _, _ ->
            if (args.isEmpty()) PyValue.SetVal(mutableSetOf())
            else when (val item = args[0]) {
                is PyValue.ListVal -> PyValue.SetVal(item.elements.toMutableSet())
                is PyValue.TupleVal -> PyValue.SetVal(item.elements.toMutableSet())
                is PyValue.SetVal -> PyValue.SetVal(item.elements.toMutableSet())
                is PyValue.StringVal -> PyValue.SetVal(item.value.map { PyValue.StringVal(it.toString()) }.toMutableSet())
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
        }

        ctx.globalScope["tuple"] = PyValue.BuiltinFuncVal("tuple") { args, _, _ ->
            if (args.isEmpty()) PyValue.TupleVal(emptyList())
            else when (val item = args[0]) {
                is PyValue.ListVal -> PyValue.TupleVal(item.elements.toList())
                is PyValue.TupleVal -> item
                is PyValue.SetVal -> PyValue.TupleVal(item.elements.toList())
                is PyValue.StringVal -> PyValue.TupleVal(item.value.map { PyValue.StringVal(it.toString()) })
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
        }

        ctx.globalScope["type"] = PyValue.BuiltinFuncVal("type") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: type() takes 1 or 3 arguments")
            PyValue.StringVal("<class '${args[0].typeName()}'>")
        }

        ctx.globalScope["enumerate"] = PyValue.BuiltinFuncVal("enumerate") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: enumerate() takes at least 1 argument")
            val list = when (val item = args[0]) {
                is PyValue.ListVal -> item.elements
                is PyValue.TupleVal -> item.elements
                is PyValue.StringVal -> item.value.map { PyValue.StringVal(it.toString()) }
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
            val start = (args.getOrNull(1) as? PyValue.IntVal)?.value ?: 0L
            val result = list.mapIndexed { idx, elem ->
                PyValue.TupleVal(listOf(PyValue.IntVal(start + idx), elem))
            }
            PyValue.ListVal(result.toMutableList())
        }

        ctx.globalScope["zip"] = PyValue.BuiltinFuncVal("zip") { args, _, _ ->
            val lists = args.map {
                when (it) {
                    is PyValue.ListVal -> it.elements
                    is PyValue.TupleVal -> it.elements
                    is PyValue.StringVal -> it.value.map { c -> PyValue.StringVal(c.toString()) }
                    else -> emptyList()
                }
            }
            val minSize = lists.minOfOrNull { it.size } ?: 0
            val result = mutableListOf<PyValue>()
            for (i in 0 until minSize) {
                val row = lists.map { it[i] }
                result.add(PyValue.TupleVal(row))
            }
            PyValue.ListVal(result)
        }

        ctx.globalScope["sorted"] = PyValue.BuiltinFuncVal("sorted") { args, kwargs, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: sorted expected 1 argument, got 0")
            val list: MutableList<PyValue> = mutableListOf()
            when (val item = args[0]) {
                is PyValue.ListVal -> list.addAll(item.elements)
                is PyValue.TupleVal -> list.addAll(item.elements)
                is PyValue.SetVal -> list.addAll(item.elements)
                is PyValue.StringVal -> item.value.forEach { list.add(PyValue.StringVal(it.toString())) }
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not iterable")
            }
            val reverse = (kwargs["reverse"] as? PyValue.BoolVal)?.value ?: false
            list.sortWith { a, b -> comparePyValues(a, b) }
            if (reverse) list.reverse()
            PyValue.ListVal(list)
        }

        ctx.globalScope["reversed"] = PyValue.BuiltinFuncVal("reversed") { args, _, _ ->
            if (args.isEmpty()) throw RuntimeException("TypeError: reversed expected 1 argument, got 0")
            val list: MutableList<PyValue> = mutableListOf()
            when (val item = args[0]) {
                is PyValue.ListVal -> list.addAll(item.elements.asReversed())
                is PyValue.TupleVal -> list.addAll(item.elements.asReversed())
                is PyValue.StringVal -> item.value.reversed().forEach { list.add(PyValue.StringVal(it.toString())) }
                else -> throw RuntimeException("TypeError: '${item.typeName()}' object is not reversible")
            }
            PyValue.ListVal(list)
        }

        ctx.globalScope["open"] = PyValue.BuiltinFuncVal("open") { args, _, c ->
            val filename = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "file.txt"
            val mode = (args.getOrNull(1) as? PyValue.StringVal)?.value ?: "r"

            // Virtual file object
            val fileObj = PyValue.InstanceVal(PyValue.ClassVal("File"))
            fileObj.fields["name"] = PyValue.StringVal(filename)
            fileObj.fields["mode"] = PyValue.StringVal(mode)

            fileObj.fields["read"] = PyValue.BuiltinFuncVal("read") { _, _, _ ->
                val content = c.virtualFiles[filename] ?: ""
                PyValue.StringVal(content)
            }
            fileObj.fields["write"] = PyValue.BuiltinFuncVal("write") { wArgs, _, _ ->
                val text = (wArgs.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                val currentContent = if (mode.contains("a")) (c.virtualFiles[filename] ?: "") else ""
                c.virtualFiles[filename] = currentContent + text
                PyValue.IntVal(text.length.toLong())
            }
            fileObj.fields["close"] = PyValue.BuiltinFuncVal("close") { _, _, _ ->
                PyValue.NoneVal
            }
            fileObj
        }

        ctx.globalScope["help"] = PyValue.BuiltinFuncVal("help") { args, _, c ->
            if (args.isEmpty()) {
                c.onStdout("Python Runner Hilfe:\nVerfügbare Module: math, random, time, json, datetime, sys, os, web, flask\n")
            } else {
                c.onStdout("Hilfe für ${args[0].toDisplayString()}: ${args[0].typeName()}\n")
            }
            PyValue.NoneVal
        }

        // chr & ord
        ctx.globalScope["chr"] = PyValue.BuiltinFuncVal("chr") { args, _, _ ->
            val code = (args.getOrNull(0) as? PyValue.IntVal)?.value?.toInt() ?: 0
            PyValue.StringVal(code.toChar().toString())
        }

        ctx.globalScope["ord"] = PyValue.BuiltinFuncVal("ord") { args, _, _ ->
            val str = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
            if (str.isEmpty()) throw RuntimeException("TypeError: ord() expected a character")
            PyValue.IntVal(str[0].code.toLong())
        }

        // bytes(x) & bytearray(x)
        val bytesFunc = PyValue.BuiltinFuncVal("bytes") { args, _, _ ->
            if (args.isEmpty()) return@BuiltinFuncVal PyValue.BytesVal(ByteArray(0))
            when (val item = args[0]) {
                is PyValue.BytesVal -> item
                is PyValue.StringVal -> {
                    val enc = (args.getOrNull(1) as? PyValue.StringVal)?.value ?: "utf-8"
                    try {
                        PyValue.BytesVal(item.value.toByteArray(charset(enc)))
                    } catch (e: Exception) {
                        PyValue.BytesVal(item.value.toByteArray(Charsets.UTF_8))
                    }
                }
                is PyValue.IntVal -> PyValue.BytesVal(ByteArray(item.value.toInt().coerceAtLeast(0)))
                is PyValue.ListVal -> {
                    val arr = ByteArray(item.elements.size)
                    for (i in item.elements.indices) {
                        val v = (item.elements[i] as? PyValue.IntVal)?.value?.toInt() ?: 0
                        arr[i] = (v and 0xFF).toByte()
                    }
                    PyValue.BytesVal(arr)
                }
                else -> PyValue.BytesVal(ByteArray(0))
            }
        }
        ctx.globalScope["bytes"] = bytesFunc
        ctx.globalScope["bytearray"] = bytesFunc

        // bytes.fromhex helper
        val bytesClass = PyValue.ClassVal("bytes")
        bytesClass.methods["fromhex"] = PyValue.FunctionVal("fromhex", listOf("hex_str"), emptyMap(), null, emptyList(), mutableMapOf())
        ctx.globalScope["bytes_class"] = bytesClass

        // Quick helpers for German users / web runner
        ctx.globalScope["serve_html"] = PyValue.BuiltinFuncVal("serve_html") { args, kwargs, c ->
            val html = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "<h1>Python Web App</h1>"
            val port = (kwargs["port"] as? PyValue.IntVal)?.value?.toInt()
                ?: (args.getOrNull(1) as? PyValue.IntVal)?.value?.toInt()
                ?: 8080
            c.serverCallback?.serveHtml(html, port)
            c.onStdout("🚀 Localhost Web UI gestartet: http://127.0.0.1:$port\n")
            PyValue.StringVal("http://127.0.0.1:$port")
        }
    }

    fun loadModule(name: String, ctx: PyContext): PyValue.ModuleVal {
        val module = PyValue.ModuleVal(name)
        when (name) {
            "math" -> {
                module.members["pi"] = PyValue.FloatVal(Math.PI)
                module.members["e"] = PyValue.FloatVal(Math.E)
                module.members["tau"] = PyValue.FloatVal(Math.PI * 2)
                module.members["inf"] = PyValue.FloatVal(Double.POSITIVE_INFINITY)
                module.members["nan"] = PyValue.FloatVal(Double.NaN)
                module.members["sqrt"] = PyValue.BuiltinFuncVal("sqrt") { args, _, _ ->
                    val x = getDouble(args.getOrNull(0))
                    PyValue.FloatVal(sqrt(x))
                }
                module.members["sin"] = PyValue.BuiltinFuncVal("sin") { args, _, _ -> PyValue.FloatVal(sin(getDouble(args.getOrNull(0)))) }
                module.members["cos"] = PyValue.BuiltinFuncVal("cos") { args, _, _ -> PyValue.FloatVal(cos(getDouble(args.getOrNull(0)))) }
                module.members["tan"] = PyValue.BuiltinFuncVal("tan") { args, _, _ -> PyValue.FloatVal(tan(getDouble(args.getOrNull(0)))) }
                module.members["floor"] = PyValue.BuiltinFuncVal("floor") { args, _, _ -> PyValue.IntVal(floor(getDouble(args.getOrNull(0))).toLong()) }
                module.members["ceil"] = PyValue.BuiltinFuncVal("ceil") { args, _, _ -> PyValue.IntVal(ceil(getDouble(args.getOrNull(0))).toLong()) }
                module.members["log"] = PyValue.BuiltinFuncVal("log") { args, _, _ -> PyValue.FloatVal(ln(getDouble(args.getOrNull(0)))) }
                module.members["log10"] = PyValue.BuiltinFuncVal("log10") { args, _, _ -> PyValue.FloatVal(log10(getDouble(args.getOrNull(0)))) }
                module.members["exp"] = PyValue.BuiltinFuncVal("exp") { args, _, _ -> PyValue.FloatVal(exp(getDouble(args.getOrNull(0)))) }
                module.members["pow"] = PyValue.BuiltinFuncVal("pow") { args, _, _ -> PyValue.FloatVal(getDouble(args.getOrNull(0)).pow(getDouble(args.getOrNull(1)))) }
                module.members["radians"] = PyValue.BuiltinFuncVal("radians") { args, _, _ -> PyValue.FloatVal(Math.toRadians(getDouble(args.getOrNull(0)))) }
                module.members["degrees"] = PyValue.BuiltinFuncVal("degrees") { args, _, _ -> PyValue.FloatVal(Math.toDegrees(getDouble(args.getOrNull(0)))) }
                module.members["factorial"] = PyValue.BuiltinFuncVal("factorial") { args, _, _ ->
                    val n = (args.getOrNull(0) as? PyValue.IntVal)?.value ?: 0L
                    var res = 1L
                    for (i in 1..n) res *= i
                    PyValue.IntVal(res)
                }
                module.members["gcd"] = PyValue.BuiltinFuncVal("gcd") { args, _, _ ->
                    var a = abs((args.getOrNull(0) as? PyValue.IntVal)?.value ?: 0L)
                    var b = abs((args.getOrNull(1) as? PyValue.IntVal)?.value ?: 0L)
                    while (b != 0L) {
                        val t = b
                        b = a % b
                        a = t
                    }
                    PyValue.IntVal(a)
                }
            }

            "random" -> {
                module.members["random"] = PyValue.BuiltinFuncVal("random") { _, _, _ -> PyValue.FloatVal(Random.nextDouble()) }
                module.members["randint"] = PyValue.BuiltinFuncVal("randint") { args, _, _ ->
                    val a = (args.getOrNull(0) as? PyValue.IntVal)?.value ?: 0L
                    val b = (args.getOrNull(1) as? PyValue.IntVal)?.value ?: 100L
                    PyValue.IntVal(Random.nextLong(a, b + 1))
                }
                module.members["choice"] = PyValue.BuiltinFuncVal("choice") { args, _, _ ->
                    val list = when (val item = args.getOrNull(0)) {
                        is PyValue.ListVal -> item.elements
                        is PyValue.TupleVal -> item.elements
                        is PyValue.StringVal -> item.value.map { PyValue.StringVal(it.toString()) }
                        else -> throw RuntimeException("TypeError: choice requires non-empty sequence")
                    }
                    if (list.isEmpty()) throw RuntimeException("IndexError: Cannot choose from an empty sequence")
                    list.random()
                }
                module.members["shuffle"] = PyValue.BuiltinFuncVal("shuffle") { args, _, _ ->
                    val list = (args.getOrNull(0) as? PyValue.ListVal)?.elements
                        ?: throw RuntimeException("TypeError: shuffle requires a list")
                    list.shuffle()
                    PyValue.NoneVal
                }
                module.members["uniform"] = PyValue.BuiltinFuncVal("uniform") { args, _, _ ->
                    val a = getDouble(args.getOrNull(0))
                    val b = getDouble(args.getOrNull(1))
                    PyValue.FloatVal(Random.nextDouble(a, b))
                }
            }

            "time" -> {
                module.members["time"] = PyValue.BuiltinFuncVal("time") { _, _, _ -> PyValue.FloatVal(System.currentTimeMillis() / 1000.0) }
                module.members["monotonic"] = PyValue.BuiltinFuncVal("monotonic") { _, _, _ -> PyValue.FloatVal(System.nanoTime() / 1_000_000_000.0) }
                module.members["ctime"] = PyValue.BuiltinFuncVal("ctime") { _, _, _ ->
                    val sdf = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US)
                    PyValue.StringVal(sdf.format(Date()))
                }
                module.members["sleep"] = PyValue.BuiltinFuncVal("sleep") { args, _, _ ->
                    val sec = getDouble(args.getOrNull(0))
                    val ms = (sec * 1000).toLong()
                    if (ms > 0) {
                        delay(ms.coerceAtMost(30000))
                    }
                    PyValue.NoneVal
                }
            }

            "json" -> {
                module.members["dumps"] = PyValue.BuiltinFuncVal("dumps") { args, _, _ ->
                    val arg = args.getOrNull(0) ?: PyValue.NoneVal
                    val jsonStr = pyValueToJson(arg)
                    PyValue.StringVal(jsonStr)
                }
                module.members["loads"] = PyValue.BuiltinFuncVal("loads") { args, _, _ ->
                    val str = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "{}"
                    jsonToPyValue(str)
                }
            }

            "datetime" -> {
                val nowFunc = PyValue.BuiltinFuncVal("now") { _, _, _ ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val d = PyValue.InstanceVal(PyValue.ClassVal("datetime"))
                    val now = Date()
                    d.fields["year"] = PyValue.IntVal(SimpleDateFormat("yyyy", Locale.US).format(now).toLong())
                    d.fields["month"] = PyValue.IntVal(SimpleDateFormat("MM", Locale.US).format(now).toLong())
                    d.fields["day"] = PyValue.IntVal(SimpleDateFormat("dd", Locale.US).format(now).toLong())
                    d.fields["hour"] = PyValue.IntVal(SimpleDateFormat("HH", Locale.US).format(now).toLong())
                    d.fields["minute"] = PyValue.IntVal(SimpleDateFormat("mm", Locale.US).format(now).toLong())
                    d.fields["second"] = PyValue.IntVal(SimpleDateFormat("ss", Locale.US).format(now).toLong())
                    d.fields["strftime"] = PyValue.BuiltinFuncVal("strftime") { strArgs, _, _ ->
                        val fmt = (strArgs.getOrNull(0) as? PyValue.StringVal)?.value ?: "%Y-%m-%d %H:%M:%S"
                        val customSdf = SimpleDateFormat(fmt.replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd").replace("%H", "HH").replace("%M", "mm").replace("%S", "ss"), Locale.getDefault())
                        PyValue.StringVal(customSdf.format(now))
                    }
                    d
                }
                val dtClass = PyValue.ModuleVal("datetime")
                dtClass.members["now"] = nowFunc
                module.members["datetime"] = dtClass
            }

            "sys" -> {
                module.members["version"] = PyValue.StringVal("3.11.4 (PyRunner Engine Android Edition)")
                module.members["platform"] = PyValue.StringVal("android")
                module.members["argv"] = PyValue.ListVal(mutableListOf(PyValue.StringVal("main.py")))
                module.members["exit"] = PyValue.BuiltinFuncVal("exit") { _, _, _ ->
                    throw RuntimeException("SystemExit: 0")
                }
            }

            "os" -> {
                module.members["name"] = PyValue.StringVal("posix")
                module.members["getcwd"] = PyValue.BuiltinFuncVal("getcwd") { _, _, _ -> PyValue.StringVal("/storage/emulated/0/PythonRunner") }
                module.members["listdir"] = PyValue.BuiltinFuncVal("listdir") { _, _, c ->
                    PyValue.ListVal(c.virtualFiles.keys.map { PyValue.StringVal(it) }.toMutableList())
                }
                val pathMod = PyValue.ModuleVal("path")
                pathMod.members["exists"] = PyValue.BuiltinFuncVal("exists") { args, _, c ->
                    val f = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                    PyValue.BoolVal(c.virtualFiles.containsKey(f))
                }
                module.members["path"] = pathMod
            }

            "web", "flask", "server", "http_server" -> {
                // Flask / Web framework module
                module.members["serve_html"] = PyValue.BuiltinFuncVal("serve_html") { args, kwargs, c ->
                    val html = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "<h1>Python Web App</h1>"
                    val port = (kwargs["port"] as? PyValue.IntVal)?.value?.toInt()
                        ?: (args.getOrNull(1) as? PyValue.IntVal)?.value?.toInt()
                        ?: 8080
                    c.serverCallback?.serveHtml(html, port)
                    c.onStdout("🌐 Server online: http://127.0.0.1:$port\n")
                    PyValue.StringVal("http://127.0.0.1:$port")
                }

                module.members["route"] = PyValue.BuiltinFuncVal("route") { args, kwargs, c ->
                    val path = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "/"
                    val method = (kwargs["methods"] as? PyValue.StringVal)?.value ?: "GET"
                    val handler = args.getOrNull(1) as? PyValue.FunctionVal

                    if (handler != null) {
                        c.serverCallback?.registerRoute(path, method) { params, body ->
                            // Execute python handler function
                            val pyParams = PyValue.DictVal(params.mapValues { PyValue.StringVal(it.value) }.toMutableMap())
                            val pyBody = PyValue.StringVal(body)
                            // invoke handler
                            val result = executeHandler(handler, listOf(pyParams, pyBody), c)
                            result.toDisplayString()
                        }
                    }
                    PyValue.NoneVal
                }

                // Flask class constructor
                val flaskClass = PyValue.BuiltinFuncVal("Flask") { args, _, c ->
                    val app = PyValue.InstanceVal(PyValue.ClassVal("Flask"))
                    app.fields["route"] = PyValue.BuiltinFuncVal("route") { rArgs, rKwargs, _ ->
                        val path = (rArgs.getOrNull(0) as? PyValue.StringVal)?.value ?: "/"
                        val methodsList = (rKwargs["methods"] as? PyValue.ListVal)?.elements?.map { it.toDisplayString() }
                        val method = methodsList?.firstOrNull() ?: "GET"

                        // Return a decorator wrapper
                        PyValue.BuiltinFuncVal("decorator") { dArgs, _, _ ->
                            val fn = dArgs.getOrNull(0) as? PyValue.FunctionVal
                            if (fn != null) {
                                c.serverCallback?.registerRoute(path, method) { params, body ->
                                    val pyParams = PyValue.DictVal(params.mapValues { PyValue.StringVal(it.value) }.toMutableMap())
                                    val pyBody = PyValue.StringVal(body)
                                    val result = executeHandler(fn, listOf(pyParams, pyBody), c)
                                    result.toDisplayString()
                                }
                            }
                            fn ?: PyValue.NoneVal
                        }
                    }
                    app.fields["run"] = PyValue.BuiltinFuncVal("run") { runArgs, runKwargs, runCtx ->
                        val port = (runKwargs["port"] as? PyValue.IntVal)?.value?.toInt()
                            ?: (runArgs.getOrNull(0) as? PyValue.IntVal)?.value?.toInt()
                            ?: 8080
                        runCtx.serverCallback?.startServer(port)
                        runCtx.onStdout("🚀 Flask App läuft auf http://127.0.0.1:$port\n")
                        PyValue.StringVal("http://127.0.0.1:$port")
                    }
                    app
                }
                module.members["Flask"] = flaskClass
            }

            "socket" -> {
                module.members["AF_INET"] = PyValue.IntVal(2)
                module.members["SOCK_STREAM"] = PyValue.IntVal(1)
                module.members["SOCK_DGRAM"] = PyValue.IntVal(2)
                module.members["SOL_SOCKET"] = PyValue.IntVal(1)
                module.members["SO_REUSEADDR"] = PyValue.IntVal(2)

                val socketFactory = PyValue.BuiltinFuncVal("socket") { _, _, c ->
                    val sockObj = PyValue.InstanceVal(PyValue.ClassVal("socket"))
                    var socketId = ""
                    var timeoutMs = 5000

                    sockObj.fields["settimeout"] = PyValue.BuiltinFuncVal("settimeout") { tArgs, _, _ ->
                        val t = (tArgs.getOrNull(0) as? PyValue.FloatVal)?.value
                            ?: (tArgs.getOrNull(0) as? PyValue.IntVal)?.value?.toDouble() ?: 5.0
                        timeoutMs = (t * 1000).toInt()
                        PyValue.NoneVal
                    }

                    sockObj.fields["connect"] = PyValue.BuiltinFuncVal("connect") { cArgs, _, _ ->
                        val target = cArgs.getOrNull(0)
                        val host: String
                        val port: Int
                        if (target is PyValue.TupleVal || target is PyValue.ListVal) {
                            val items = if (target is PyValue.TupleVal) target.elements else (target as PyValue.ListVal).elements
                            host = (items.getOrNull(0) as? PyValue.StringVal)?.value ?: "127.0.0.1"
                            port = (items.getOrNull(1) as? PyValue.IntVal)?.value?.toInt() ?: 80
                        } else {
                            host = (target as? PyValue.StringVal)?.value ?: "127.0.0.1"
                            port = (cArgs.getOrNull(1) as? PyValue.IntVal)?.value?.toInt() ?: 80
                        }

                        val result = c.socketManager.openSocket(host, port, timeoutMs)
                        if (result.isSuccess) {
                            socketId = result.getOrNull() ?: ""
                        } else {
                            throw RuntimeException("ConnectionError: Failed to connect to $host:$port (${result.exceptionOrNull()?.message})")
                        }
                        PyValue.NoneVal
                    }

                    sockObj.fields["send"] = PyValue.BuiltinFuncVal("send") { sArgs, _, _ ->
                        if (socketId.isEmpty()) throw RuntimeException("SocketError: Socket is not connected")
                        val dataBytes = when (val item = sArgs.getOrNull(0)) {
                            is PyValue.BytesVal -> item.data
                            is PyValue.StringVal -> item.value.toByteArray(Charsets.UTF_8)
                            else -> ByteArray(0)
                        }
                        val result = c.socketManager.sendBytes(socketId, dataBytes)
                        if (result.isSuccess) {
                            PyValue.IntVal(result.getOrDefault(0).toLong())
                        } else {
                            throw RuntimeException("SocketError: Failed to send data (${result.exceptionOrNull()?.message})")
                        }
                    }

                    sockObj.fields["sendall"] = sockObj.fields["send"]!!

                    sockObj.fields["recv"] = PyValue.BuiltinFuncVal("recv") { rArgs, _, _ ->
                        if (socketId.isEmpty()) throw RuntimeException("SocketError: Socket is not connected")
                        val maxBytes = (rArgs.getOrNull(0) as? PyValue.IntVal)?.value?.toInt() ?: 1024
                        val result = c.socketManager.receiveBytes(socketId, maxBytes, timeoutMs)
                        if (result.isSuccess) {
                            PyValue.BytesVal(result.getOrDefault(ByteArray(0)))
                        } else {
                            throw RuntimeException("SocketError: Failed to receive data (${result.exceptionOrNull()?.message})")
                        }
                    }

                    sockObj.fields["close"] = PyValue.BuiltinFuncVal("close") { _, _, _ ->
                        if (socketId.isNotEmpty()) {
                            c.socketManager.closeSocket(socketId)
                            socketId = ""
                        }
                        PyValue.NoneVal
                    }

                    sockObj
                }
                module.members["socket"] = socketFactory
            }

            "struct" -> {
                module.members["pack"] = PyValue.BuiltinFuncVal("pack") { args, _, _ ->
                    val fmt = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                    val packArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()
                    val packed = PyStruct.pack(fmt, packArgs)
                    PyValue.BytesVal(packed)
                }

                module.members["unpack"] = PyValue.BuiltinFuncVal("unpack") { args, _, _ ->
                    val fmt = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                    val data = when (val item = args.getOrNull(1)) {
                        is PyValue.BytesVal -> item.data
                        is PyValue.StringVal -> item.value.toByteArray(Charsets.UTF_8)
                        else -> ByteArray(0)
                    }
                    val unpacked = PyStruct.unpack(fmt, data)
                    PyValue.TupleVal(unpacked)
                }

                module.members["calcsize"] = PyValue.BuiltinFuncVal("calcsize") { args, _, _ ->
                    val fmt = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                    PyValue.IntVal(PyStruct.calcsize(fmt).toLong())
                }
            }

            "binascii" -> {
                module.members["hexlify"] = PyValue.BuiltinFuncVal("hexlify") { args, _, _ ->
                    val data = when (val item = args.getOrNull(0)) {
                        is PyValue.BytesVal -> item.data
                        is PyValue.StringVal -> item.value.toByteArray(Charsets.UTF_8)
                        else -> ByteArray(0)
                    }
                    val hex = data.joinToString("") { "%02x".format(it) }
                    PyValue.BytesVal(hex.toByteArray(Charsets.US_ASCII))
                }
                module.members["b2a_hex"] = module.members["hexlify"]!!

                module.members["unhexlify"] = PyValue.BuiltinFuncVal("unhexlify") { args, _, _ ->
                    val hex = when (val item = args.getOrNull(0)) {
                        is PyValue.StringVal -> item.value
                        is PyValue.BytesVal -> String(item.data, Charsets.US_ASCII)
                        else -> ""
                    }.trim()
                    val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    PyValue.BytesVal(bytes)
                }
                module.members["a2b_hex"] = module.members["unhexlify"]!!

                module.members["crc32"] = PyValue.BuiltinFuncVal("crc32") { args, _, _ ->
                    val data = when (val item = args.getOrNull(0)) {
                        is PyValue.BytesVal -> item.data
                        is PyValue.StringVal -> item.value.toByteArray(Charsets.UTF_8)
                        else -> ByteArray(0)
                    }
                    val crc = java.util.zip.CRC32()
                    crc.update(data)
                    PyValue.IntVal(crc.value)
                }
            }

            "pysolarmanv5" -> {
                val solarmanClass = PyValue.BuiltinFuncVal("PySolarmanV5") { args, kwargs, c ->
                    val address = (kwargs["address"] as? PyValue.StringVal)?.value
                        ?: (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "192.168.1.100"
                    val serial = (kwargs["serial"] as? PyValue.IntVal)?.value?.toInt()
                        ?: (args.getOrNull(1) as? PyValue.IntVal)?.value?.toInt() ?: 123456789
                    val port = (kwargs["port"] as? PyValue.IntVal)?.value?.toInt()
                        ?: (args.getOrNull(2) as? PyValue.IntVal)?.value?.toInt() ?: 8899
                    val slaveId = (kwargs["mb_slave_id"] as? PyValue.IntVal)?.value?.toInt()
                        ?: (args.getOrNull(3) as? PyValue.IntVal)?.value?.toInt() ?: 1

                    val clientObj = PyValue.InstanceVal(PyValue.ClassVal("PySolarmanV5"))
                    clientObj.fields["address"] = PyValue.StringVal(address)
                    clientObj.fields["serial"] = PyValue.IntVal(serial.toLong())
                    clientObj.fields["port"] = PyValue.IntVal(port.toLong())
                    clientObj.fields["mb_slave_id"] = PyValue.IntVal(slaveId.toLong())

                    var socketId = ""

                    clientObj.fields["read_holding_registers"] = PyValue.BuiltinFuncVal("read_holding_registers") { rArgs, _, _ ->
                        val startAddr = (rArgs.getOrNull(0) as? PyValue.IntVal)?.value?.toInt() ?: 0
                        val quantity = (rArgs.getOrNull(1) as? PyValue.IntVal)?.value?.toInt() ?: 1

                        if (socketId.isEmpty()) {
                            val res = c.socketManager.openSocket(address, port, 5000)
                            if (res.isSuccess) {
                                socketId = res.getOrNull() ?: ""
                            } else {
                                throw RuntimeException("PySolarmanV5: Connection to $address:$port failed (${res.exceptionOrNull()?.message})")
                            }
                        }

                        // Build Modbus RTU Read Holding Registers frame (Func 0x03)
                        val pdu = ByteArray(6)
                        pdu[0] = slaveId.toByte()
                        pdu[1] = 0x03 // Func
                        pdu[2] = (startAddr shr 8).toByte()
                        pdu[3] = (startAddr and 0xFF).toByte()
                        pdu[4] = (quantity shr 8).toByte()
                        pdu[5] = (quantity and 0xFF).toByte()
                        val crc = PyStruct.crc16Modbus(pdu)
                        val modbusFrame = pdu + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())

                        // Solarman V5 frame encapsulation
                        val payloadLen = modbusFrame.size
                        val frameLen = 13 + payloadLen + 2
                        val v5Header = ByteArray(13)
                        v5Header[0] = 0xA5.toByte() // Start
                        v5Header[1] = (payloadLen and 0xFF).toByte()
                        v5Header[2] = (payloadLen shr 8).toByte()
                        v5Header[3] = 0x10.toByte() // Control Code
                        v5Header[4] = 0x45.toByte()
                        v5Header[5] = 0x00.toByte()
                        // Serial number (4 bytes little-endian)
                        v5Header[7] = (serial and 0xFF).toByte()
                        v5Header[8] = ((serial shr 8) and 0xFF).toByte()
                        v5Header[9] = ((serial shr 16) and 0xFF).toByte()
                        v5Header[10] = ((serial shr 24) and 0xFF).toByte()

                        val fullFrame = v5Header + modbusFrame + byteArrayOf(0x00.toByte(), 0x15.toByte())
                        c.socketManager.sendBytes(socketId, fullFrame)
                        val respBytes = c.socketManager.receiveBytes(socketId, 1024, 5000).getOrDefault(ByteArray(0))

                        // Parse response or generate structured values
                        val regList = mutableListOf<PyValue>()
                        if (respBytes.size >= 14) {
                            // Extract Modbus response registers
                            val dataStart = 14
                            for (q in 0 until quantity) {
                                val offset = dataStart + (q * 2)
                                if (offset + 1 < respBytes.size) {
                                    val high = respBytes[offset].toInt() and 0xFF
                                    val low = respBytes[offset + 1].toInt() and 0xFF
                                    val val16 = (high shl 8) or low
                                    regList.add(PyValue.IntVal(val16.toLong()))
                                } else {
                                    regList.add(PyValue.IntVal(0))
                                }
                            }
                        } else {
                            for (q in 0 until quantity) regList.add(PyValue.IntVal(0))
                        }
                        PyValue.ListVal(regList)
                    }

                    clientObj.fields["disconnect"] = PyValue.BuiltinFuncVal("disconnect") { _, _, _ ->
                        if (socketId.isNotEmpty()) {
                            c.socketManager.closeSocket(socketId)
                            socketId = ""
                        }
                        PyValue.NoneVal
                    }

                    clientObj
                }
                module.members["PySolarmanV5"] = solarmanClass
            }
        }
        return module
    }

    private suspend fun executeHandler(fn: PyValue.FunctionVal, args: List<PyValue>, ctx: PyContext): PyValue {
        val interpreter = PyInterpreter(ctx)
        return try {
            interpreter.callFunction(fn, args, emptyMap())
        } catch (e: Exception) {
            PyValue.StringVal("<h1>500 Internal Server Error</h1><pre>${e.message}</pre>")
        }
    }

    private fun getDouble(v: PyValue?): Double {
        return when (v) {
            is PyValue.IntVal -> v.value.toDouble()
            is PyValue.FloatVal -> v.value
            is PyValue.StringVal -> v.value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun comparePyValues(a: PyValue, b: PyValue): Int {
        return when {
            a is PyValue.IntVal && b is PyValue.IntVal -> a.value.compareTo(b.value)
            a is PyValue.FloatVal && b is PyValue.FloatVal -> a.value.compareTo(b.value)
            a is PyValue.IntVal && b is PyValue.FloatVal -> a.value.toDouble().compareTo(b.value)
            a is PyValue.FloatVal && b is PyValue.IntVal -> a.value.compareTo(b.value.toDouble())
            a is PyValue.StringVal && b is PyValue.StringVal -> a.value.compareTo(b.value)
            else -> a.toDisplayString().compareTo(b.toDisplayString())
        }
    }

    private fun pyValueToJson(v: PyValue): String {
        return when (v) {
            is PyValue.IntVal -> v.value.toString()
            is PyValue.FloatVal -> v.value.toString()
            is PyValue.StringVal -> JSONObject.quote(v.value)
            is PyValue.BoolVal -> if (v.value) "true" else "false"
            is PyValue.NoneVal -> "null"
            is PyValue.ListVal -> "[" + v.elements.joinToString(", ") { pyValueToJson(it) } + "]"
            is PyValue.TupleVal -> "[" + v.elements.joinToString(", ") { pyValueToJson(it) } + "]"
            is PyValue.DictVal -> "{" + v.entries.entries.joinToString(", ") { (k, valV) ->
                "${JSONObject.quote(k)}: ${pyValueToJson(valV)}"
            } + "}"
            else -> JSONObject.quote(v.toDisplayString())
        }
    }

    private fun jsonToPyValue(jsonStr: String): PyValue {
        val trimmed = jsonStr.trim()
        return try {
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val map = mutableMapOf<String, PyValue>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = jsonElementToPy(json.get(k))
                }
                PyValue.DictVal(map)
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val list = mutableListOf<PyValue>()
                for (i in 0 until arr.length()) {
                    list.add(jsonElementToPy(arr.get(i)))
                }
                PyValue.ListVal(list)
            } else {
                PyValue.StringVal(trimmed)
            }
        } catch (e: Exception) {
            PyValue.StringVal(trimmed)
        }
    }

    private fun jsonElementToPy(elem: Any?): PyValue {
        return when (elem) {
            null, JSONObject.NULL -> PyValue.NoneVal
            is Boolean -> PyValue.BoolVal(elem)
            is Int -> PyValue.IntVal(elem.toLong())
            is Long -> PyValue.IntVal(elem)
            is Double -> PyValue.FloatVal(elem)
            is String -> PyValue.StringVal(elem)
            is JSONObject -> {
                val map = mutableMapOf<String, PyValue>()
                val keys = elem.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = jsonElementToPy(elem.get(k))
                }
                PyValue.DictVal(map)
            }
            is JSONArray -> {
                val list = mutableListOf<PyValue>()
                for (i in 0 until elem.length()) {
                    list.add(jsonElementToPy(elem.get(i)))
                }
                PyValue.ListVal(list)
            }
            else -> PyValue.StringVal(elem.toString())
        }
    }
}
