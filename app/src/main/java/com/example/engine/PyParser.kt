package com.example.engine

class PyParser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Statement> {
        val statements = mutableListOf<Statement>()
        skipNewlines()
        while (!isAtEnd()) {
            val stmt = parseStatement()
            if (stmt != null) {
                statements.add(stmt)
            }
            skipNewlines()
        }
        return statements
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = if (current < tokens.size) tokens[current] else tokens.last()

    private fun previous(): Token = tokens[current - 1]

    private fun check(type: TokenType): Boolean = !isAtEnd() && peek().type == type

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw RuntimeException("Line ${peek().line}:${peek().column} SyntaxError: $message, found '${peek().value}'")
    }

    private fun skipNewlines() {
        while (check(TokenType.NEWLINE)) {
            advance()
        }
    }

    private fun parseStatement(): Statement? {
        skipNewlines()
        if (isAtEnd()) return null

        return when (peek().type) {
            TokenType.DEF -> parseDef()
            TokenType.CLASS -> parseClass()
            TokenType.IF -> parseIf()
            TokenType.WHILE -> parseWhile()
            TokenType.FOR -> parseFor()
            TokenType.RETURN -> parseReturn()
            TokenType.BREAK -> { advance(); consumeEnding(); Statement.BreakStmt }
            TokenType.CONTINUE -> { advance(); consumeEnding(); Statement.ContinueStmt }
            TokenType.PASS -> { advance(); consumeEnding(); Statement.PassStmt }
            TokenType.IMPORT, TokenType.FROM -> parseImport()
            TokenType.TRY -> parseTry()
            TokenType.RAISE -> parseRaise()
            TokenType.ASSERT -> parseAssert()
            else -> parseSimpleOrAssignStatement()
        }
    }

    private fun parseDef(): Statement {
        advance() // def
        val name = consume(TokenType.IDENTIFIER, "Expected function name").value
        consume(TokenType.LPAREN, "Expected '(' after function name")
        val params = mutableListOf<String>()
        val defaultParams = mutableMapOf<String, Expression>()
        var vararg: String? = null

        if (!check(TokenType.RPAREN)) {
            do {
                if (match(TokenType.STAR)) {
                    vararg = consume(TokenType.IDENTIFIER, "Expected parameter name after '*'").value
                } else {
                    val pName = consume(TokenType.IDENTIFIER, "Expected parameter name").value
                    params.add(pName)
                    // Optional type hint
                    if (match(TokenType.COLON)) {
                        parseTypeHint()
                    }
                    if (match(TokenType.ASSIGN)) {
                        defaultParams[pName] = parseExpression()
                    }
                }
            } while (match(TokenType.COMMA) && !check(TokenType.RPAREN))
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters")

        // Return type hint
        if (match(TokenType.ARROW)) {
            parseTypeHint()
        }

        consume(TokenType.COLON, "Expected ':' after function signature")
        val body = parseBlock()
        return Statement.DefStmt(name, params, defaultParams, vararg, body)
    }

    private fun parseTypeHint() {
        // Skip type hints like int, str, List[int], etc.
        if (match(TokenType.IDENTIFIER)) {
            if (match(TokenType.LBRACKET)) {
                while (!check(TokenType.RBRACKET) && !isAtEnd()) {
                    advance()
                }
                match(TokenType.RBRACKET)
            }
        }
    }

    private fun parseClass(): Statement {
        advance() // class
        val name = consume(TokenType.IDENTIFIER, "Expected class name").value
        var baseClass: String? = null
        if (match(TokenType.LPAREN)) {
            if (check(TokenType.IDENTIFIER)) {
                baseClass = advance().value
            }
            consume(TokenType.RPAREN, "Expected ')' after base class")
        }
        consume(TokenType.COLON, "Expected ':' after class declaration")
        val body = parseBlock()
        return Statement.ClassStmt(name, baseClass, body)
    }

    private fun parseIf(): Statement {
        advance() // if
        val branches = mutableListOf<Pair<Expression, List<Statement>>>()
        val firstCond = parseExpression()
        consume(TokenType.COLON, "Expected ':' after if condition")
        val firstBody = parseBlock()
        branches.add(Pair(firstCond, firstBody))

        while (match(TokenType.ELIF)) {
            val cond = parseExpression()
            consume(TokenType.COLON, "Expected ':' after elif condition")
            val body = parseBlock()
            branches.add(Pair(cond, body))
        }

        var elseBranch: List<Statement>? = null
        if (match(TokenType.ELSE)) {
            consume(TokenType.COLON, "Expected ':' after else")
            elseBranch = parseBlock()
        }

        return Statement.IfStmt(branches, elseBranch)
    }

    private fun parseWhile(): Statement {
        advance() // while
        val condition = parseExpression()
        consume(TokenType.COLON, "Expected ':' after while condition")
        val body = parseBlock()
        var elseBranch: List<Statement>? = null
        if (match(TokenType.ELSE)) {
            consume(TokenType.COLON, "Expected ':' after else")
            elseBranch = parseBlock()
        }
        return Statement.WhileStmt(condition, body, elseBranch)
    }

    private fun parseFor(): Statement {
        advance() // for
        val variables = mutableListOf<String>()
        do {
            variables.add(consume(TokenType.IDENTIFIER, "Expected loop variable name").value)
        } while (match(TokenType.COMMA))
        consume(TokenType.IN, "Expected 'in' after loop variable")
        val iterable = parseExpression()
        consume(TokenType.COLON, "Expected ':' after for statement")
        val body = parseBlock()
        var elseBranch: List<Statement>? = null
        if (match(TokenType.ELSE)) {
            consume(TokenType.COLON, "Expected ':' after else")
            elseBranch = parseBlock()
        }
        return Statement.ForStmt(variables, iterable, body, elseBranch)
    }

    private fun parseReturn(): Statement {
        advance() // return
        val value = if (check(TokenType.NEWLINE) || check(TokenType.EOF) || check(TokenType.DEDENT)) null else parseExpression()
        consumeEnding()
        return Statement.ReturnStmt(value)
    }

    private fun parseImport(): Statement {
        if (match(TokenType.FROM)) {
            val moduleName = consume(TokenType.IDENTIFIER, "Expected module name after from").value
            consume(TokenType.IMPORT, "Expected 'import' after module name")
            val specific = mutableListOf<Pair<String, String?>>()
            do {
                val item = consume(TokenType.IDENTIFIER, "Expected import symbol name").value
                var alias: String? = null
                if (match(TokenType.AS)) {
                    alias = consume(TokenType.IDENTIFIER, "Expected alias after 'as'").value
                }
                specific.add(Pair(item, alias))
            } while (match(TokenType.COMMA))
            consumeEnding()
            return Statement.ImportStmt(moduleName, null, specific)
        } else {
            advance() // import
            val moduleName = consume(TokenType.IDENTIFIER, "Expected module name after import").value
            var alias: String? = null
            if (match(TokenType.AS)) {
                alias = consume(TokenType.IDENTIFIER, "Expected alias after 'as'").value
            }
            consumeEnding()
            return Statement.ImportStmt(moduleName, alias)
        }
    }

    private fun parseTry(): Statement {
        advance() // try
        consume(TokenType.COLON, "Expected ':' after try")
        val tryBody = parseBlock()
        val excepts = mutableListOf<ExceptClause>()

        while (match(TokenType.EXCEPT)) {
            var exType: String? = null
            var exVar: String? = null
            if (!check(TokenType.COLON)) {
                if (check(TokenType.IDENTIFIER)) {
                    exType = advance().value
                    if (match(TokenType.AS)) {
                        exVar = consume(TokenType.IDENTIFIER, "Expected exception variable name").value
                    }
                }
            }
            consume(TokenType.COLON, "Expected ':' after except clause")
            val exBody = parseBlock()
            excepts.add(ExceptClause(exType, exVar, exBody))
        }

        var finBody: List<Statement>? = null
        if (match(TokenType.FINALLY)) {
            consume(TokenType.COLON, "Expected ':' after finally")
            finBody = parseBlock()
        }

        return Statement.TryExceptStmt(tryBody, excepts, finBody)
    }

    private fun parseRaise(): Statement {
        advance() // raise
        val expr = if (!check(TokenType.NEWLINE) && !isAtEnd()) parseExpression() else null
        consumeEnding()
        return Statement.RaiseStmt(expr)
    }

    private fun parseAssert(): Statement {
        advance() // assert
        val condition = parseExpression()
        var msg: Expression? = null
        if (match(TokenType.COMMA)) {
            msg = parseExpression()
        }
        consumeEnding()
        return Statement.AssertStmt(condition, msg)
    }

    private fun parseSimpleOrAssignStatement(): Statement {
        val expr = parseExpression()

        // Check for augmented assignment (+=, -=, etc.)
        val augOp = when {
            match(TokenType.PLUS_ASSIGN) -> BinaryOperator.ADD
            match(TokenType.MINUS_ASSIGN) -> BinaryOperator.SUBTRACT
            match(TokenType.STAR_ASSIGN) -> BinaryOperator.MULTIPLY
            match(TokenType.SLASH_ASSIGN) -> BinaryOperator.DIVIDE
            match(TokenType.PERCENT_ASSIGN) -> BinaryOperator.MODULO
            match(TokenType.DOUBLE_SLASH_ASSIGN) -> BinaryOperator.FLOOR_DIVIDE
            else -> null
        }

        if (augOp != null) {
            val right = parseExpression()
            consumeEnding()
            return Statement.AugmentedAssign(expr, augOp, right)
        }

        // Check for standard assignment (=)
        if (match(TokenType.ASSIGN)) {
            val right = parseExpression()
            consumeEnding()
            return Statement.Assign(listOf(expr), right)
        }

        consumeEnding()
        return Statement.ExprStmt(expr)
    }

    private fun parseBlock(): List<Statement> {
        val body = mutableListOf<Statement>()
        skipNewlines()
        if (match(TokenType.INDENT)) {
            while (!check(TokenType.DEDENT) && !isAtEnd()) {
                skipNewlines()
                if (check(TokenType.DEDENT)) break
                val stmt = parseStatement()
                if (stmt != null) body.add(stmt)
                skipNewlines()
            }
            if (!isAtEnd()) match(TokenType.DEDENT)
        } else {
            // Single-line block
            val stmt = parseStatement()
            if (stmt != null) body.add(stmt)
        }
        return body
    }

    private fun consumeEnding() {
        if (!isAtEnd() && !check(TokenType.DEDENT)) {
            match(TokenType.NEWLINE)
        }
    }

    // Expressions parsing with operator precedence

    fun parseExpression(): Expression {
        return parseTernary()
    }

    private fun parseTernary(): Expression {
        val expr = parseLambda()
        if (match(TokenType.IF)) {
            val condition = parseExpression()
            consume(TokenType.ELSE, "Expected 'else' in ternary expression")
            val falseExpr = parseExpression()
            return Expression.Ternary(condition, expr, falseExpr)
        }
        return expr
    }

    private fun parseLambda(): Expression {
        if (match(TokenType.LAMBDA)) {
            val params = mutableListOf<String>()
            if (!check(TokenType.COLON)) {
                do {
                    params.add(consume(TokenType.IDENTIFIER, "Expected parameter name in lambda").value)
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.COLON, "Expected ':' after lambda parameters")
            val body = parseExpression()
            return Expression.Lambda(params, body)
        }
        return parseLogicalOr()
    }

    private fun parseLogicalOr(): Expression {
        var expr = parseLogicalAnd()
        while (match(TokenType.OR)) {
            val right = parseLogicalAnd()
            expr = Expression.BinaryOp(expr, BinaryOperator.OR, right)
        }
        return expr
    }

    private fun parseLogicalAnd(): Expression {
        var expr = parseLogicalNot()
        while (match(TokenType.AND)) {
            val right = parseLogicalNot()
            expr = Expression.BinaryOp(expr, BinaryOperator.AND, right)
        }
        return expr
    }

    private fun parseLogicalNot(): Expression {
        if (match(TokenType.NOT)) {
            val expr = parseLogicalNot()
            return Expression.UnaryOp(UnaryOperator.NOT, expr)
        }
        return parseComparison()
    }

    private fun parseComparison(): Expression {
        var expr = parseBitwiseOr()
        while (true) {
            val op = when {
                match(TokenType.EQUAL) -> BinaryOperator.EQUAL
                match(TokenType.NOT_EQUAL) -> BinaryOperator.NOT_EQUAL
                match(TokenType.LESS) -> BinaryOperator.LESS_THAN
                match(TokenType.LESS_EQUAL) -> BinaryOperator.LESS_EQUAL
                match(TokenType.GREATER) -> BinaryOperator.GREATER_THAN
                match(TokenType.GREATER_EQUAL) -> BinaryOperator.GREATER_EQUAL
                match(TokenType.IN) -> BinaryOperator.IN
                match(TokenType.NOT) -> {
                    consume(TokenType.IN, "Expected 'in' after 'not'")
                    BinaryOperator.NOT_IN
                }
                match(TokenType.IS) -> {
                    if (match(TokenType.NOT)) BinaryOperator.IS_NOT else BinaryOperator.IS
                }
                else -> null
            } ?: break

            val right = parseBitwiseOr()
            expr = Expression.BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun parseBitwiseOr(): Expression {
        var expr = parseBitwiseXor()
        while (match(TokenType.BIT_OR)) {
            val right = parseBitwiseXor()
            expr = Expression.BinaryOp(expr, BinaryOperator.BIT_OR, right)
        }
        return expr
    }

    private fun parseBitwiseXor(): Expression {
        var expr = parseBitwiseAnd()
        while (match(TokenType.BIT_XOR)) {
            val right = parseBitwiseAnd()
            expr = Expression.BinaryOp(expr, BinaryOperator.BIT_XOR, right)
        }
        return expr
    }

    private fun parseBitwiseAnd(): Expression {
        var expr = parseBitwiseShift()
        while (match(TokenType.BIT_AND)) {
            val right = parseBitwiseShift()
            expr = Expression.BinaryOp(expr, BinaryOperator.BIT_AND, right)
        }
        return expr
    }

    private fun parseBitwiseShift(): Expression {
        var expr = parseAdditive()
        while (true) {
            val op = when {
                match(TokenType.BIT_LSHIFT) -> BinaryOperator.BIT_LSHIFT
                match(TokenType.BIT_RSHIFT) -> BinaryOperator.BIT_RSHIFT
                else -> null
            } ?: break
            val right = parseAdditive()
            expr = Expression.BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun parseAdditive(): Expression {
        var expr = parseMultiplicative()
        while (true) {
            val op = when {
                match(TokenType.PLUS) -> BinaryOperator.ADD
                match(TokenType.MINUS) -> BinaryOperator.SUBTRACT
                else -> null
            } ?: break
            val right = parseMultiplicative()
            expr = Expression.BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun parseMultiplicative(): Expression {
        var expr = parseUnary()
        while (true) {
            val op = when {
                match(TokenType.STAR) -> BinaryOperator.MULTIPLY
                match(TokenType.SLASH) -> BinaryOperator.DIVIDE
                match(TokenType.DOUBLE_SLASH) -> BinaryOperator.FLOOR_DIVIDE
                match(TokenType.PERCENT) -> BinaryOperator.MODULO
                else -> null
            } ?: break
            val right = parseUnary()
            expr = Expression.BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun parseUnary(): Expression {
        if (match(TokenType.MINUS)) {
            return Expression.UnaryOp(UnaryOperator.NEGATE, parseUnary())
        }
        if (match(TokenType.PLUS)) {
            return Expression.UnaryOp(UnaryOperator.POSITIVE, parseUnary())
        }
        if (match(TokenType.BIT_NOT)) {
            return Expression.UnaryOp(UnaryOperator.BIT_NOT, parseUnary())
        }
        return parsePower()
    }

    private fun parsePower(): Expression {
        var expr = parsePostfix()
        if (match(TokenType.DOUBLE_STAR)) {
            val right = parseUnary()
            expr = Expression.BinaryOp(expr, BinaryOperator.POWER, right)
        }
        return expr
    }

    private fun parsePostfix(): Expression {
        var expr = parsePrimary()
        while (true) {
            if (match(TokenType.LPAREN)) {
                // Function call
                val args = mutableListOf<Expression>()
                val kwargs = mutableMapOf<String, Expression>()
                if (!check(TokenType.RPAREN)) {
                    do {
                        if (check(TokenType.IDENTIFIER) && peekAheadIs(1, TokenType.ASSIGN)) {
                            val key = advance().value
                            advance() // =
                            val v = parseExpression()
                            kwargs[key] = v
                        } else {
                            args.add(parseExpression())
                        }
                    } while (match(TokenType.COMMA) && !check(TokenType.RPAREN))
                }
                consume(TokenType.RPAREN, "Expected ')' after function arguments")
                expr = Expression.FunctionCall(expr, args, kwargs)
            } else if (match(TokenType.LBRACKET)) {
                // Index or slice access
                var start: Expression? = null
                var stop: Expression? = null
                var step: Expression? = null

                if (match(TokenType.COLON)) {
                    // [:stop:step]
                    if (!check(TokenType.COLON) && !check(TokenType.RBRACKET)) {
                        stop = parseExpression()
                    }
                    if (match(TokenType.COLON)) {
                        if (!check(TokenType.RBRACKET)) {
                            step = parseExpression()
                        }
                    }
                    consume(TokenType.RBRACKET, "Expected ']' after slice")
                    expr = Expression.SliceAccess(expr, start, stop, step)
                } else {
                    val firstExpr = parseExpression()
                    if (match(TokenType.COLON)) {
                        start = firstExpr
                        if (!check(TokenType.COLON) && !check(TokenType.RBRACKET)) {
                            stop = parseExpression()
                        }
                        if (match(TokenType.COLON)) {
                            if (!check(TokenType.RBRACKET)) {
                                step = parseExpression()
                            }
                        }
                        consume(TokenType.RBRACKET, "Expected ']' after slice")
                        expr = Expression.SliceAccess(expr, start, stop, step)
                    } else {
                        consume(TokenType.RBRACKET, "Expected ']' after index")
                        expr = Expression.IndexAccess(expr, firstExpr)
                    }
                }
            } else if (match(TokenType.DOT)) {
                val attr = consume(TokenType.IDENTIFIER, "Expected attribute name after '.'").value
                expr = Expression.AttributeAccess(expr, attr)
            } else {
                break
            }
        }
        return expr
    }

    private fun peekAheadIs(offset: Int, type: TokenType): Boolean {
        val target = current + offset
        return target < tokens.size && tokens[target].type == type
    }

    private fun parsePrimary(): Expression {
        if (match(TokenType.TRUE)) return Expression.Literal(PyValue.BoolVal(true))
        if (match(TokenType.FALSE)) return Expression.Literal(PyValue.BoolVal(false))
        if (match(TokenType.NONE)) return Expression.Literal(PyValue.NoneVal)
        if (match(TokenType.INT)) {
            val num = previous().value.toLongOrNull() ?: 0L
            return Expression.Literal(PyValue.IntVal(num))
        }
        if (match(TokenType.FLOAT)) {
            val num = previous().value.toDoubleOrNull() ?: 0.0
            return Expression.Literal(PyValue.FloatVal(num))
        }
        if (match(TokenType.STRING)) {
            return Expression.Literal(PyValue.StringVal(previous().value))
        }
        if (match(TokenType.FSTRING)) {
            return parseFStringLiteral(previous().value)
        }
        if (match(TokenType.IDENTIFIER)) {
            return Expression.Identifier(previous().value)
        }

        // Parentheses or Tuples
        if (match(TokenType.LPAREN)) {
            if (match(TokenType.RPAREN)) {
                return Expression.TupleLiteral(emptyList())
            }
            val expr = parseExpression()
            if (match(TokenType.COMMA)) {
                val items = mutableListOf(expr)
                while (!check(TokenType.RPAREN) && !isAtEnd()) {
                    items.add(parseExpression())
                    if (!match(TokenType.COMMA)) break
                }
                consume(TokenType.RPAREN, "Expected ')' after tuple")
                return Expression.TupleLiteral(items)
            }
            consume(TokenType.RPAREN, "Expected ')' after expression")
            return expr
        }

        // List literal or list comprehension
        if (match(TokenType.LBRACKET)) {
            if (match(TokenType.RBRACKET)) {
                return Expression.ListLiteral(emptyList())
            }
            val firstExpr = parseExpression()
            if (match(TokenType.FOR)) {
                // List comprehension: [expr for x in iter if cond] or [expr for k, v in iter if cond]
                val variables = mutableListOf<String>()
                do {
                    variables.add(consume(TokenType.IDENTIFIER, "Expected variable name in comprehension").value)
                } while (match(TokenType.COMMA))
                consume(TokenType.IN, "Expected 'in' in comprehension")
                val iter = parseLogicalOr()
                var cond: Expression? = null
                if (match(TokenType.IF)) {
                    cond = parseLogicalOr()
                }
                consume(TokenType.RBRACKET, "Expected ']' after list comprehension")
                return Expression.ListComprehension(firstExpr, variables, iter, cond)
            }

            val items = mutableListOf(firstExpr)
            while (match(TokenType.COMMA) && !check(TokenType.RBRACKET)) {
                items.add(parseExpression())
            }
            consume(TokenType.RBRACKET, "Expected ']' after list")
            return Expression.ListLiteral(items)
        }

        // Dict / Set literal
        if (match(TokenType.LBRACE)) {
            if (match(TokenType.RBRACE)) {
                return Expression.DictLiteral(emptyList())
            }
            val firstKey = parseExpression()
            if (match(TokenType.COLON)) {
                // Dict
                val firstVal = parseExpression()
                if (match(TokenType.FOR)) {
                    // Dict comprehension
                    val variables = mutableListOf<String>()
                    do {
                        variables.add(consume(TokenType.IDENTIFIER, "Expected variable name in dict comprehension").value)
                    } while (match(TokenType.COMMA))
                    consume(TokenType.IN, "Expected 'in' in dict comprehension")
                    val iter = parseLogicalOr()
                    var cond: Expression? = null
                    if (match(TokenType.IF)) {
                        cond = parseLogicalOr()
                    }
                    consume(TokenType.RBRACE, "Expected '}' after dict comprehension")
                    return Expression.DictComprehension(firstKey, firstVal, variables, iter, cond)
                }

                val entries = mutableListOf(Pair(firstKey, firstVal))
                while (match(TokenType.COMMA) && !check(TokenType.RBRACE)) {
                    val k = parseExpression()
                    consume(TokenType.COLON, "Expected ':' after dictionary key")
                    val v = parseExpression()
                    entries.add(Pair(k, v))
                }
                consume(TokenType.RBRACE, "Expected '}' after dictionary")
                return Expression.DictLiteral(entries)
            } else {
                // Set
                val items = mutableListOf(firstKey)
                while (match(TokenType.COMMA) && !check(TokenType.RBRACE)) {
                    items.add(parseExpression())
                }
                consume(TokenType.RBRACE, "Expected '}' after set")
                return Expression.SetLiteral(items)
            }
        }

        throw RuntimeException("Line ${peek().line}:${peek().column} SyntaxError: Unexpected token '${peek().value}'")
    }

    private fun parseFStringLiteral(raw: String): Expression {
        // Splits f"Hello {name}, your score is {score * 2}" into concatenated expressions
        val parts = mutableListOf<Expression>()
        var i = 0
        val sb = StringBuilder()

        while (i < raw.length) {
            if (raw[i] == '{' && i + 1 < raw.length && raw[i + 1] != '{') {
                if (sb.isNotEmpty()) {
                    parts.add(Expression.Literal(PyValue.StringVal(sb.toString())))
                    sb.clear()
                }
                i++
                val exprCode = StringBuilder()
                while (i < raw.length && raw[i] != '}') {
                    exprCode.append(raw[i])
                    i++
                }
                if (i < raw.length && raw[i] == '}') i++
                
                try {
                    val innerLexer = PyLexer(exprCode.toString())
                    val innerTokens = innerLexer.tokenize()
                    val innerParser = PyParser(innerTokens)
                    parts.add(innerParser.parseExpression())
                } catch (e: Exception) {
                    // Fallback to literal text if not a valid Python expression (e.g. CSS styles or raw text)
                    parts.add(Expression.Literal(PyValue.StringVal("{" + exprCode.toString() + "}")))
                }
            } else if (raw[i] == '{' && i + 1 < raw.length && raw[i + 1] == '{') {
                sb.append('{')
                i += 2
            } else if (raw[i] == '}' && i + 1 < raw.length && raw[i + 1] == '}') {
                sb.append('}')
                i += 2
            } else {
                sb.append(raw[i])
                i++
            }
        }

        if (sb.isNotEmpty()) {
            parts.add(Expression.Literal(PyValue.StringVal(sb.toString())))
        }

        return Expression.FormattedString(parts)
    }
}
