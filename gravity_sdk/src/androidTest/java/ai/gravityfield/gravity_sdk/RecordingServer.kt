package ai.gravityfield.gravity_sdk

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Local HTTP server for wire-level tests: records every request body and
 * answers /visit and /event with [campaigns], everything else with an empty
 * content list. Every answer carries [responseUser].
 */
internal class RecordingServer {
    data class Request(val path: String, val body: JSONObject)

    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val requests = CopyOnWriteArrayList<Request>()

    @Volatile
    var campaigns: JSONArray = JSONArray()

    @Volatile
    var responseUser: JSONObject = JSONObject(mapOf("uid" to "server-uid", "ses" to "server-ses"))

    val url = "http://127.0.0.1:${serverSocket.localPort}"

    val recorded: List<Request> get() = requests.toList()

    fun start() {
        thread(name = "RecordingServer", isDaemon = true) {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) { handle(socket) }
            }
        }
    }

    fun stop() {
        serverSocket.close()
    }

    fun reset() {
        requests.clear()
        campaigns = JSONArray()
        responseUser = JSONObject(mapOf("uid" to "server-uid", "ses" to "server-ses"))
    }

    /** Waits until at least [count] requests arrived (async SDK flows). */
    fun waitFor(count: Int, timeoutMs: Long = 5_000): List<Request> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (requests.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        return recorded
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = 5_000
            val input = BufferedInputStream(it.getInputStream())
            val requestLine = readLine(input)
            var contentLength = 0
            while (true) {
                val header = readLine(input)
                if (header.isEmpty()) break
                val parts = header.split(":", limit = 2)
                if (parts[0].trim().equals("Content-Length", ignoreCase = true)) {
                    contentLength = parts.getOrElse(1) { "" }.trim().toIntOrNull() ?: 0
                }
            }
            val bodyBytes = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(bodyBytes, read, contentLength - read)
                if (n == -1) break
                read += n
            }
            val path = requestLine.split(" ").getOrElse(1) { "" }
            val body = runCatching { JSONObject(String(bodyBytes, 0, read)) }.getOrDefault(JSONObject())
            requests.add(Request(path, body))

            val answer = JSONObject().put("user", responseUser)
            if (path.endsWith("/visit") || path.endsWith("/event")) {
                answer.put("campaigns", campaigns)
            } else {
                answer.put("data", JSONArray())
            }
            val bytes = answer.toString().toByteArray()
            it.getOutputStream().apply {
                write(
                    ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                )
                write(bytes)
                flush()
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }
}
