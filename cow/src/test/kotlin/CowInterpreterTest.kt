import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CowInterpreterTest {
    private val originalIn = System.`in`
    private val originalOut = System.out

    @AfterEach
    fun restoreStreams() {
        System.setIn(originalIn)
        System.setOut(originalOut)
    }

    @Test
    fun `test ConsoleIO read and print`() {
        val inputData = "A"
        System.setIn(ByteArrayInputStream(inputData.toByteArray()))

        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        val consoleIO = ConsoleIO()

        val readByte = consoleIO.read()
        assertEquals('A'.code, readByte)

        consoleIO.print('B'.code)
        assertEquals("B", outContent.toString())
    }

    @Test
    fun `test ConsoleIO read EOF`() {
        System.setIn(ByteArrayInputStream(ByteArray(0)))
        val consoleIO = ConsoleIO()
        assertNull(consoleIO.read())
    }

    @Test
    fun `test CowOp lookups for unknown values`() {
        assertEquals(CowOp.UNKNOWN, CowOp.fromString("invalid_op"))
        assertEquals(CowOp.UNKNOWN, CowOp.fromString("   "))

        assertEquals(CowOp.UNKNOWN, CowOp.fromIndex(999))
        assertEquals(CowOp.UNKNOWN, CowOp.fromIndex(-1))
    }

    class MockIO(inputString: String = "") : CowIO {
        private val inputQueue = ArrayDeque<Int>()
        val outputStream = ByteArrayOutputStream()

        init {
            inputString.forEach { inputQueue.add(it.code) }
        }

        override fun read(): Int? = if (inputQueue.isEmpty()) null else inputQueue.removeFirst()
        override fun print(value: Int) {
            outputStream.write(value)
        }
    }

    @Test
    fun `test Empty Source Code`() {
        val interp = CowInterpreter(MockIO())
        interp.execute("")
        assertEquals(0, interp.getMemorySnapshot()[0])
    }

    @Test
    fun `test Memory Overflow (Right)`() {
        val interp = CowInterpreter(MockIO(), memorySize = 2)
        val code = "moO moO"
        assertThrows<IndexOutOfBoundsException> {
            interp.execute(code)
        }
    }

    @Test
    fun `test Memory Underflow (Left)`() {
        val interp = CowInterpreter(MockIO())
        val code = "mOo"
        assertThrows<IndexOutOfBoundsException> {
            interp.execute(code)
        }
    }

    @Test
    fun `test Syntax Error Unmatched LOOP_START (moo)`() {
        val interp = CowInterpreter(MockIO())
        val code = "moo"
        val exception = assertThrows<IllegalArgumentException> {
            interp.execute(code)
        }
        assertTrue(exception.message!!.contains("Unmatched 'moo'"))
    }

    @Test
    fun `test Syntax Error Unmatched LOOP_END (MOO)`() {
        val interp = CowInterpreter(MockIO())
        val code = "MOO"
        val exception = assertThrows<IllegalArgumentException> {
            interp.execute(code)
        }
        assertTrue(exception.message!!.contains("Unmatched 'MOO'"))
    }

    @Test
    fun `test Indirect Execution Safety (mOO)`() {
        val interp = CowInterpreter(MockIO())

        interp.execute("mOO")

        val io = MockIO("c")
        val interp2 = CowInterpreter(io)
        interp2.execute("oom mOO")
    }

    @Test
    fun `test else branch in READ_OR_PRINT`() {
        val io1 = MockIO("A")
        val interp1 = CowInterpreter(io1)
        interp1.execute("Moo")
        assertEquals(65, interp1.getMemorySnapshot()[0])

        val io2 = MockIO()
        val interp2 = CowInterpreter(io2)
        interp2.execute("MoO Moo")
        assertEquals(1, io2.outputStream.size())
    }

    @Test
    fun `test logic integration hello world letter H`() {
        val code =
            "MoO MoO MoO MoO MoO MoO MoO MoO " +             // Cell[0] = 8
                    "moo " +                                 // Loop start
                    "MOo " +                                 // Cell[0]--
                    "moO " +                                 // Move next
                    "MoO MoO MoO MoO MoO MoO MoO MoO MoO " + // Cell[1] += 9
                    "mOo " +                                 // Move prev
                    "MOO " +                                 // Loop end
                    "moO OOM"                                // Move next, Print

        val io = MockIO()
        CowInterpreter(io).execute(code)
        assertEquals("H", io.outputStream.toString())
    }
}
