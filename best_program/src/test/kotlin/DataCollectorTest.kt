import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class DataCollectorTest {
    @BeforeEach
    fun setup() {
        isRunning.set(true)
        linesWritten.set(0)
    }

    @AfterEach
    fun tearDown() {
        isRunning.set(false)
    }

    @Test
    fun `test verifyChecksum`() {
        assertTrue(verifyChecksum(byteArrayOf(1, 2, 3)))
        assertFalse(verifyChecksum(byteArrayOf(1, 2, 5)))
        assertTrue(verifyChecksum(byteArrayOf(200.toByte(), 200.toByte(), 144.toByte())))
    }

    @Test
    fun `test formatTime`() {
        val t = formatTime(1672531200000000L)
        assertTrue(t.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `test parsers`() {
        // 5123
        val b1 = ByteBuffer.allocate(15).putLong(10).putFloat(1.1f).putShort(5).put(0).array()
        val s1 = parsePort5123(b1)
        assertTrue(s1.contains("5123") && (s1.contains("1,10") || s1.contains("1.10")))

        // 5124
        val b2 = ByteBuffer.allocate(21).putLong(10).putInt(1).putInt(2).putInt(3).put(0).array()
        val s2 = parsePort5124(b2)
        assertTrue(s2.contains("5124") && s2.contains("X: 1"))
    }

    @Test
    fun `test fileWriter`(@TempDir tempDir: File) = runTest {
        val file = File(tempDir, "test.txt")
        val ch = Channel<String>(10)
        ch.send("ABC")
        ch.close()
        fileWriter(ch, file)

        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertEquals("ABC", lines.first())

        val badCh = Channel<String>(10)
        badCh.send("FAIL")
        badCh.close()
        assertDoesNotThrow {
            runBlocking { fileWriter(badCh, tempDir) }
        }
    }

    @Test
    fun `test runClient with Bad CRC`() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val ch = Channel<String>(10)

        // эмулятор сервака
        launch(Dispatchers.IO) {
            try {
                val socket = server.accept()
                val inp = DataInputStream(socket.getInputStream())
                val out = DataOutputStream(socket.getOutputStream())

                inp.readNBytes(6) // key
                out.write("granted".toByteArray(StandardCharsets.US_ASCII))
                out.flush()

                inp.readNBytes(3) // get
                out.write(ByteArray(15) { 1 }) // bad data
                out.flush()

                delay(100) // даем клиенту прочитать
                socket.close()
            } catch (_: Exception) {
            }
        }

        val clientJob = launch(Dispatchers.IO) {
            runClient(port, 15, ch, { parsePort5123(it) }, "127.0.0.1")
        }

        delay(1000) // ждём реальное время
        assertTrue(ch.isEmpty, "Данные с плохим CRC не должны попасть в канал")

        isRunning.set(false)
        server.close()
        clientJob.cancelAndJoin()
    }

    @Test
    fun `test runClient Auth Failure`() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val ch = Channel<String>()

        launch(Dispatchers.IO) {
            try {
                val socket = server.accept()
                socket.getInputStream().readNBytes(6)
                socket.close() // закрываем без ответа granted
            } catch (_: Exception) {
            }
        }

        val job = launch(Dispatchers.IO) {
            runClient(port, 15, ch, { "" }, "127.0.0.1")
        }

        delay(1000)
        assertTrue(job.isActive, "Клиент должен продолжать работу (реконнект), а не падать")

        isRunning.set(false)
        job.cancelAndJoin()
        server.close()
    }

    @Test
    fun `test runClient Connection Refused`() = runBlocking {
        val unusedPort = 54321
        val ch = Channel<String>()

        val job = launch(Dispatchers.IO) {
            runClient(unusedPort, 15, ch, { "" }, "127.0.0.1")
        }

        delay(500)
        assertTrue(job.isActive)
        isRunning.set(false)
        job.cancelAndJoin()
    }

    @Test
    fun `test runApplication full flow`(@TempDir tempDir: File) = runBlocking {
        val outFile = File(tempDir, "full_out.txt")
        val stopSignal = CompletableDeferred<Unit>()

        val s1 = ServerSocket(0)
        val s2 = ServerSocket(0)
        val p1 = s1.localPort
        val p2 = s2.localPort

        val mocksJob = launch(Dispatchers.IO) {
            handleMockServer(s1, 15)
            handleMockServer(s2, 21)
        }

        val appJob = launch(Dispatchers.IO) {
            runApplication(outFile, stopSignal, "127.0.0.1", p1, p2)
        }

        delay(3000)

        stopSignal.complete(Unit)
        appJob.join()

        isRunning.set(false)
        s1.close()
        s2.close()
        mocksJob.cancelAndJoin()

        assertTrue(outFile.exists())
        val lines = outFile.readLines()
        assertTrue(lines.isNotEmpty(), "Файл пуст, данные не успели записаться.")
        println("Записано строк в тесте: ${lines.size}")
    }

    private suspend fun handleMockServer(server: ServerSocket, size: Int) = coroutineScope {
        launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val socket = server.accept()
                    socket.use {
                        val inp = DataInputStream(it.getInputStream())
                        val out = DataOutputStream(it.getOutputStream())

                        inp.readNBytes(6)
                        out.write("granted".toByteArray(StandardCharsets.US_ASCII))
                        out.flush()

                        while (isActive && !it.isClosed) {
                            try {
                                inp.readNBytes(3)
                                val bytes = ByteArray(size)
                                bytes[size - 1] = 0
                                out.write(bytes)
                                out.flush()
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}