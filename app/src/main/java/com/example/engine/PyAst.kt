package com.example.engine

sealed class Expression {
    data class Literal(val value: PyValue) : Expression()
    data class Identifier(val name: String) : Expression()
    data class BinaryOp(val left: Expression, val op: BinaryOperator, val right: Expression) : Expression()
    data class UnaryOp(val op: UnaryOperator, val expr: Expression) : Expression()
    data class FunctionCall(val target: Expression, val args: List<Expression>, val kwargs: Map<String, Expression> = emptyMap()) : Expression()
    data class IndexAccess(val target: Expression, val index: Expression) : Expression()
    data class SliceAccess(val target: Expression, val start: Expression?, val stop: Expression?, val step: Expression? = null) : Expression()
    data class AttributeAccess(val target: Expression, val attrName: String) : Expression()
    data class ListLiteral(val items: List<Expression>) : Expression()
    data class TupleLiteral(val items: List<Expression>) : Expression()
    data class DictLiteral(val entries: List<Pair<Expression, Expression>>) : Expression()
    data class SetLiteral(val items: List<Expression>) : Expression()
    data class Ternary(val condition: Expression, val trueExpr: Expression, val falseExpr: Expression) : Expression()
    data class Lambda(val params: List<String>, val body: Expression) : Expression()
    data class FormattedString(val parts: List<Expression>) : Expression()
    data class ListComprehension(val expr: Expression, val variables: List<String>, val iterable: Expression, val condition: Expression? = null) : Expression()
    data class DictComprehension(val keyExpr: Expression, val valExpr: Expression, val variables: List<String>, val iterable: Expression, val condition: Expression? = null) : Expression()
}

enum class BinaryOperator {
    ADD, SUBTRACT, MULTIPLY, DIVIDE, FLOOR_DIVIDE, MODULO, POWER,
    EQUAL, NOT_EQUAL, LESS_THAN, LESS_EQUAL, GREATER_THAN, GREATER_EQUAL,
    AND, OR, IN, NOT_IN, IS, IS_NOT,
    BIT_AND, BIT_OR, BIT_XOR, BIT_LSHIFT, BIT_RSHIFT
}

enum class UnaryOperator {
    NOT, NEGATE, POSITIVE, BIT_NOT
}

sealed class Statement {
    data class Assign(val targets: List<Expression>, val value: Expression) : Statement()
    data class AugmentedAssign(val target: Expression, val op: BinaryOperator, val value: Expression) : Statement()
    data class ExprStmt(val expr: Expression) : Statement()
    data class IfStmt(val branches: List<Pair<Expression, List<Statement>>>, val elseBranch: List<Statement>? = null) : Statement()
    data class WhileStmt(val condition: Expression, val body: List<Statement>, val elseBranch: List<Statement>? = null) : Statement()
    data class ForStmt(val variables: List<String>, val iterable: Expression, val body: List<Statement>, val elseBranch: List<Statement>? = null) : Statement()
    data class DefStmt(
        val name: String,
        val params: List<String>,
        val defaultParams: Map<String, Expression> = emptyMap(),
        val vararg: String? = null,
        val body: List<Statement>
    ) : Statement()
    data class ClassStmt(val name: String, val baseClass: String? = null, val body: List<Statement>) : Statement()
    data class ReturnStmt(val value: Expression?) : Statement()
    object BreakStmt : Statement()
    object ContinueStmt : Statement()
    object PassStmt : Statement()
    data class ImportStmt(val moduleName: String, val alias: String? = null, val specificImports: List<Pair<String, String?>> = emptyList()) : Statement()
    data class TryExceptStmt(
        val tryBody: List<Statement>,
        val exceptClauses: List<ExceptClause>,
        val finallyBody: List<Statement>? = null
    ) : Statement()
    data class RaiseStmt(val exceptionExpr: Expression?) : Statement()
    data class AssertStmt(val condition: Expression, val message: Expression? = null) : Statement()
}

data class ExceptClause(
    val exceptionType: String? = null,
    val exceptionVar: String? = null,
    val body: List<Statement>
)
