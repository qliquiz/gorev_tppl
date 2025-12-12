package pascal

import java.util.Locale

// ==================== Model ====================

enum class TokenType {
    INTEGER, PLUS, MINUS, MUL, DIV, LPAREN, RPAREN,
    BEGIN, END, DOT, SEMI, ASSIGN, ID, EOF
}

data class Token(val type: TokenType, val value: String)

sealed class AST
data class BinOp(val left: AST, val op: Token, val right: AST) : AST()
data class UnaryOp(val op: Token, val expr: AST) : AST()
data class Num(val token: Token) : AST()
data class Compound(val children: List<AST>) : AST()
data class Assign(val left: Var, val op: Token, val right: AST) : AST()
data class Var(val token: Token) : AST()
object NoOp : AST()

// ==================== Lexer ====================

class Lexer(private val text: String) {
    private var pos: Int = 0
    private var currentChar: Char? = if (text.isNotEmpty()) text[0] else null

    private fun advance() {
        pos++
        currentChar = if (pos < text.length) text[pos] else null
    }

    private fun peek(): Char? {
        val peekPos = pos + 1
        return if (peekPos < text.length) text[peekPos] else null
    }

    private fun skipWhitespace() {
        while (currentChar != null && currentChar!!.isWhitespace()) {
            advance()
        }
    }

    private fun integer(): String {
        val result = StringBuilder()
        while (currentChar != null && currentChar!!.isDigit()) {
            result.append(currentChar)
            advance()
        }
        return result.toString()
    }

    private fun identifier(): Token {
        val result = StringBuilder()
        while (currentChar != null && (currentChar!!.isLetterOrDigit() || currentChar == '_')) {
            result.append(currentChar)
            advance()
        }
        val str = result.toString()
        return when (str.uppercase(Locale.getDefault())) {
            "BEGIN" -> Token(TokenType.BEGIN, str)
            "END" -> Token(TokenType.END, str)
            else -> Token(TokenType.ID, str.lowercase(Locale.getDefault()))
        }
    }

    fun getNextToken(): Token {
        while (currentChar != null) {
            if (currentChar!!.isWhitespace()) {
                skipWhitespace()
                continue
            }

            if (currentChar!!.isDigit()) {
                return Token(TokenType.INTEGER, integer())
            }

            if (currentChar!!.isLetter()) {
                return identifier()
            }

            if (currentChar == ':' && peek() == '=') {
                advance()
                advance()
                return Token(TokenType.ASSIGN, ":=")
            }

            val token = when (currentChar) {
                '+' -> Token(TokenType.PLUS, "+")
                '-' -> Token(TokenType.MINUS, "-")
                '*' -> Token(TokenType.MUL, "*")
                '/' -> Token(TokenType.DIV, "/")
                '(' -> Token(TokenType.LPAREN, "(")
                ')' -> Token(TokenType.RPAREN, ")")
                ';' -> Token(TokenType.SEMI, ";")
                '.' -> Token(TokenType.DOT, ".")
                else -> throw IllegalArgumentException("Unexpected character: $currentChar")
            }
            advance()
            return token
        }
        return Token(TokenType.EOF, "")
    }
}

// ==================== Parser ====================

class Parser(private val lexer: Lexer) {
    private var currentToken: Token = lexer.getNextToken()

    private fun eat(type: TokenType) {
        if (currentToken.type == type) {
            currentToken = lexer.getNextToken()
        } else {
            throw IllegalArgumentException("Syntax error: Expected $type but found ${currentToken.type}")
        }
    }

    private fun factor(): AST {
        val token = currentToken
        return when (token.type) {
            TokenType.PLUS -> {
                eat(TokenType.PLUS)
                UnaryOp(token, factor())
            }

            TokenType.MINUS -> {
                eat(TokenType.MINUS)
                UnaryOp(token, factor())
            }

            TokenType.INTEGER -> {
                eat(TokenType.INTEGER)
                Num(token)
            }

            TokenType.LPAREN -> {
                eat(TokenType.LPAREN)
                val node = expr()
                eat(TokenType.RPAREN)
                node
            }

            TokenType.ID -> variable()
            else -> throw IllegalArgumentException("Syntax error: Unexpected token in factor: $token")
        }
    }

    private fun term(): AST {
        var node = factor()
        while (currentToken.type == TokenType.MUL || currentToken.type == TokenType.DIV) {
            val token = currentToken
            if (token.type == TokenType.MUL) eat(TokenType.MUL) else eat(TokenType.DIV)
            node = BinOp(node, token, factor())
        }
        return node
    }

    private fun expr(): AST {
        var node = term()
        while (currentToken.type == TokenType.PLUS || currentToken.type == TokenType.MINUS) {
            val token = currentToken
            if (token.type == TokenType.PLUS) eat(TokenType.PLUS) else eat(TokenType.MINUS)
            node = BinOp(node, token, term())
        }
        return node
    }

    private fun variable(): Var {
        val node = Var(currentToken)
        eat(TokenType.ID)
        return node
    }

    private fun assignmentStatement(): AST {
        val left = variable()
        val token = currentToken
        eat(TokenType.ASSIGN)
        val right = expr()
        return Assign(left, token, right)
    }

    private fun statement(): AST {
        return when (currentToken.type) {
            TokenType.BEGIN -> compoundStatement()
            TokenType.ID -> assignmentStatement()
            else -> NoOp
        }
    }

    private fun statementList(): List<AST> {
        val results = mutableListOf<AST>()
        results.add(statement())
        while (currentToken.type == TokenType.SEMI) {
            eat(TokenType.SEMI)
            results.add(statement())
        }
        return results
    }

    private fun compoundStatement(): AST {
        eat(TokenType.BEGIN)
        val nodes = statementList()
        eat(TokenType.END)
        return Compound(nodes)
    }

    fun parse(): AST {
        val node = compoundStatement()
        eat(TokenType.DOT)
        return node
    }
}

// ==================== Interpreter ====================

class Interpreter(private val parser: Parser) {
    val variables = mutableMapOf<String, Double>()

    fun interpret(): Map<String, Double> {
        val tree = parser.parse()
        visit(tree)
        return variables
    }

    private fun visit(node: AST): Any? {
        return when (node) {
            is BinOp -> visitBinOp(node)
            is UnaryOp -> visitUnaryOp(node)
            is Num -> visitNum(node)
            is Compound -> visitCompound(node)
            is Assign -> visitAssign(node)
            is Var -> visitVar(node)
            is NoOp -> null
        }
    }

    private fun visitCompound(node: Compound) {
        for (child in node.children) {
            visit(child)
        }
    }

    private fun visitAssign(node: Assign) {
        val varName = node.left.token.value
        val value = visit(node.right) as Double
        variables[varName] = value
    }

    private fun visitVar(node: Var): Double {
        val varName = node.token.value
        return variables[varName] ?: throw IllegalArgumentException("Runtime error: Variable '$varName' not found")
    }

    private fun visitBinOp(node: BinOp): Double {
        val left = visit(node.left) as Double
        val right = visit(node.right) as Double

        return when (node.op.type) {
            TokenType.PLUS -> left + right
            TokenType.MINUS -> left - right
            TokenType.MUL -> left * right
            TokenType.DIV -> left / right
            else -> throw IllegalStateException("Unknown operator")
        }
    }

    private fun visitUnaryOp(node: UnaryOp): Double {
        val exprVal = visit(node.expr) as Double
        return when (node.op.type) {
            TokenType.PLUS -> exprVal
            TokenType.MINUS -> -exprVal
            else -> throw IllegalStateException("Unknown unary operator")
        }
    }

    private fun visitNum(node: Num): Double {
        return node.token.value.toDouble()
    }
}
