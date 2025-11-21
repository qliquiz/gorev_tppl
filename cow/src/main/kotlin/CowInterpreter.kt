import java.util.Stack

enum class CowOp(val opcode: String, val codeIndex: Int?) {
    LOOP_START("moo", 0),
    PREV_CELL("mOo", 1),
    NEXT_CELL("moO", 2),
    EXEC_BY_VAL("mOO", 3),
    READ_OR_PRINT("Moo", 4),
    DECREMENT("MOo", 5),
    LOOP_END("MOO", 6),
    INCREMENT("MoO", 7),
    ZERO_VAL("OOO", 8),
    PRINT_VAL("OOM", 9),
    READ_VAL("oom", 10),
    UNKNOWN("", null);

    companion object {
        private val mapByString = entries.associateBy { it.opcode }
        private val mapByIndex = entries.filter { it.codeIndex != null }.associateBy { it.codeIndex!! }

        fun fromString(s: String): CowOp = mapByString[s] ?: UNKNOWN
        fun fromIndex(i: Int): CowOp = mapByIndex[i] ?: UNKNOWN
    }
}

interface CowIO {
    fun read(): Int?
    fun print(value: Int)
}

class ConsoleIO : CowIO {
    override fun read(): Int? {
        val i = System.`in`.read()
        return if (i == -1) null else i
    }

    override fun print(value: Int) {
        kotlin.io.print(value.toChar())
    }
}

class CowInterpreter(
    private val io: CowIO = ConsoleIO(),
    private val memorySize: Int = 30000
) {
    private val memory = IntArray(memorySize)
    private var ptr = 0
    private var pc = 0

    fun execute(source: String) {
        memory.fill(0)
        ptr = 0
        pc = 0 // program counter

        val program = parse(source)
        if (program.isEmpty()) return

        val jumpTable = buildJumpTable(program)

        while (pc < program.size) {
            val op = program[pc]
            executeOp(op, jumpTable, program)
            pc++
        }
    }

    private fun executeOp(op: CowOp, jumpTable: Map<Int, Int>, program: List<CowOp>) {
        when (op) {
            CowOp.INCREMENT -> memory[ptr]++
            CowOp.DECREMENT -> memory[ptr]--
            CowOp.NEXT_CELL -> {
                ptr++
                if (ptr >= memorySize) throw IndexOutOfBoundsException("Memory pointer overflow")
            }

            CowOp.PREV_CELL -> {
                ptr--
                if (ptr < 0) throw IndexOutOfBoundsException("Memory pointer underflow")
            }

            CowOp.LOOP_START -> {
                if (memory[ptr] == 0)
                    pc = jumpTable[pc] ?: throw IllegalStateException("Unmatched 'moo' at $pc")
            }

            CowOp.LOOP_END -> {
                if (memory[ptr] != 0) {
                    val target = jumpTable[pc] ?: throw IllegalStateException("Unmatched 'MOO' at $pc")
                    pc = target - 1
                }
            }

            CowOp.ZERO_VAL -> memory[ptr] = 0
            CowOp.PRINT_VAL -> io.print(memory[ptr])
            CowOp.READ_VAL -> {
                val input = io.read()
                memory[ptr] = input ?: 0
            }

            CowOp.READ_OR_PRINT -> {
                if (memory[ptr] == 0) {
                    val input = io.read()
                    memory[ptr] = input ?: 0
                } else {
                    io.print(memory[ptr])
                }
            }

            CowOp.EXEC_BY_VAL -> {
                val code = memory[ptr]
                val opToExec = CowOp.fromIndex(code)

                if (opToExec != CowOp.LOOP_START && opToExec != CowOp.LOOP_END && opToExec != CowOp.UNKNOWN)
                    executeOp(opToExec, jumpTable, program)
            }

            else -> {}
        }
    }

    private fun parse(source: String): List<CowOp> {
        val ops = mutableListOf<CowOp>()
        var i = 0
        while (i <= source.length - 3) {
            val sub = source.substring(i, i + 3)
            val op = CowOp.fromString(sub)
            if (op != CowOp.UNKNOWN) {
                ops.add(op)
                i += 3
            } else {
                i++
            }
        }
        return ops
    }

    private fun buildJumpTable(program: List<CowOp>): Map<Int, Int> {
        val table = mutableMapOf<Int, Int>()
        val stack = Stack<Int>()

        for ((index, op) in program.withIndex()) {
            if (op == CowOp.LOOP_START) {
                stack.push(index)
            } else if (op == CowOp.LOOP_END) {
                if (stack.isEmpty())
                    throw IllegalArgumentException("Syntax Error: Unmatched 'MOO' at index $index")
                val start = stack.pop()
                table[start] = index // moo -> MOO
                table[index] = start // MOO -> moo
            }
        }

        if (stack.isNotEmpty())
            throw IllegalArgumentException("Syntax Error: Unmatched 'moo' at index ${stack.peek()}")

        return table
    }

    fun getMemorySnapshot(limit: Int = 10): List<Int> = memory.take(limit).toList()
}