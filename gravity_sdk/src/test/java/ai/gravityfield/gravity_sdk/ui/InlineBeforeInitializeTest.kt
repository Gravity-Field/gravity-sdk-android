package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.R
import ai.gravityfield.gravity_sdk.UserIdTestProcess
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.utils.LogLevel
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class InlineBeforeInitializeTest {
    @Test fun viewProvidedPageContextBeforeInitializeRequestsContentAfterInitialize() = runBlocking<Unit> {
        assertViewWaitsForInitialize(isList = false)
    }

    @Test fun listViewProvidedPageContextBeforeInitializeRequestsContentAfterInitialize() = runBlocking<Unit> {
        assertViewWaitsForInitialize(isList = true)
    }

    private suspend fun assertViewWaitsForInitialize(isList: Boolean) {
        UserIdTestProcess.start()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val server = ContentServer()
        val frames = UiFrames()
        var sdk: GravitySDK? = null
        try {
            val attributes = Robolectric.buildAttributeSet()
                .addAttribute(if (isList) R.attr.groupSelector else R.attr.selector, "\"slot\"")
                .build()
            val page = PageContext(ContextType.OTHER, emptyList(), "before-initialize")
            val view: View = if (isList) {
                GravityInlineListView(activity.get(), attributes).apply {
                    setParentCompositionContext(frames.recomposer)
                    init(page)
                }
            } else {
                GravityInlineView(activity.get(), attributes).apply {
                    setParentCompositionContext(frames.recomposer)
                    init(page)
                }
            }
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            activity.get().setContentView(view, view.layoutParams)
            activity.visible()
            repeat(30) {
                frames.pump()
            }
            assertFalse(GravitySDK.isInitialized)
            assertFalse(server.request.isCompleted)
            assertEquals("A waiting placement must not collapse before initialization", 120, view.layoutParams.height)

            GravitySDK.initialize(activity.get(), "test-key", "test-section", {}, logLevel = LogLevel.DEBUG)
            sdk = GravitySDK.instance
            sdk.setOptions(null, null, server.url)
            val completed = withTimeoutOrNull(5000) {
                while (!server.request.isCompleted || sdk.getUserId() != "inline-user") {
                    frames.pump()
                }
                val request = server.request.await()
                assertTrue(request.line.startsWith("POST /choose "))
                val data = request.body.getJSONArray("data").getJSONObject(0)
                assertEquals("slot", data.getString(if (isList) "group" else "selector"))
                assertEquals("before-initialize", request.body.getJSONObject("ctx").getString("location"))
                server.finished.await()
                true
            }
            if (completed != true) {
                fail("Placement did not finish: " + org.robolectric.shadows.ShadowLog.getLogs()
                    .joinToString { it.msg + ": " + it.throwable })
            }
        } finally {
            sdk?.dispose()
            sdk?.let { UserIdTestProcess.stop(it) }
            activity.pause().stop().destroy()
            frames.close()
            server.close()
        }
    }

    @Test fun composedBeforeInitializeKeepsLoadingThenRequestsContent() = runBlocking<Unit> {
        // Keep storage uninitialized too. Discovering all Startup components again in a reused
        // Robolectric sandbox would duplicate ProcessLifecycleOwner's activity callbacks.
        UserIdTestProcess.start()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val server = ContentServer()
        val frames = UiFrames()
        val request = server.request
        var sdk: GravitySDK? = null
        var loaderVisible = false
        try {
            val view = ComposeView(activity.get()).apply {
                setParentCompositionContext(frames.recomposer)
                setContent {
                    GravityInlineCompose(
                        Modifier, "\"slot\"", PageContext(ContextType.OTHER, emptyList(), "startup"),
                        loader = {
                            DisposableEffect(Unit) {
                                loaderVisible = true
                                onDispose { loaderVisible = false }
                            }
                        },
                    )
                }
            }
            activity.get().setContentView(view)
            activity.visible()
            repeat(30) {
                frames.pump()
            }
            assertTrue("Inline must keep its loader while waiting for SDK initialization", loaderVisible)
            assertFalse(request.isCompleted)

            GravitySDK.initialize(activity.get(), "test-key", "test-section", {})
            sdk = GravitySDK.instance
            sdk.setOptions(null, null, server.url)
            withTimeout(5000) {
                while (!request.isCompleted || loaderVisible) {
                    frames.pump()
                }
                assertTrue(request.await().line.startsWith("POST /choose "))
                server.finished.await()
            }
        } finally {
            sdk?.dispose()
            sdk?.let { UserIdTestProcess.stop(it) }
            activity.pause().stop().destroy()
            frames.close()
            server.close()
        }
    }

    /** Each test owns its recomposer; AndroidUiDispatcher.Main caches a Choreographer across tests. */
    private class UiFrames : Closeable {
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        val recomposer = Recomposer(scope.coroutineContext)

        init { scope.launch { recomposer.runRecomposeAndApplyChanges() } }

        suspend fun pump() {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            clock.sendFrame(System.nanoTime())
            shadowOf(Looper.getMainLooper()).idle()
            delay(5)
        }

        override fun close() {
            recomposer.cancel()
            scope.cancel()
        }
    }

    private data class Request(val line: String, val body: JSONObject)

    private class ContentServer : Closeable {
        private val socket = ServerSocket(0)
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${socket.localPort}"
        val request = CompletableDeferred<Request>()
        val finished = CompletableDeferred<Unit>()

        init {
            executor.submit {
                try {
                    socket.accept().use { connection ->
                        connection.soTimeout = 5000
                        val input = connection.getInputStream().buffered()
                        val headers = buildString {
                            while (!endsWith("\r\n\r\n")) {
                                val next = input.read()
                                check(next >= 0) { "incomplete headers" }
                                append(next.toChar())
                            }
                        }
                        val length = headers.lineSequence()
                            .first { it.startsWith("Content-Length:", true) }
                            .substringAfter(':').trim().toInt()
                        request.complete(Request(
                            headers.lineSequence().first(),
                            JSONObject(input.readNBytes(length).decodeToString()),
                        ))
                        val body = """{"user":{"uid":"inline-user","ses":"inline-session"},"data":[]}""".toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                    finished.complete(Unit)
                } catch (error: Exception) {
                    request.completeExceptionally(error)
                    finished.completeExceptionally(error)
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }
    }
}
