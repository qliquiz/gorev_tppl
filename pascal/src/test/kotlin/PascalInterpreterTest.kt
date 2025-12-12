package pascal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PascalInterpreterTest {
    private fun execute(code: String): Map<String, Double> {
        val lexer = Lexer(code)
        val parser = Parser(lexer)
        val interpreter = Interpreter(parser)
        return interpreter.interpret()
    }

    // ==========================================
    // Lexer coverage
    // ==========================================

    @Test
    fun `test Lexer all tokens`() {
        val code = "BEGIN x := 10 + 20 - 5 * 2 / ( 1 ); END."
        val lexer = Lexer(code)

        val expectedTokens = listOf(
            TokenType.BEGIN, TokenType.ID, TokenType.ASSIGN, TokenType.INTEGER,
            TokenType.PLUS, TokenType.INTEGER, TokenType.MINUS, TokenType.INTEGER,
            TokenType.MUL, TokenType.INTEGER, TokenType.DIV, TokenType.LPAREN,
            TokenType.INTEGER, TokenType.RPAREN, TokenType.SEMI, TokenType.END,
            TokenType.DOT, TokenType.EOF
        )

        for (type in expectedTokens) {
            assertEquals(type, lexer.getNextToken().type)
        }
    }

    @Test
    fun `test Lexer illegal character`() {
        val exception = assertThrows<IllegalArgumentException> {
            val lexer = Lexer("x := 10 & 20") // & недопустим
            lexer.getNextToken() // x
            lexer.getNextToken() // :=
            lexer.getNextToken() // 10
            lexer.getNextToken() // & -> Error
        }
        assertTrue(exception.message!!.contains("Unexpected character"))
    }

    @Test
    fun `test Lexer case insensitivity`() {
        val lexer = Lexer("BeGiN eNd Var")
        assertEquals(TokenType.BEGIN, lexer.getNextToken().type)
        assertEquals(TokenType.END, lexer.getNextToken().type)
        val varToken = lexer.getNextToken()
        assertEquals(TokenType.ID, varToken.type)
        assertEquals("var", varToken.value) // приводится к lowercase
    }

    // ==========================================
    // Parser coverage
    // ==========================================

    @Test
    fun `test Parser unexpected token`() {
        val exception = assertThrows<IllegalArgumentException> {
            execute("BEGIN x := 10 ) END.") // лишняя закрывающая скобка
        }
        assertTrue(exception.message!!.contains("Syntax error"))
    }

    @Test
    fun `test Parser bad factor`() {
        val exception = assertThrows<IllegalArgumentException> {
            execute("BEGIN x := * 2 END.") // выражение не может начинаться с *
        }
        assertTrue(exception.message!!.contains("Unexpected token in factor"))
    }

    @Test
    fun `test Parser empty statement`() {
        // Проверка пустых выражений (NoOp) и множественных точек с запятой
        val code = """
            BEGIN
               x := 1;;;
               y := 2
            END.
        """.trimIndent()
        val result = execute(code)
        assertEquals(1.0, result["x"])
        assertEquals(2.0, result["y"])
    }

    // ==========================================
    // Interpreter coverage
    // ==========================================

    @Test
    fun `test Arithmetic Operations`() {
        val code = """
            BEGIN
                add := 10 + 2;
                sub := 10 - 2;
                mul := 10 * 2;
                div := 10 / 2;
                complex := 2 + 3 * 4
            END.
        """
        val result = execute(code)
        assertEquals(12.0, result["add"])
        assertEquals(8.0, result["sub"])
        assertEquals(20.0, result["mul"])
        assertEquals(5.0, result["div"])
        assertEquals(14.0, result["complex"]) // приоритет операций (2 + 12)
    }

    @Test
    fun `test Unary Operators`() {
        val code = """
            BEGIN
                pos := +5;
                neg := -5;
                calc := 10 + -2
            END.
        """
        val result = execute(code)
        assertEquals(5.0, result["pos"])
        assertEquals(-5.0, result["neg"])
        assertEquals(8.0, result["calc"])
    }

    @Test
    fun `test Uninitialized Variable`() {
        val exception = assertThrows<IllegalArgumentException> {
            execute("BEGIN x := y + 1 END.") // y не определен
        }
        assertTrue(exception.message!!.contains("Variable 'y' not found"))
    }

    // ==========================================
    // Интеграционные тесты
    // ==========================================

    @Test
    fun `test Case 1 - Empty Program`() {
        val code = """
            BEGIN
            END.
        """
        val result = execute(code)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test Case 2 - Complex Arithmetic`() {
        val code = """
            BEGIN
                x:= 2 + 3 * (2 + 3);
                y:= 2 / 2 - 2 + 3 * ((1 + 1) + (1 + 1));
            END.
        """
        val result = execute(code)
        assertEquals(17.0, result["x"])
        assertEquals(11.0, result["y"])
    }

    @Test
    fun `test Case 3 - Nested Scopes`() {
        val code = """
            BEGIN
                y := 2;
                BEGIN
                    a := 3;
                    a := a;
                    b := 10 + a + 10 * y / 4;
                    c := a - b
                END;
                x := 11;
            END.
        """
        val result = execute(code)
        // b = 10 + 3 + (10*2/4) = 13 + 5 = 18
        // c = 3 - 18 = -15
        assertEquals(2.0, result["y"])
        assertEquals(3.0, result["a"])
        assertEquals(18.0, result["b"])
        assertEquals(-15.0, result["c"])
        assertEquals(11.0, result["x"])
    }

    // ==========================================
    // Coverage for Data Classes and unreachable branches
    // ==========================================

    @Test
    fun `test AST Data Classes`() {
        val token = Token(TokenType.INTEGER, "1")
        val numNode = Num(token)
        assertEquals("Token(type=INTEGER, value=1)", token.toString())
        assertNotNull(numNode)
    }

    @Test
    fun `test Unreachable Operator Exceptions`() {
        val parser = Parser(Lexer("BEGIN END."))
        val interpreter = Interpreter(parser)
        val badBinOp =
            BinOp(Num(Token(TokenType.INTEGER, "1")), Token(TokenType.EOF, ""), Num(Token(TokenType.INTEGER, "1")))
        val method = Interpreter::class.java.getDeclaredMethod("visit", AST::class.java)
        method.isAccessible = true

        val exceptionBin = assertThrows<java.lang.reflect.InvocationTargetException> {
            method.invoke(interpreter, badBinOp)
        }
        assertTrue(exceptionBin.cause is IllegalStateException)

        val badUnaryOp = UnaryOp(Token(TokenType.MUL, "*"), Num(Token(TokenType.INTEGER, "1")))
        val exceptionUnary = assertThrows<java.lang.reflect.InvocationTargetException> {
            method.invoke(interpreter, badUnaryOp)
        }
        assertTrue(exceptionUnary.cause is IllegalStateException)
    }
}
