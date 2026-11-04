package ai.gravityfield.gravity_sdk

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Small real HTTP server shared by headless contract tests. */
internal class HeadlessRecordingServer(private val respond: (String, JSONObject) -> Pair<Int, String>?) {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${serverSocket.localPort}"

    var observeRawBody: ((String) -> Unit)? = null

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
            val rawBody = String(bodyBytes, 0, read)
            observeRawBody?.invoke(rawBody)
            val body = runCatching { JSONObject(rawBody) }.getOrDefault(JSONObject())
            // A null response closes the connection without sending HTTP headers.
            val (status, answer) = respond(path, body) ?: return
            val bytes = answer.toByteArray()
            it.getOutputStream().apply {
                write(
                    ("HTTP/1.1 $status Response\r\n" +
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
