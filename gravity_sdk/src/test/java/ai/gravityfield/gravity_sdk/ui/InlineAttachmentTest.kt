package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.R
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class InlineAttachmentTest {
    @Test fun viewReattachmentKeepsInFlightChoose() = checkAttachment(false, false)
    @Test fun listReattachmentKeepsInFlightChoose() = checkAttachment(true, false)
    @Test fun detachedViewWaitsToReloadChangedIdentity() = checkAttachment(false, true)
    @Test fun detachedListWaitsToReloadChangedIdentity() = checkAttachment(true, true)

    @Test fun viewOwnerDestroyCancelsDetachedRetry() = checkAttachment(false, true, true)
    @Test fun listOwnerDestroyCancelsDetachedRetry() = checkAttachment(true, true, true)

    private fun checkAttachment(isList: Boolean, changeUser: Boolean, destroyDetached: Boolean = false) = runBlocking<Unit> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val server = GatedServer()
        var sdk: GravitySDK? = null
        var destroyed = false
        suspend fun pump() {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            clock.sendFrame(System.nanoTime())
            shadowOf(Looper.getMainLooper()).idle()
            delay(5)
        }
        suspend fun settle() { repeat(40) { pump() } }
        try {
            GravitySDK.initialize(activity.get(), "attachment-key", "attachment-section", {})
            sdk = GravitySDK.instance
            sdk.setOptions(null, null, server.url)
            sdk.restoreUserId("original")
            val parent = FrameLayout(activity.get())
            activity.get().setContentView(parent)
            val attributes = Robolectric.buildAttributeSet()
                .addAttribute(if (isList) R.attr.groupSelector else R.attr.selector, "\"slot\"")
                .build()
            val page = PageContext(ContextType.OTHER, emptyList(), "attachment")
            val view: View = if (isList) GravityInlineListView(activity.get(), attributes).apply {
                setParentCompositionContext(recomposer); init(page)
            } else GravityInlineView(activity.get(), attributes).apply {
                setParentCompositionContext(recomposer); init(page)
            }
            parent.addView(view, ViewGroup.LayoutParams(300, 120))
            activity.visible()
            withTimeout(5000) { while (server.requests.get() != 1) pump() }
            parent.removeView(view)
            settle()
            if (changeUser) {
                sdk.restoreUserId("restored")
                server.release.countDown()
                settle()
                assertEquals("Detached placements must not retry a stale response", 1, server.requests.get())
                assertEquals("restored", sdk.getUserId())
            }
            if (destroyDetached) {
                assertTrue("Detach must retain the waiting placement", (view as AbstractComposeView).hasComposition)
                activity.pause().stop().destroy()
                destroyed = true
                settle()
                assertFalse("Destroying the owner must dispose retained work", view.hasComposition)
                assertEquals(1, server.requests.get())
                return@runBlocking
            }
            parent.addView(view, ViewGroup.LayoutParams(300, 120))
            settle()
            if (!changeUser) {
                repeat(2) {
                    parent.removeView(view)
                    settle()
                    parent.addView(view, ViewGroup.LayoutParams(300, 120))
                    settle()
                }
                server.release.countDown()
            }
            withTimeout(5000) {
                while (server.finished.get() < if (changeUser) 2 else 1) pump()
            }
            settle()
            assertEquals("A temporary detach must retain the original choose", if (changeUser) 2 else 1, server.requests.get())
            assertEquals(if (changeUser) "restored" else "original", sdk.getUserId())
        } finally {
            server.release.countDown()
            sdk?.dispose()
            if (!destroyed) activity.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
            server.socket.close()
        }
    }

    private class GatedServer {
        val socket = ServerSocket(0)
        val url = "http://127.0.0.1:${socket.localPort}"
        val requests = AtomicInteger()
        val finished = AtomicInteger()
        val release = CountDownLatch(1)
        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val connection = try { socket.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) {
                        try {
                            connection.use {
                                it.soTimeout = 5000
                                val input = it.getInputStream().buffered()
                                val headers = buildString {
                                    while (!endsWith("\r\n\r\n")) {
                                        val next = input.read(); check(next >= 0); append(next.toChar())
                                    }
                                }
                                val length = headers.lineSequence().first { line -> line.startsWith("Content-Length:", true) }
                                    .substringAfter(':').trim().toInt()
                                val body = org.json.JSONObject(input.readNBytes(length).decodeToString())
                                val uid = body.getJSONObject("user").getString("uid")
                                val index = requests.incrementAndGet()
                                if (index == 1) check(release.await(5, TimeUnit.SECONDS))
                                val response = """{"user":{"uid":"$uid","ses":"session"},"data":[]}""".toByteArray()
                                it.getOutputStream().apply {
                                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    write(response); flush()
                                }
                            }
                        } catch (_: Exception) {
                            // A cancelled pre-fix request closes the peer; the count still records it.
                        } finally { finished.incrementAndGet() }
                    }
                }
            }
        }
    }
}
