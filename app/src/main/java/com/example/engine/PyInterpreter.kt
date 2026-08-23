package com.example.engine

import kotlinx.coroutines.yield
import kotlin.math.pow

sealed class FlowControl {
    object None : FlowControl()
    data class Return(val value: PyValue) : FlowControl()
    object Break : FlowControl()
    object Continue : FlowControl()
}

class PyInterpreter(val ctx: PyContext) {

    init {
        PyStdLib.populateBuiltins(ctx)
    }

    suspend fun execute(statements: List<Statement>): PyValue {
        var lastVal: PyValue = PyValue.NoneVal
        for (stmt in statements) {
            checkCancelled()
            yield()
            val flow = executeStatement(stmt)
            when (flow) {
                is FlowControl.Return -> return flow.value
                is FlowControl.Break -> break
                is FlowControl.Continue -> continue
                is FlowControl.None -> Unit
            }
        }
        return lastVal
    }

    private fun checkCancelled() {
        if (ctx.isCancelled) {
            throw RuntimeException("KeyboardInterrupt: Execution stopped by user")
        }
    }

    suspend fun executeStatement(stmt: Statement): FlowControl {
        checkCancelled()
        yield()

        return when (stmt) {
            is Statement.Assign -> {
                val value = evaluate(stmt.value)
                for (target in stmt.targets) {
                    assignToTarget(target, value)
                }
                FlowControl.None
            }

            is Statement.AugmentedAssign -> {
                val targetVal = evaluate(stmt.target)
                val rightVal = evaluate(stmt.value)
                val res = evalBinaryOp(targetVal, stmt.op, rightVal)
                assignToTarget(stmt.target, res)
                FlowControl.None
            }

            is Statement.ExprStmt -> {
                evaluate(stmt.expr)
                FlowControl.None
            }

            is Statement.IfStmt -> {
                var branchMatched = false
                for ((cond, body) in stmt.branches) {
                    val condVal = evaluate(cond)
                    if (condVal.isTruthy()) {
                        branchMatched = true
                        val flow = executeBlock(body)
                        if (flow !is FlowControl.None) return flow
                        break
                    }
                }
                if (!branchMatched && stmt.elseBranch != null) {
                    val flow = executeBlock(stmt.elseBranch)
                    if (flow !is FlowControl.None) return flow
                }
                FlowControl.None
            }

            is Statement.WhileStmt -> {
                var wasBroken = false
                while (evaluate(stmt.condition).isTruthy()) {
                    checkCancelled()
                    yield()
                    val flow = executeBlock(stmt.body)
                    if (flow is FlowControl.Break) {
                        wasBroken = true
                        break
                    }
                    if (flow is FlowControl.Return) return flow
                }
                if (!wasBroken && stmt.elseBranch != null) {
                    val flow = executeBlock(stmt.elseBranch)
                    if (flow !is FlowControl.None) return flow
                }
                FlowControl.None
            }

            is Statement.ForStmt -> {
                val iterVal = evaluate(stmt.iterable)
                val items = getIterableElements(iterVal)
                var wasBroken = false

                for (item in items) {
                    checkCancelled()
                    yield()
                    assignVariables(stmt.variables, item)
                    val flow = executeBlock(stmt.body)
                    if (flow is FlowControl.Break) {
                        wasBroken = true
                        break
                    }
                    if (flow is FlowControl.Return) return flow
                }
                if (!wasBroken && stmt.elseBranch != null) {
                    val flow = executeBlock(stmt.elseBranch)
                    if (flow !is FlowControl.None) return flow
                }
                FlowControl.None
            }

            is Statement.DefStmt -> {
                val evaluatedDefaults = mutableMapOf<String, PyValue>()
                for ((k, v) in stmt.defaultParams) {
                    evaluatedDefaults[k] = evaluate(v)
                }
                val fn = PyValue.FunctionVal(
                    name = stmt.name,
                    params = stmt.params,
                    defaultParams = evaluatedDefaults,
                    vararg = stmt.vararg,
                    body = stmt.body,
                    closureScope = ctx.scopes.lastOrNull()?.toMutableMap() ?: ctx.globalScope
                )
                ctx.setVariable(stmt.name, fn)
                FlowControl.None
            }

            is Statement.ClassStmt -> {
                val classVal = PyValue.ClassVal(stmt.name)
                // Execute class body in isolated scope to gather methods
                val classScope = mutableMapOf<String, PyValue>()
                ctx.pushScope(classScope)
                for (s in stmt.body) {
                    if (s is Statement.DefStmt) {
                        val fn = PyValue.FunctionVal(
                            name = s.name,
                            params = s.params,
                            body = s.body,
                            closureScope = classScope
                        )
                        classVal.methods[s.name] = fn
                    } else {
                        executeStatement(s)
                    }
                }
                ctx.popScope()
                ctx.setVariable(stmt.name, classVal)
                FlowControl.None
            }

            is Statement.ReturnStmt -> {
                val value = if (stmt.value != null) evaluate(stmt.value) else PyValue.NoneVal
                FlowControl.Return(value)
            }

            is Statement.BreakStmt -> FlowControl.Break
            is Statement.ContinueStmt -> FlowControl.Continue
            is Statement.PassStmt -> FlowControl.None

            is Statement.ImportStmt -> {
                val mod = PyStdLib.loadModule(stmt.moduleName, ctx)
                if (stmt.specificImports.isNotEmpty()) {
                    for ((symbol, alias) in stmt.specificImports) {
                        val value = mod.members[symbol]
                            ?: throw RuntimeException("ImportError: cannot import name '$symbol' from '${stmt.moduleName}'")
                        ctx.setVariable(alias ?: symbol, value)
                    }
                } else {
                    ctx.setVariable(stmt.alias ?: stmt.moduleName, mod)
                }
                FlowControl.None
            }

            is Statement.TryExceptStmt -> {
                try {
                    val flow = executeBlock(stmt.tryBody)
                    if (flow !is FlowControl.None) return flow
                } catch (e: Exception) {
                    var handled = false
                    for (clause in stmt.exceptClauses) {
                        val exName = clause.exceptionType ?: "Exception"
                        if (exName == "Exception" || exName == "BaseException" || e.message?.contains(exName) == true) {
                            if (clause.exceptionVar != null) {
                                ctx.setVariable(clause.exceptionVar, PyValue.StringVal(e.message ?: "Error"))
                            }
                            handled = true
                            val flow = executeBlock(clause.body)
                            if (flow !is FlowControl.None) return flow
                            break
                        }
                    }
                    if (!handled) {
                        throw e
                    }
                } finally {
                    if (stmt.finallyBody != null) {
                        executeBlock(stmt.finallyBody)
                    }
                }
                FlowControl.None
            }

            is Statement.RaiseStmt -> {
                val msg = if (stmt.exceptionExpr != null) evaluate(stmt.exceptionExpr).toDisplayString() else "Exception"
                throw RuntimeException("Exception: $msg")
            }

            is Statement.AssertStmt -> {
                val cond = evaluate(stmt.condition)
                if (!cond.isTruthy()) {
                    val msg = if (stmt.message != null) evaluate(stmt.message).toDisplayString() else "AssertionError"
                    throw RuntimeException("AssertionError: $msg")
                }
                FlowControl.None
            }
        }
    }

    private suspend fun executeBlock(body: List<Statement>): FlowControl {
        for (s in body) {
            checkCancelled()
            yield()
            val flow = executeStatement(s)
            if (flow !is FlowControl.None) return flow
        }
        return FlowControl.None
    }

    private suspend fun assignToTarget(target: Expression, value: PyValue) {
        when (target) {
            is Expression.Identifier -> {
                ctx.setVariable(target.name, value)
            }
            is Expression.IndexAccess -> {
                val obj = evaluate(target.target)
                val idx = evaluate(target.index)
                when (obj) {
                    is PyValue.ListVal -> {
                        val intIdx = (idx as? PyValue.IntVal)?.value?.toInt()
                            ?: throw RuntimeException("TypeError: list indices must be integers")
                        val actualIdx = if (intIdx < 0) obj.elements.size + intIdx else intIdx
                        if (actualIdx in obj.elements.indices) {
                            obj.elements[actualIdx] = value
                        } else {
                            throw RuntimeException("IndexError: list assignment index out of range")
                        }
                    }
                    is PyValue.DictVal -> {
                        obj.entries[idx.toDisplayString()] = value
                    }
                    else -> throw RuntimeException("TypeError: '${obj.typeName()}' object does not support item assignment")
                }
            }
            is Expression.AttributeAccess -> {
                val obj = evaluate(target.target)
                if (obj is PyValue.InstanceVal) {
                    obj.fields[target.attrName] = value
                } else if (obj is PyValue.ModuleVal) {
                    obj.members[target.attrName] = value
                } else {
                    throw RuntimeException("AttributeError: '${obj.typeName()}' object has no attribute '${target.attrName}'")
                }
            }
            is Expression.TupleLiteral -> {
                val elements = getIterableElements(value)
                if (elements.size != target.items.size) {
                    throw RuntimeException("ValueError: too many values to unpack (expected ${target.items.size})")
                }
                for (i in target.items.indices) {
                    assignToTarget(target.items[i], elements[i])
                }
            }
            is Expression.ListLiteral -> {
                val elements = getIterableElements(value)
                if (elements.size != target.items.size) {
                    throw RuntimeException("ValueError: too many values to unpack (expected ${target.items.size})")
                }
                for (i in target.items.indices) {
                    assignToTarget(target.items[i], elements[i])
                }
            }
            else -> throw RuntimeException("SyntaxError: cannot assign to expression")
        }
    }

    suspend fun evaluate(expr: Expression): PyValue {
        checkCancelled()

        return when (expr) {
            is Expression.Literal -> expr.value

            is Expression.Identifier -> ctx.getVariable(expr.name)

            is Expression.UnaryOp -> {
                val v = evaluate(expr.expr)
                when (expr.op) {
                    UnaryOperator.NOT -> PyValue.BoolVal(!v.isTruthy())
                    UnaryOperator.NEGATE -> when (v) {
                        is PyValue.IntVal -> PyValue.IntVal(-v.value)
                        is PyValue.FloatVal -> PyValue.FloatVal(-v.value)
                        else -> throw RuntimeException("TypeError: bad operand type for unary -: '${v.typeName()}'")
                    }
                    UnaryOperator.POSITIVE -> v
                    UnaryOperator.BIT_NOT -> when (v) {
                        is PyValue.IntVal -> PyValue.IntVal(v.value.inv())
                        else -> throw RuntimeException("TypeError: bad operand type for unary ~: '${v.typeName()}'")
                    }
                }
            }

            is Expression.BinaryOp -> {
                // Short-circuit logical ops
                if (expr.op == BinaryOperator.AND) {
                    val left = evaluate(expr.left)
                    return if (!left.isTruthy()) left else evaluate(expr.right)
                }
                if (expr.op == BinaryOperator.OR) {
                    val left = evaluate(expr.left)
                    return if (left.isTruthy()) left else evaluate(expr.right)
                }

                val left = evaluate(expr.left)
                val right = evaluate(expr.right)
                evalBinaryOp(left, expr.op, right)
            }

            is Expression.FunctionCall -> {
                val target = evaluate(expr.target)
                val args = expr.args.map { evaluate(it) }
                val kwargs = expr.kwargs.mapValues { evaluate(it.value) }
                callTarget(target, args, kwargs)
            }

            is Expression.IndexAccess -> {
                val target = evaluate(expr.target)
                val idx = evaluate(expr.index)
                evalIndexAccess(target, idx)
            }

            is Expression.SliceAccess -> {
                val target = evaluate(expr.target)
                val start = expr.start?.let { evaluate(it) }
                val stop = expr.stop?.let { evaluate(it) }
                val step = expr.step?.let { evaluate(it) }
                evalSliceAccess(target, start, stop, step)
            }

            is Expression.AttributeAccess -> {
                val target = evaluate(expr.target)
                evalAttributeAccess(target, expr.attrName)
            }

            is Expression.ListLiteral -> {
                PyValue.ListVal(expr.items.map { evaluate(it) }.toMutableList())
            }

            is Expression.TupleLiteral -> {
                PyValue.TupleVal(expr.items.map { evaluate(it) })
            }

            is Expression.DictLiteral -> {
                val map = mutableMapOf<String, PyValue>()
                for ((kExpr, vExpr) in expr.entries) {
                    val k = evaluate(kExpr)
                    val v = evaluate(vExpr)
                    map[k.toDisplayString()] = v
                }
                PyValue.DictVal(map)
            }

            is Expression.SetLiteral -> {
                PyValue.SetVal(expr.items.map { evaluate(it) }.toMutableSet())
            }

            is Expression.Ternary -> {
                val cond = evaluate(expr.condition)
                if (cond.isTruthy()) evaluate(expr.trueExpr) else evaluate(expr.falseExpr)
            }

            is Expression.Lambda -> {
                PyValue.FunctionVal(
                    name = "<lambda>",
                    params = expr.params,
                    body = listOf(Statement.ReturnStmt(expr.body)),
                    closureScope = ctx.scopes.lastOrNull()?.toMutableMap() ?: ctx.globalScope
                )
            }

            is Expression.FormattedString -> {
                val sb = StringBuilder()
                for (part in expr.parts) {
                    sb.append(evaluate(part).toDisplayString())
                }
                PyValue.StringVal(sb.toString())
            }

            is Expression.ListComprehension -> {
                val iterVal = evaluate(expr.iterable)
                val elements = getIterableElements(iterVal)
                val result = mutableListOf<PyValue>()

                ctx.pushScope()
                for (item in elements) {
                    assignVariables(expr.variables, item)
                    val keep = expr.condition == null || evaluate(expr.condition).isTruthy()
                    if (keep) {
                        result.add(evaluate(expr.expr))
                    }
                }
                ctx.popScope()
                PyValue.ListVal(result)
            }

            is Expression.DictComprehension -> {
                val iterVal = evaluate(expr.iterable)
                val elements = getIterableElements(iterVal)
                val result = mutableMapOf<String, PyValue>()

                ctx.pushScope()
                for (item in elements) {
                    assignVariables(expr.variables, item)
                    val keep = expr.condition == null || evaluate(expr.condition).isTruthy()
                    if (keep) {
                        val k = evaluate(expr.keyExpr)
                        val v = evaluate(expr.valExpr)
                        result[k.toDisplayString()] = v
                    }
                }
                ctx.popScope()
                PyValue.DictVal(result)
            }
        }
    }

    private fun assignVariables(variables: List<String>, item: PyValue) {
        if (variables.size == 1) {
            ctx.setVariable(variables[0], item)
        } else {
            val unpacked = when (item) {
                is PyValue.ListVal -> item.elements
                is PyValue.TupleVal -> item.elements
                else -> listOf(item)
            }
            for (i in variables.indices) {
                if (i < unpacked.size) {
                    ctx.setVariable(variables[i], unpacked[i])
                } else {
                    ctx.setVariable(variables[i], PyValue.NoneVal)
                }
            }
        }
    }

    private fun evalBinaryOp(left: PyValue, op: BinaryOperator, right: PyValue): PyValue {
        return when (op) {
            BinaryOperator.ADD -> when {
                left is PyValue.IntVal && right is PyValue.IntVal -> PyValue.IntVal(left.value + right.value)
                left is PyValue.FloatVal || right is PyValue.FloatVal -> {
                    val a = (left as? PyValue.FloatVal)?.value ?: (left as PyValue.IntVal).value.toDouble()
                    val b = (right as? PyValue.FloatVal)?.value ?: (right as PyValue.IntVal).value.toDouble()
                    PyValue.FloatVal(a + b)
                }
                left is PyValue.StringVal && right is PyValue.StringVal -> PyValue.StringVal(left.value + right.value)
                left is PyValue.BytesVal && right is PyValue.BytesVal -> {
                    val combined = ByteArray(left.data.size + right.data.size)
                    System.arraycopy(left.data, 0, combined, 0, left.data.size)
                    System.arraycopy(right.data, 0, combined, left.data.size, right.data.size)
                    PyValue.BytesVal(combined)
                }
                left is PyValue.ListVal && right is PyValue.ListVal -> {
                    val combined = mutableListOf<PyValue>()
                    combined.addAll(left.elements)
                    combined.addAll(right.elements)
                    PyValue.ListVal(combined)
                }
                else -> throw RuntimeException("TypeError: unsupported operand type(s) for +: '${left.typeName()}' and '${right.typeName()}'")
            }

            BinaryOperator.SUBTRACT -> when {
                left is PyValue.IntVal && right is PyValue.IntVal -> PyValue.IntVal(left.value - right.value)
                left is PyValue.FloatVal || right is PyValue.FloatVal -> {
                    val a = (left as? PyValue.FloatVal)?.value ?: (left as PyValue.IntVal).value.toDouble()
                    val b = (right as? PyValue.FloatVal)?.value ?: (right as PyValue.IntVal).value.toDouble()
                    PyValue.FloatVal(a - b)
                }
                else -> throw RuntimeException("TypeError: unsupported operand type(s) for -: '${left.typeName()}' and '${right.typeName()}'")
            }

            BinaryOperator.MULTIPLY -> when {
                left is PyValue.IntVal && right is PyValue.IntVal -> PyValue.IntVal(left.value * right.value)
                left is PyValue.FloatVal || right is PyValue.FloatVal -> {
                    val a = (left as? PyValue.FloatVal)?.value ?: (left as PyValue.IntVal).value.toDouble()
                    val b = (right as? PyValue.FloatVal)?.value ?: (right as PyValue.IntVal).value.toDouble()
                    PyValue.FloatVal(a * b)
                }
                left is PyValue.StringVal && right is PyValue.IntVal -> PyValue.StringVal(left.value.repeat(right.value.toInt().coerceAtLeast(0)))
                left is PyValue.ListVal && right is PyValue.IntVal -> {
                    val repeated = mutableListOf<PyValue>()
                    for (i in 0 until right.value.toInt().coerceAtLeast(0)) {
                        repeated.addAll(left.elements)
                    }
                    PyValue.ListVal(repeated)
                }
                else -> throw RuntimeException("TypeError: unsupported operand type(s) for *: '${left.typeName()}' and '${right.typeName()}'")
            }

            BinaryOperator.DIVIDE -> {
                val a = (left as? PyValue.FloatVal)?.value ?: (left as? PyValue.IntVal)?.value?.toDouble()
                    ?: throw RuntimeException("TypeError: unsupported operand type(s) for /: '${left.typeName()}'")
                val b = (right as? PyValue.FloatVal)?.value ?: (right as? PyValue.IntVal)?.value?.toDouble()
                    ?: throw RuntimeException("TypeError: unsupported operand type(s) for /: '${right.typeName()}'")
                if (b == 0.0) throw RuntimeException("ZeroDivisionError: division by zero")
                PyValue.FloatVal(a / b)
            }

            BinaryOperator.FLOOR_DIVIDE -> {
                val a = (left as? PyValue.IntVal)?.value ?: (left as? PyValue.FloatVal)?.value?.toLong()
                    ?: throw RuntimeException("TypeError: unsupported operand type(s) for //: '${left.typeName()}'")
                val b = (right as? PyValue.IntVal)?.value ?: (right as? PyValue.FloatVal)?.value?.toLong()
                    ?: throw RuntimeException("TypeError: unsupported operand type(s) for //: '${right.typeName()}'")
                if (b == 0L) throw RuntimeException("ZeroDivisionError: integer division or modulo by zero")
                PyValue.IntVal(a / b)
            }

            BinaryOperator.MODULO -> {
                if (left is PyValue.StringVal) {
                    // String formatting %
                    return formatStringModulo(left.value, right)
                }
                val a = (left as? PyValue.IntVal)?.value ?: (left as? PyValue.FloatVal)?.value?.toLong() ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: (right as? PyValue.FloatVal)?.value?.toLong() ?: 1L
                if (b == 0L) throw RuntimeException("ZeroDivisionError: integer division or modulo by zero")
                PyValue.IntVal(a % b)
            }

            BinaryOperator.POWER -> {
                val a = (left as? PyValue.FloatVal)?.value ?: (left as? PyValue.IntVal)?.value?.toDouble() ?: 0.0
                val b = (right as? PyValue.FloatVal)?.value ?: (right as? PyValue.IntVal)?.value?.toDouble() ?: 0.0
                if (left is PyValue.IntVal && right is PyValue.IntVal && right.value >= 0) {
                    PyValue.IntVal(a.pow(b).toLong())
                } else {
                    PyValue.FloatVal(a.pow(b))
                }
            }

            BinaryOperator.EQUAL -> PyValue.BoolVal(pyValuesEqual(left, right))
            BinaryOperator.NOT_EQUAL -> PyValue.BoolVal(!pyValuesEqual(left, right))
            BinaryOperator.LESS_THAN -> PyValue.BoolVal(pyValuesCompare(left, right) < 0)
            BinaryOperator.LESS_EQUAL -> PyValue.BoolVal(pyValuesCompare(left, right) <= 0)
            BinaryOperator.GREATER_THAN -> PyValue.BoolVal(pyValuesCompare(left, right) > 0)
            BinaryOperator.GREATER_EQUAL -> PyValue.BoolVal(pyValuesCompare(left, right) >= 0)

            BinaryOperator.IN -> PyValue.BoolVal(pyValueIn(left, right))
            BinaryOperator.NOT_IN -> PyValue.BoolVal(!pyValueIn(left, right))
            BinaryOperator.IS -> PyValue.BoolVal(left === right || (left is PyValue.NoneVal && right is PyValue.NoneVal))
            BinaryOperator.IS_NOT -> PyValue.BoolVal(left !== right && !(left is PyValue.NoneVal && right is PyValue.NoneVal))

            BinaryOperator.BIT_AND -> {
                val a = (left as? PyValue.IntVal)?.value ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: 0L
                PyValue.IntVal(a and b)
            }
            BinaryOperator.BIT_OR -> {
                val a = (left as? PyValue.IntVal)?.value ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: 0L
                PyValue.IntVal(a or b)
            }
            BinaryOperator.BIT_XOR -> {
                val a = (left as? PyValue.IntVal)?.value ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: 0L
                PyValue.IntVal(a xor b)
            }
            BinaryOperator.BIT_LSHIFT -> {
                val a = (left as? PyValue.IntVal)?.value ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: 0L
                PyValue.IntVal(a shl b.toInt())
            }
            BinaryOperator.BIT_RSHIFT -> {
                val a = (left as? PyValue.IntVal)?.value ?: 0L
                val b = (right as? PyValue.IntVal)?.value ?: 0L
                PyValue.IntVal(a shr b.toInt())
            }
            else -> PyValue.NoneVal
        }
    }

    private fun formatStringModulo(fmt: String, arg: PyValue): PyValue {
        val argsList = if (arg is PyValue.TupleVal) arg.elements else listOf(arg)
        var res = fmt
        for (a in argsList) {
            val idxS = res.indexOf("%s")
            val idxD = res.indexOf("%d")
            val idxF = res.indexOf("%f")

            val minIdx = listOf(idxS, idxD, idxF).filter { it >= 0 }.minOrNull() ?: break
            if (minIdx == idxS) {
                res = res.replaceFirst("%s", a.toDisplayString())
            } else if (minIdx == idxD) {
                val num = (a as? PyValue.IntVal)?.value ?: (a as? PyValue.FloatVal)?.value?.toLong() ?: 0L
                res = res.replaceFirst("%d", num.toString())
            } else if (minIdx == idxF) {
                val num = (a as? PyValue.FloatVal)?.value ?: (a as? PyValue.IntVal)?.value?.toDouble() ?: 0.0
                res = res.replaceFirst("%f", String.format("%.6f", num))
            }
        }
        return PyValue.StringVal(res)
    }

    private fun pyValuesEqual(a: PyValue, b: PyValue): Boolean {
        if (a is PyValue.IntVal && b is PyValue.IntVal) return a.value == b.value
        if (a is PyValue.FloatVal && b is PyValue.FloatVal) return a.value == b.value
        if (a is PyValue.IntVal && b is PyValue.FloatVal) return a.value.toDouble() == b.value
        if (a is PyValue.FloatVal && b is PyValue.IntVal) return a.value == b.value.toDouble()
        if (a is PyValue.StringVal && b is PyValue.StringVal) return a.value == b.value
        if (a is PyValue.BoolVal && b is PyValue.BoolVal) return a.value == b.value
        if (a is PyValue.NoneVal && b is PyValue.NoneVal) return true
        if (a is PyValue.ListVal && b is PyValue.ListVal) {
            if (a.elements.size != b.elements.size) return false
            return a.elements.indices.all { pyValuesEqual(a.elements[it], b.elements[it]) }
        }
        return false
    }

    private fun pyValuesCompare(a: PyValue, b: PyValue): Int {
        if (a is PyValue.IntVal && b is PyValue.IntVal) return a.value.compareTo(b.value)
        if (a is PyValue.FloatVal && b is PyValue.FloatVal) return a.value.compareTo(b.value)
        if (a is PyValue.IntVal && b is PyValue.FloatVal) return a.value.toDouble().compareTo(b.value)
        if (a is PyValue.FloatVal && b is PyValue.IntVal) return a.value.compareTo(b.value.toDouble())
        if (a is PyValue.StringVal && b is PyValue.StringVal) return a.value.compareTo(b.value)
        return a.toDisplayString().compareTo(b.toDisplayString())
    }

    private fun pyValueIn(needle: PyValue, haystack: PyValue): Boolean {
        return when (haystack) {
            is PyValue.ListVal -> haystack.elements.any { pyValuesEqual(it, needle) }
            is PyValue.TupleVal -> haystack.elements.any { pyValuesEqual(it, needle) }
            is PyValue.SetVal -> haystack.elements.any { pyValuesEqual(it, needle) }
            is PyValue.StringVal -> haystack.value.contains(needle.toDisplayString())
            is PyValue.DictVal -> haystack.entries.containsKey(needle.toDisplayString())
            else -> throw RuntimeException("TypeError: argument of type '${haystack.typeName()}' is not iterable")
        }
    }

    private fun getIterableElements(v: PyValue): List<PyValue> {
        return when (v) {
            is PyValue.ListVal -> v.elements
            is PyValue.TupleVal -> v.elements
            is PyValue.SetVal -> v.elements.toList()
            is PyValue.StringVal -> v.value.map { PyValue.StringVal(it.toString()) }
            is PyValue.DictVal -> v.entries.keys.map { PyValue.StringVal(it) }
            else -> throw RuntimeException("TypeError: '${v.typeName()}' object is not iterable")
        }
    }

    private fun evalIndexAccess(target: PyValue, idx: PyValue): PyValue {
        return when (target) {
            is PyValue.ListVal -> {
                val i = (idx as? PyValue.IntVal)?.value?.toInt()
                    ?: throw RuntimeException("TypeError: list indices must be integers")
                val actualIdx = if (i < 0) target.elements.size + i else i
                if (actualIdx in target.elements.indices) target.elements[actualIdx]
                else throw RuntimeException("IndexError: list index out of range")
            }
            is PyValue.TupleVal -> {
                val i = (idx as? PyValue.IntVal)?.value?.toInt()
                    ?: throw RuntimeException("TypeError: tuple indices must be integers")
                val actualIdx = if (i < 0) target.elements.size + i else i
                if (actualIdx in target.elements.indices) target.elements[actualIdx]
                else throw RuntimeException("IndexError: tuple index out of range")
            }
            is PyValue.StringVal -> {
                val i = (idx as? PyValue.IntVal)?.value?.toInt()
                    ?: throw RuntimeException("TypeError: string indices must be integers")
                val actualIdx = if (i < 0) target.value.length + i else i
                if (actualIdx in target.value.indices) PyValue.StringVal(target.value[actualIdx].toString())
                else throw RuntimeException("IndexError: string index out of range")
            }
            is PyValue.BytesVal -> {
                val i = (idx as? PyValue.IntVal)?.value?.toInt()
                    ?: throw RuntimeException("TypeError: bytes indices must be integers")
                val actualIdx = if (i < 0) target.data.size + i else i
                if (actualIdx in target.data.indices) PyValue.IntVal((target.data[actualIdx].toInt() and 0xFF).toLong())
                else throw RuntimeException("IndexError: index out of range")
            }
            is PyValue.DictVal -> {
                val key = idx.toDisplayString()
                target.entries[key] ?: throw RuntimeException("KeyError: '$key'")
            }
            else -> throw RuntimeException("TypeError: '${target.typeName()}' object is not subscriptable")
        }
    }

    private fun evalSliceAccess(target: PyValue, startVal: PyValue?, stopVal: PyValue?, stepVal: PyValue?): PyValue {
        val step = (stepVal as? PyValue.IntVal)?.value?.toInt() ?: 1
        when (target) {
            is PyValue.StringVal -> {
                val s = target.value
                val start = (startVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) 0 else s.length - 1
                val stop = (stopVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) s.length else -1
                val sb = StringBuilder()
                if (step > 0) {
                    var i = if (start < 0) (s.length + start).coerceAtLeast(0) else start
                    val actualStop = if (stop < 0) (s.length + stop).coerceAtLeast(0) else stop.coerceAtMost(s.length)
                    while (i < actualStop && i in s.indices) {
                        sb.append(s[i])
                        i += step
                    }
                } else {
                    var i = if (start < 0) (s.length + start) else start.coerceAtMost(s.length - 1)
                    val actualStop = if (stop < 0) (s.length + stop) else stop
                    while (i > actualStop && i in s.indices) {
                        sb.append(s[i])
                        i += step
                    }
                }
                return PyValue.StringVal(sb.toString())
            }
            is PyValue.BytesVal -> {
                val bytes = target.data
                val start = (startVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) 0 else bytes.size - 1
                val stop = (stopVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) bytes.size else -1
                val result = mutableListOf<Byte>()
                if (step > 0) {
                    var i = if (start < 0) (bytes.size + start).coerceAtLeast(0) else start
                    val actualStop = if (stop < 0) (bytes.size + stop).coerceAtLeast(0) else stop.coerceAtMost(bytes.size)
                    while (i < actualStop && i in bytes.indices) {
                        result.add(bytes[i])
                        i += step
                    }
                } else {
                    var i = if (start < 0) (bytes.size + start) else start.coerceAtMost(bytes.size - 1)
                    val actualStop = if (stop < 0) (bytes.size + stop) else stop
                    while (i > actualStop && i in bytes.indices) {
                        result.add(bytes[i])
                        i += step
                    }
                }
                return PyValue.BytesVal(result.toByteArray())
            }
            is PyValue.ListVal -> {
                val list = target.elements
                val start = (startVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) 0 else list.size - 1
                val stop = (stopVal as? PyValue.IntVal)?.value?.toInt() ?: if (step > 0) list.size else -1
                val result = mutableListOf<PyValue>()
                if (step > 0) {
                    var i = if (start < 0) (list.size + start).coerceAtLeast(0) else start
                    val actualStop = if (stop < 0) (list.size + stop).coerceAtLeast(0) else stop.coerceAtMost(list.size)
                    while (i < actualStop && i in list.indices) {
                        result.add(list[i])
                        i += step
                    }
                } else {
                    var i = if (start < 0) (list.size + start) else start.coerceAtMost(list.size - 1)
                    val actualStop = if (stop < 0) (list.size + stop) else stop
                    while (i > actualStop && i in list.indices) {
                        result.add(list[i])
                        i += step
                    }
                }
                return PyValue.ListVal(result)
            }
            else -> throw RuntimeException("TypeError: '${target.typeName()}' object is not sliceable")
        }
    }

    private fun evalAttributeAccess(target: PyValue, attr: String): PyValue {
        when (target) {
            is PyValue.ModuleVal -> {
                return target.members[attr]
                    ?: throw RuntimeException("AttributeError: module '${target.name}' has no attribute '$attr'")
            }
            is PyValue.InstanceVal -> {
                target.fields[attr]?.let { return it }
                val method = target.pyClass.methods[attr]
                if (method != null) {
                    // Bound method (self prepended)
                    return PyValue.BuiltinFuncVal(attr) { args, kwargs, _ ->
                        val boundArgs = mutableListOf<PyValue>(target)
                        boundArgs.addAll(args)
                        callFunction(method, boundArgs, kwargs)
                    }
                }
                throw RuntimeException("AttributeError: '${target.pyClass.name}' object has no attribute '$attr'")
            }
            is PyValue.StringVal -> {
                return when (attr) {
                    "upper" -> PyValue.BuiltinFuncVal("upper") { _, _, _ -> PyValue.StringVal(target.value.uppercase()) }
                    "lower" -> PyValue.BuiltinFuncVal("lower") { _, _, _ -> PyValue.StringVal(target.value.lowercase()) }
                    "strip" -> PyValue.BuiltinFuncVal("strip") { _, _, _ -> PyValue.StringVal(target.value.trim()) }
                    "split" -> PyValue.BuiltinFuncVal("split") { args, _, _ ->
                        val sep = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: " "
                        val parts = if (sep == " ") target.value.trim().split(Regex("\\s+")) else target.value.split(sep)
                        PyValue.ListVal(parts.map { PyValue.StringVal(it) }.toMutableList())
                    }
                    "replace" -> PyValue.BuiltinFuncVal("replace") { args, _, _ ->
                        val old = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                        val new = (args.getOrNull(1) as? PyValue.StringVal)?.value ?: ""
                        PyValue.StringVal(target.value.replace(old, new))
                    }
                    "join" -> PyValue.BuiltinFuncVal("join") { args, _, _ ->
                        val seq = when (val item = args.getOrNull(0)) {
                            is PyValue.ListVal -> item.elements
                            is PyValue.TupleVal -> item.elements
                            else -> emptyList()
                        }
                        PyValue.StringVal(seq.joinToString(target.value) { it.toDisplayString() })
                    }
                    "startswith" -> PyValue.BuiltinFuncVal("startswith") { args, _, _ ->
                        val prefix = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                        PyValue.BoolVal(target.value.startsWith(prefix))
                    }
                    "endswith" -> PyValue.BuiltinFuncVal("endswith") { args, _, _ ->
                        val suffix = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                        PyValue.BoolVal(target.value.endsWith(suffix))
                    }
                    "find" -> PyValue.BuiltinFuncVal("find") { args, _, _ ->
                        val sub = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: ""
                        PyValue.IntVal(target.value.indexOf(sub).toLong())
                    }
                    "encode" -> PyValue.BuiltinFuncVal("encode") { args, _, _ ->
                        val encoding = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "utf-8"
                        try {
                            PyValue.BytesVal(target.value.toByteArray(charset(encoding)))
                        } catch (e: Exception) {
                            PyValue.BytesVal(target.value.toByteArray(Charsets.UTF_8))
                        }
                    }
                    else -> throw RuntimeException("AttributeError: 'str' object has no attribute '$attr'")
                }
            }
            is PyValue.BytesVal -> {
                return when (attr) {
                    "hex" -> PyValue.BuiltinFuncVal("hex") { _, _, _ -> PyValue.StringVal(target.hex()) }
                    "decode" -> PyValue.BuiltinFuncVal("decode") { args, _, _ ->
                        val encoding = (args.getOrNull(0) as? PyValue.StringVal)?.value ?: "utf-8"
                        try {
                            PyValue.StringVal(String(target.data, charset(encoding)))
                        } catch (e: Exception) {
                            PyValue.StringVal(String(target.data, Charsets.UTF_8))
                        }
                    }
                    else -> throw RuntimeException("AttributeError: 'bytes' object has no attribute '$attr'")
                }
            }
            is PyValue.ListVal -> {
                return when (attr) {
                    "append" -> PyValue.BuiltinFuncVal("append") { args, _, _ ->
                        target.elements.add(args.getOrNull(0) ?: PyValue.NoneVal)
                        PyValue.NoneVal
                    }
                    "extend" -> PyValue.BuiltinFuncVal("extend") { args, _, _ ->
                        val item = args.getOrNull(0)
                        if (item is PyValue.ListVal) target.elements.addAll(item.elements)
                        PyValue.NoneVal
                    }
                    "pop" -> PyValue.BuiltinFuncVal("pop") { args, _, _ ->
                        val idx = (args.getOrNull(0) as? PyValue.IntVal)?.value?.toInt() ?: (target.elements.size - 1)
                        if (idx in target.elements.indices) target.elements.removeAt(idx)
                        else throw RuntimeException("IndexError: pop index out of range")
                    }
                    "remove" -> PyValue.BuiltinFuncVal("remove") { args, _, _ ->
                        val elem = args.getOrNull(0) ?: PyValue.NoneVal
                        val idx = target.elements.indexOfFirst { pyValuesEqual(it, elem) }
                        if (idx >= 0) target.elements.removeAt(idx)
                        else throw RuntimeException("ValueError: list.remove(x): x not in list")
                        PyValue.NoneVal
                    }
                    "clear" -> PyValue.BuiltinFuncVal("clear") { _, _, _ ->
                        target.elements.clear()
                        PyValue.NoneVal
                    }
                    "count" -> PyValue.BuiltinFuncVal("count") { args, _, _ ->
                        val elem = args.getOrNull(0) ?: PyValue.NoneVal
                        PyValue.IntVal(target.elements.count { pyValuesEqual(it, elem) }.toLong())
                    }
                    "insert" -> PyValue.BuiltinFuncVal("insert") { args, _, _ ->
                        val idx = (args.getOrNull(0) as? PyValue.IntVal)?.value?.toInt() ?: 0
                        val elem = args.getOrNull(1) ?: PyValue.NoneVal
                        target.elements.add(idx.coerceIn(0, target.elements.size), elem)
                        PyValue.NoneVal
                    }
                    else -> throw RuntimeException("AttributeError: 'list' object has no attribute '$attr'")
                }
            }
            is PyValue.DictVal -> {
                return when (attr) {
                    "get" -> PyValue.BuiltinFuncVal("get") { args, _, _ ->
                        val key = args.getOrNull(0)?.toDisplayString() ?: ""
                        val default = args.getOrNull(1) ?: PyValue.NoneVal
                        target.entries[key] ?: default
                    }
                    "keys" -> PyValue.BuiltinFuncVal("keys") { _, _, _ ->
                        PyValue.ListVal(target.entries.keys.map { PyValue.StringVal(it) }.toMutableList())
                    }
                    "values" -> PyValue.BuiltinFuncVal("values") { _, _, _ ->
                        PyValue.ListVal(target.entries.values.toMutableList())
                    }
                    "items" -> PyValue.BuiltinFuncVal("items") { _, _, _ ->
                        val pairs = target.entries.entries.map {
                            PyValue.TupleVal(listOf(PyValue.StringVal(it.key), it.value))
                        }
                        PyValue.ListVal(pairs.toMutableList())
                    }
                    "update" -> PyValue.BuiltinFuncVal("update") { args, _, _ ->
                        val other = args.getOrNull(0)
                        if (other is PyValue.DictVal) {
                            target.entries.putAll(other.entries)
                        }
                        PyValue.NoneVal
                    }
                    "pop" -> PyValue.BuiltinFuncVal("pop") { args, _, _ ->
                        val key = args.getOrNull(0)?.toDisplayString() ?: ""
                        target.entries.remove(key) ?: (args.getOrNull(1) ?: throw RuntimeException("KeyError: '$key'"))
                    }
                    else -> throw RuntimeException("AttributeError: 'dict' object has no attribute '$attr'")
                }
            }
            is PyValue.SetVal -> {
                return when (attr) {
                    "add" -> PyValue.BuiltinFuncVal("add") { args, _, _ ->
                        target.elements.add(args.getOrNull(0) ?: PyValue.NoneVal)
                        PyValue.NoneVal
                    }
                    "remove" -> PyValue.BuiltinFuncVal("remove") { args, _, _ ->
                        val elem = args.getOrNull(0) ?: PyValue.NoneVal
                        target.elements.remove(elem)
                        PyValue.NoneVal
                    }
                    else -> throw RuntimeException("AttributeError: 'set' object has no attribute '$attr'")
                }
            }
            else -> throw RuntimeException("AttributeError: '${target.typeName()}' object has no attribute '$attr'")
        }
    }

    suspend fun callTarget(target: PyValue, args: List<PyValue>, kwargs: Map<String, PyValue>): PyValue {
        return when (target) {
            is PyValue.FunctionVal -> callFunction(target, args, kwargs)
            is PyValue.BuiltinFuncVal -> target.handler(args, kwargs, ctx)
            is PyValue.ClassVal -> {
                // Instantiate class
                val instance = PyValue.InstanceVal(target)
                val initMethod = target.methods["__init__"]
                if (initMethod != null) {
                    val initArgs = mutableListOf<PyValue>(instance)
                    initArgs.addAll(args)
                    callFunction(initMethod, initArgs, kwargs)
                }
                instance
            }
            else -> throw RuntimeException("TypeError: '${target.typeName()}' object is not callable")
        }
    }

    suspend fun callFunction(fn: PyValue.FunctionVal, args: List<PyValue>, kwargs: Map<String, PyValue>): PyValue {
        val newScope = fn.closureScope.toMutableMap()

        // Bind positional parameters
        var argIdx = 0
        for (i in fn.params.indices) {
            val paramName = fn.params[i]
            if (argIdx < args.size) {
                newScope[paramName] = args[argIdx]
                argIdx++
            } else if (kwargs.containsKey(paramName)) {
                newScope[paramName] = kwargs[paramName]!!
            } else if (fn.defaultParams.containsKey(paramName)) {
                newScope[paramName] = fn.defaultParams[paramName]!!
            } else {
                throw RuntimeException("TypeError: ${fn.name}() missing required argument: '$paramName'")
            }
        }

        // Handle *args vararg
        if (fn.vararg != null) {
            val varargsList = if (argIdx < args.size) args.subList(argIdx, args.size) else emptyList()
            newScope[fn.vararg] = PyValue.TupleVal(varargsList)
        }

        ctx.pushScope(newScope)
        val flow = executeBlock(fn.body)
        ctx.popScope()

        return when (flow) {
            is FlowControl.Return -> flow.value
            else -> PyValue.NoneVal
        }
    }
}
