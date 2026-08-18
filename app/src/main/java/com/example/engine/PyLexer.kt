package com.example.engine

enum class TokenType {
    // Literals
    INT, FLOAT, STRING, FSTRING, IDENTIFIER,

    // Keywords
    DEF, CLASS, IF, ELIF, ELSE, WHILE, FOR, IN, NOT_IN, RETURN, BREAK, CONTINUE, PASS,
    IMPORT, FROM, AS, TRY, EXCEPT, FINALLY, RAISE, ASSERT, AND, OR, NOT, IS, LAMBDA,
    TRUE, FALSE, NONE,

    // Operators & Delimiters
    PLUS, MINUS, STAR, DOUBLE_STAR, SLASH, DOUBLE_SLASH, PERCENT,
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, DOUBLE_SLASH_ASSIGN, PERCENT_ASSIGN,
    EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
    COLON, COMMA, DOT, ARROW, BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, BIT_LSHIFT, BIT_RSHIFT,

    // Structure
    NEWLINE, INDENT, DEDENT, EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val line: Int,
    val column: Int
)

class PyLexer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1
    private val indentStack = mutableListOf(0)
    private var bracketNesting = 0
    private val pendingTokens = mutableListOf<Token>()
    private var atLineStart = true

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = nextToken()
            tokens.add(token)
            if (token.type == TokenType.EOF) break
        }
        return tokens
    }

    private fun nextToken(): Token {
        if (pendingTokens.isNotEmpty()) {
            return pendingTokens.removeAt(0)
        }

        while (index < source.length) {
            val char = source[index]

            // Handle start of line indentation
            if (atLineStart && bracketNesting == 0) {
                var currentIndent = 0
                val startCol = column
                while (index < source.length && (source[index] == ' ' || source[index] == '\t')) {
                    if (source[index] == '\t') currentIndent += 4 else currentIndent += 1
                    index++
                    column++
                }

                // If line is empty or comment, ignore indentation
                if (index < source.length && (source[index] == '\n' || source[index] == '\r' || source[index] == '#')) {
                    atLineStart = false
                    continue
                }

                atLineStart = false
                val previousIndent = indentStack.last()

                if (currentIndent > previousIndent) {
                    indentStack.add(currentIndent)
                    return Token(TokenType.INDENT, "", line, startCol)
                } else if (currentIndent < previousIndent) {
                    while (indentStack.isNotEmpty() && indentStack.last() > currentIndent) {
                        indentStack.removeAt(indentStack.size - 1)
                        pendingTokens.add(Token(TokenType.DEDENT, "", line, startCol))
                    }
                    if (pendingTokens.isNotEmpty()) {
                        return pendingTokens.removeAt(0)
                    }
                }
            }

            // Skip inline whitespace
            if (char == ' ' || char == '\t') {
                index++
                column++
                continue
            }

            // Comments
            if (char == '#') {
                while (index < source.length && source[index] != '\n') {
                    index++
                }
                continue
            }

            // Newlines
            if (char == '\n' || (char == '\r' && peek(1) == '\n')) {
                if (char == '\r') index++
                index++
                val curLine = line
                val curCol = column
                line++
                column = 1
                atLineStart = true

                if (bracketNesting == 0) {
                    return Token(TokenType.NEWLINE, "\\n", curLine, curCol)
                }
                continue
            }

            // Strings
            if (char == '"' || char == '\'') {
                return scanString(char, false)
            }

            // Formatted string or raw string prefix
            if ((char == 'f' || char == 'F' || char == 'r' || char == 'R') &&
                (peek(1) == '"' || peek(1) == '\'')
            ) {
                val isFString = (char == 'f' || char == 'F')
                index++
                column++
                return scanString(source[index], isFString)
            }

            // Numbers
            if (char.isDigit() || (char == '.' && peek(1).isDigit())) {
                return scanNumber()
            }

            // Identifiers and keywords
            if (char.isLetter() || char == '_') {
                return scanIdentifierOrKeyword()
            }

            // Operators & Delimiters
            val startCol = column
            val startLine = line
            val tok = scanOperator()
            if (tok != null) return tok

            // Unknown character, skip
            index++
            column++
        }

        // Emit remaining DEDENTs at EOF
        while (indentStack.size > 1) {
            indentStack.removeAt(indentStack.size - 1)
            pendingTokens.add(Token(TokenType.DEDENT, "", line, column))
        }

        if (pendingTokens.isNotEmpty()) {
            return pendingTokens.removeAt(0)
        }

        return Token(TokenType.EOF, "", line, column)
    }

    private fun peek(offset: Int): Char {
        val target = index + offset
        return if (target in source.indices) source[target] else '\u0000'
    }

    private fun scanString(quoteChar: Char, isFString: Boolean): Token {
        val startLine = line
        val startCol = column
        val isTriple = peek(1) == quoteChar && peek(2) == quoteChar

        if (isTriple) {
            index += 3
            column += 3
        } else {
            index += 1
            column += 1
        }

        val sb = StringBuilder()
        while (index < source.length) {
            if (isTriple) {
                if (source[index] == quoteChar && peek(1) == quoteChar && peek(2) == quoteChar) {
                    index += 3
                    column += 3
                    return Token(
                        if (isFString) TokenType.FSTRING else TokenType.STRING,
                        sb.toString(),
                        startLine,
                        startCol
                    )
                }
            } else {
                if (source[index] == quoteChar) {
                    index += 1
                    column += 1
                    return Token(
                        if (isFString) TokenType.FSTRING else TokenType.STRING,
                        sb.toString(),
                        startLine,
                        startCol
                    )
                }
            }

            val ch = source[index]
            if (ch == '\\' && index + 1 < source.length) {
                index++
                column++
                when (source[index]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    '\'' -> sb.append('\'')
                    '"' -> sb.append('"')
                    else -> {
                        sb.append('\\')
                        sb.append(source[index])
                    }
                }
            } else {
                if (ch == '\n') {
                    line++
                    column = 0
                }
                sb.append(ch)
            }
            index++
            column++
        }

        return Token(
            if (isFString) TokenType.FSTRING else TokenType.STRING,
            sb.toString(),
            startLine,
            startCol
        )
    }

    private fun scanNumber(): Token {
        val startLine = line
        val startCol = column
        val sb = StringBuilder()

        // Hex or binary
        if (source[index] == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            sb.append("0x")
            index += 2
            column += 2
            while (index < source.length && (source[index].isDigit() || source[index] in "abcdefABCDEF_")) {
                if (source[index] != '_') sb.append(source[index])
                index++
                column++
            }
            val num = sb.toString().substring(2).toLongOrNull(16) ?: 0L
            return Token(TokenType.INT, num.toString(), startLine, startCol)
        }

        var isFloat = false
        while (index < source.length) {
            val c = source[index]
            if (c.isDigit()) {
                sb.append(c)
            } else if (c == '.' && !isFloat && peek(1).isDigit()) {
                isFloat = true
                sb.append(c)
            } else if ((c == 'e' || c == 'E') && !isFloat) {
                isFloat = true
                sb.append(c)
                if (peek(1) == '+' || peek(1) == '-') {
                    index++
                    column++
                    sb.append(source[index])
                }
            } else if (c == '_') {
                // Ignore numeric separator
            } else {
                break
            }
            index++
            column++
        }

        return Token(
            if (isFloat) TokenType.FLOAT else TokenType.INT,
            sb.toString(),
            startLine,
            startCol
        )
    }

    private fun scanIdentifierOrKeyword(): Token {
        val startLine = line
        val startCol = column
        val sb = StringBuilder()

        while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
            sb.append(source[index])
            index++
            column++
        }

        val word = sb.toString()
        val type = when (word) {
            "def" -> TokenType.DEF
            "class" -> TokenType.CLASS
            "if" -> TokenType.IF
            "elif" -> TokenType.ELIF
            "else" -> TokenType.ELSE
            "while" -> TokenType.WHILE
            "for" -> TokenType.FOR
            "in" -> TokenType.IN
            "return" -> TokenType.RETURN
            "break" -> TokenType.BREAK
            "continue" -> TokenType.CONTINUE
            "pass" -> TokenType.PASS
            "import" -> TokenType.IMPORT
            "from" -> TokenType.FROM
            "as" -> TokenType.AS
            "try" -> TokenType.TRY
            "except" -> TokenType.EXCEPT
            "finally" -> TokenType.FINALLY
            "raise" -> TokenType.RAISE
            "assert" -> TokenType.ASSERT
            "and" -> TokenType.AND
            "or" -> TokenType.OR
            "not" -> TokenType.NOT
            "is" -> TokenType.IS
            "lambda" -> TokenType.LAMBDA
            "True" -> TokenType.TRUE
            "False" -> TokenType.FALSE
            "None" -> TokenType.NONE
            else -> TokenType.IDENTIFIER
        }

        return Token(type, word, startLine, startCol)
    }

    private fun scanOperator(): Token? {
        val startLine = line
        val startCol = column
        val c = source[index]
        val c2 = peek(1)
        val c3 = peek(2)

        // 3-char operators
        if (c == '*' && c2 == '*' && c3 == '=') {
            index += 3; column += 3
            return Token(TokenType.DOUBLE_STAR, "**=", startLine, startCol)
        }
        if (c == '/' && c2 == '/' && c3 == '=') {
            index += 3; column += 3
            return Token(TokenType.DOUBLE_SLASH_ASSIGN, "//=", startLine, startCol)
        }

        // 2-char operators
        if (c == '=' && c2 == '=') { index += 2; column += 2; return Token(TokenType.EQUAL, "==", startLine, startCol) }
        if (c == '!' && c2 == '=') { index += 2; column += 2; return Token(TokenType.NOT_EQUAL, "!=", startLine, startCol) }
        if (c == '<' && c2 == '=') { index += 2; column += 2; return Token(TokenType.LESS_EQUAL, "<=", startLine, startCol) }
        if (c == '>' && c2 == '=') { index += 2; column += 2; return Token(TokenType.GREATER_EQUAL, ">=", startLine, startCol) }
        if (c == '+' && c2 == '=') { index += 2; column += 2; return Token(TokenType.PLUS_ASSIGN, "+=", startLine, startCol) }
        if (c == '-' && c2 == '=') { index += 2; column += 2; return Token(TokenType.MINUS_ASSIGN, "-=", startLine, startCol) }
        if (c == '*' && c2 == '=') { index += 2; column += 2; return Token(TokenType.STAR_ASSIGN, "*=", startLine, startCol) }
        if (c == '/' && c2 == '=') { index += 2; column += 2; return Token(TokenType.SLASH_ASSIGN, "/=", startLine, startCol) }
        if (c == '%' && c2 == '=') { index += 2; column += 2; return Token(TokenType.PERCENT_ASSIGN, "%=", startLine, startCol) }
        if (c == '*' && c2 == '*') { index += 2; column += 2; return Token(TokenType.DOUBLE_STAR, "**", startLine, startCol) }
        if (c == '/' && c2 == '/') { index += 2; column += 2; return Token(TokenType.DOUBLE_SLASH, "//", startLine, startCol) }
        if (c == '-' && c2 == '>') { index += 2; column += 2; return Token(TokenType.ARROW, "->", startLine, startCol) }
        if (c == '<' && c2 == '<') { index += 2; column += 2; return Token(TokenType.BIT_LSHIFT, "<<", startLine, startCol) }
        if (c == '>' && c2 == '>') { index += 2; column += 2; return Token(TokenType.BIT_RSHIFT, ">>", startLine, startCol) }

        // 1-char operators & delimiters
        index++; column++
        return when (c) {
            '+' -> Token(TokenType.PLUS, "+", startLine, startCol)
            '-' -> Token(TokenType.MINUS, "-", startLine, startCol)
            '*' -> Token(TokenType.STAR, "*", startLine, startCol)
            '/' -> Token(TokenType.SLASH, "/", startLine, startCol)
            '%' -> Token(TokenType.PERCENT, "%", startLine, startCol)
            '=' -> Token(TokenType.ASSIGN, "=", startLine, startCol)
            '<' -> Token(TokenType.LESS, "<", startLine, startCol)
            '>' -> Token(TokenType.GREATER, ">", startLine, startCol)
            '(' -> { bracketNesting++; Token(TokenType.LPAREN, "(", startLine, startCol) }
            ')' -> { if (bracketNesting > 0) bracketNesting--; Token(TokenType.RPAREN, ")", startLine, startCol) }
            '[' -> { bracketNesting++; Token(TokenType.LBRACKET, "[", startLine, startCol) }
            ']' -> { if (bracketNesting > 0) bracketNesting--; Token(TokenType.RBRACKET, "]", startLine, startCol) }
            '{' -> { bracketNesting++; Token(TokenType.LBRACE, "{", startLine, startCol) }
            '}' -> { if (bracketNesting > 0) bracketNesting--; Token(TokenType.RBRACE, "}", startLine, startCol) }
            ':' -> Token(TokenType.COLON, ":", startLine, startCol)
            ',' -> Token(TokenType.COMMA, ",", startLine, startCol)
            '.' -> Token(TokenType.DOT, ".", startLine, startCol)
            '&' -> Token(TokenType.BIT_AND, "&", startLine, startCol)
            '|' -> Token(TokenType.BIT_OR, "|", startLine, startCol)
            '^' -> Token(TokenType.BIT_XOR, "^", startLine, startCol)
            '~' -> Token(TokenType.BIT_NOT, "~", startLine, startCol)
            else -> null
        }
    }
}
