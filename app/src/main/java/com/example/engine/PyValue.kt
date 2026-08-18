package com.example.engine

sealed class PyValue {
    abstract fun toDisplayString(): String
    abstract fun typeName(): String
    open fun isTruthy(): Boolean = true

    override fun toString(): String = toDisplayString()

    data class IntVal(val value: Long) : PyValue() {
        override fun toDisplayString(): String = value.toString()
        override fun typeName(): String = "int"
        override fun isTruthy(): Boolean = value != 0L
    }

    data class FloatVal(val value: Double) : PyValue() {
        override fun toDisplayString(): String {
            return if (value % 1.0 == 0.0) {
                String.format("%.1f", value)
            } else {
                value.toString()
            }
        }
        override fun typeName(): String = "float"
        override fun isTruthy(): Boolean = value != 0.0
    }

    data class StringVal(val value: String) : PyValue() {
        override fun toDisplayString(): String = value
        fun repr(): String = "'${value.replace("'", "\\'")}'"
        override fun typeName(): String = "str"
        override fun isTruthy(): Boolean = value.isNotEmpty()
    }

    data class BoolVal(val value: Boolean) : PyValue() {
        override fun toDisplayString(): String = if (value) "True" else "False"
        override fun typeName(): String = "bool"
        override fun isTruthy(): Boolean = value
    }

    data class ListVal(val elements: MutableList<PyValue> = mutableListOf()) : PyValue() {
        override fun toDisplayString(): String =
            "[" + elements.joinToString(", ") { if (it is StringVal) it.repr() else it.toDisplayString() } + "]"
        override fun typeName(): String = "list"
        override fun isTruthy(): Boolean = elements.isNotEmpty()
    }

    data class TupleVal(val elements: List<PyValue>) : PyValue() {
        override fun toDisplayString(): String {
            return if (elements.size == 1) {
                "(${if (elements[0] is StringVal) (elements[0] as StringVal).repr() else elements[0].toDisplayString()},)"
            } else {
                "(" + elements.joinToString(", ") { if (it is StringVal) it.repr() else it.toDisplayString() } + ")"
            }
        }
        override fun typeName(): String = "tuple"
        override fun isTruthy(): Boolean = elements.isNotEmpty()
    }

    data class DictVal(val entries: MutableMap<String, PyValue> = mutableMapOf()) : PyValue() {
        override fun toDisplayString(): String {
            val items = entries.entries.joinToString(", ") { (k, v) ->
                "'$k': ${if (v is StringVal) v.repr() else v.toDisplayString()}"
            }
            return "{$items}"
        }
        override fun typeName(): String = "dict"
        override fun isTruthy(): Boolean = entries.isNotEmpty()
    }

    data class SetVal(val elements: MutableSet<PyValue> = mutableSetOf()) : PyValue() {
        override fun toDisplayString(): String {
            return if (elements.isEmpty()) "set()"
            else "{" + elements.joinToString(", ") { it.toDisplayString() } + "}"
        }
        override fun typeName(): String = "set"
        override fun isTruthy(): Boolean = elements.isNotEmpty()
    }

    object NoneVal : PyValue() {
        override fun toDisplayString(): String = "None"
        override fun typeName(): String = "NoneType"
        override fun isTruthy(): Boolean = false
    }

    data class FunctionVal(
        val name: String,
        val params: List<String>,
        val defaultParams: Map<String, PyValue> = emptyMap(),
        val vararg: String? = null,
        val body: List<Statement>,
        val closureScope: MutableMap<String, PyValue>
    ) : PyValue() {
        override fun toDisplayString(): String = "<function $name>"
        override fun typeName(): String = "function"
    }

    data class BuiltinFuncVal(
        val name: String,
        val handler: suspend (args: List<PyValue>, kwargs: Map<String, PyValue>, ctx: PyContext) -> PyValue
    ) : PyValue() {
        override fun toDisplayString(): String = "<built-in function $name>"
        override fun typeName(): String = "builtin_function"
    }

    data class ModuleVal(
        val name: String,
        val members: MutableMap<String, PyValue> = mutableMapOf()
    ) : PyValue() {
        override fun toDisplayString(): String = "<module '$name'>"
        override fun typeName(): String = "module"
    }

    data class ClassVal(
        val name: String,
        val methods: MutableMap<String, FunctionVal> = mutableMapOf(),
        val baseClass: ClassVal? = null
    ) : PyValue() {
        override fun toDisplayString(): String = "<class '$name'>"
        override fun typeName(): String = "type"
    }

    data class InstanceVal(
        val pyClass: ClassVal,
        val fields: MutableMap<String, PyValue> = mutableMapOf()
    ) : PyValue() {
        override fun toDisplayString(): String = "<${pyClass.name} object>"
        override fun typeName(): String = pyClass.name
    }
}

fun Long.toPy(): PyValue = PyValue.IntVal(this)
fun Int.toPy(): PyValue = PyValue.IntVal(this.toLong())
fun Double.toPy(): PyValue = PyValue.FloatVal(this)
fun String.toPy(): PyValue = PyValue.StringVal(this)
fun Boolean.toPy(): PyValue = PyValue.BoolVal(this)
