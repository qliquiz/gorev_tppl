import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Conf */
const val HOST = "95.163.237.76"
const val PORT_1 = 5123
const val PORT_2 = 5124
const val KEY = "isu_pt"
const val CMD_GET = "get"
const val OUTPUT_FILE = "sensor_data.txt"
const val REQUEST_DELAY_MS = 25L

val isRunning = AtomicBoolean(true)
val linesWritten = AtomicLong(0)

val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

fun main() = runBlocking {
    val stopSignal = async(Dispatchers.IO) {
        readlnOrNull()
        Unit
    }

    runApplication(
        outputFile = File(OUTPUT_FILE), stopSignal = stopSignal
    )
}

suspend fun runApplication(
    outputFile: File, stopSignal: Deferred<Unit>, host: String = HOST, port1: Int = PORT_1, port2: Int = PORT_2
) = coroutineScope {
    println("Сервер: $host")
    println("Порты: $port1, $port2")
    println("Файл: ${outputFile.absolutePath}")
    println("Нажмите Enter для корректной остановки...")

    val dataChannel = Channel<String>(capacity = 10000)

    val writerJob = launch(Dispatchers.IO) {
        fileWriter(dataChannel, outputFile)
    }

    val client1Job = launch(Dispatchers.IO) {
        runClient(port1, 15, dataChannel, { buf -> parsePort5123(buf) }, host)
    }

    val client2Job = launch(Dispatchers.IO) {
        runClient(port2, 21, dataChannel, { buf -> parsePort5124(buf) }, host)
    }

    stopSignal.await()

    println("\nЗавершение работы...")
    isRunning.set(false)

    client1Job.cancelAndJoin()
    client2Job.cancelAndJoin()
    dataChannel.close()
    writerJob.join()

    println("Итого записано строк: ${linesWritten.get()}")
    println("Данные лежат в: ${outputFile.name}")
}

suspend fun runClient(
    port: Int, packetSize: Int, channel: Channel<String>, parser: (ByteArray) -> String, host: String = HOST
) {
    val buffer = ByteArray(packetSize)
    val greetingBuffer = ByteArray(7)

    val keyBytes = KEY.toByteArray(StandardCharsets.US_ASCII)
    val getBytes = CMD_GET.toByteArray(StandardCharsets.US_ASCII)

    while (isRunning.get()) {
        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 10000
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                output.write(keyBytes)
                output.flush()

                delay(500)

                try {
                    input.readFully(greetingBuffer)
                    println("Порт $port: Соединение стабильно.")
                } catch (_: Exception) {
                    println("Порт $port: Сбой авторизации (не пришел granted). Повтор...")
                    return@use
                }

                while (isRunning.get() && socket.isConnected) {
                    try {
                        output.write(getBytes)
                        output.flush()

                        input.readFully(buffer)

                        if (verifyChecksum(buffer)) channel.send(parser(buffer))

                        delay(REQUEST_DELAY_MS)
                    } catch (_: java.io.EOFException) {
                        println("Порт $port: Сервер разорвал сессию. Переподключение...")
                        break
                    }
                }
            }
        } catch (_: Exception) {
            if (isRunning.get()) delay(2000)
        }
    }
}

suspend fun fileWriter(channel: Channel<String>, file: File) {
    try {
        file.printWriter().use { writer ->
            for (line in channel) {
                writer.println(line)
                val count = linesWritten.incrementAndGet()
                if (count % 100 == 0L) print("\rВсего собрано строк: $count")
            }
        }
    } catch (e: Exception) {
        System.err.println("\nОшибка записи файла: ${e.message}")
    }
}

fun verifyChecksum(data: ByteArray): Boolean {
    var sum = 0
    for (i in 0 until data.size - 1) {
        sum += (data[i].toInt() and 0xFF)
    }
    return (sum % 256).toByte() == data.last()
}

fun parsePort5123(data: ByteArray): String {
    val bb = ByteBuffer.wrap(data)
    val timeMicros = bb.long
    val temp = bb.float
    val pressure = bb.short.toInt()
    return "${formatTime(timeMicros)} | 5123 | Temp: %.2f | Press: $pressure".format(temp)
}

fun parsePort5124(data: ByteArray): String {
    val bb = ByteBuffer.wrap(data)
    val timeMicros = bb.long
    val x = bb.int
    val y = bb.int
    val z = bb.int
    return "${formatTime(timeMicros)} | 5124 | X: $x | Y: $y | Z: $z"
}

fun formatTime(micros: Long): String {
    val seconds = micros / 1_000_000
    val nanos = (micros % 1_000_000) * 1000
    val instant = Instant.ofEpochSecond(seconds, nanos)
    return timeFormatter.format(instant)
}