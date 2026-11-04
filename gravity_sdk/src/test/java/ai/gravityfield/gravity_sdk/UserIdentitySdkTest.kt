package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.internal.InlineViewCache
import ai.gravityfield.gravity_sdk.models.internal.ScrollPosition
import ai.gravityfield.gravity_sdk.network.GravityRepository
import android.os.Looper
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class UserIdentitySdkTest {
    private lateinit var sdk: GravitySDK
    @Before fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        GravitySDK.initialize(context, "test-key", "test-section", {})
        sdk = GravitySDK.instance
        runBlocking { sdk.resetUser() }
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun dispose() { sdk.setUserIdListener(null); sdk.dispose() }

    @Test fun invalidRestorePreservesUserAndInlineCaches() = runBlocking<Unit> {
        sdk.restoreUserId("known")
        val page = PageContext(ContextType.OTHER, emptyList(), "test")
        val identity = sdk.userIdentitySnapshot()
        val cache = InlineViewCache(scrollPosition = ScrollPosition(3, 42))
        sdk.putInlineViewCache("slot", page, cache, identity)
        sdk.putInlineListViewCache("group", page, cache, identity)
        val presentation = sdk.presentationGate.beginRequest(page.location, false)

        for (uid in listOf("  \t\n", "")) {
            try {
                sdk.restoreUserId(uid)
                fail("invalid uid accepted")
            } catch (_: IllegalArgumentException) { }

            assertEquals("known", sdk.getUserId())
            assertSame(cache, sdk.getInlineViewCache("slot", page))
            assertSame(cache, sdk.getInlineListViewCache("group", page))
            assertTrue(sdk.presentationGate.isCurrent(presentation))
        }
    }

    @Test fun restoredUidIsTrimmedInGetterAndChooseRequest() = runBlocking<Unit> {
        val captured = CompletableDeferred<Pair<String, JSONObject>>()
        val executor = Executors.newSingleThreadExecutor()
        val server = ServerSocket(0)
        executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().buffered()
                val headers = buildString {
                    while (!endsWith("\r\n\r\n")) {
                        val next = input.read()
                        check(next >= 0) { "request ended before headers" }
                        append(next.toChar())
                    }
                }
                val contentLength = headers.lineSequence()
                    .first { it.startsWith("Content-Length:", ignoreCase = true) }
                    .substringAfter(":").trim().toInt()
                val request = JSONObject(input.readNBytes(contentLength).decodeToString())
                captured.complete(headers.lineSequence().first() to request)
                val body = """{"user":{"uid":"restored","ses":"session"},"data":[]}""".toByteArray()
                val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(response.toByteArray()); write(body); flush() }
            }
        }
        sdk.setOptions(null, null, "http://127.0.0.1:${server.localPort}")
        try {
            sdk.restoreUserId("  restored \t\n")
            assertEquals("restored", sdk.getUserId())
            val response = withTimeout(10000) {
                sdk.getContentBySelectorWithDetails("\"slot\"", PageContext(ContextType.OTHER, emptyList(), "test"))
            }
            assertNotNull(response)
            val (requestLine, request) = withTimeout(1000) { captured.await() }
            assertTrue(requestLine.startsWith("POST /choose "))
            val user = request.getJSONObject("user")
            assertEquals("restored", user.getString("uid"))
            assertTrue(user.isNull("ses"))
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    @Test fun listenerUsesMainThreadAndSurvivesDisposeAndReinitialize() {
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            heard.add(it)
        }
        runBlocking {
            sdk.restoreUserId("server")
            assertEquals("server", sdk.getUserId())
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("server"), heard)
        runBlocking { sdk.restoreUserId("server") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("server"), heard)
        runBlocking { sdk.restoreUserId("queued-before-dispose") }
        sdk.dispose()
        runBlocking { GravityRepository.sessionManager.restoreUserId("after-dispose") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("server", "queued-before-dispose", "after-dispose"), heard)

        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "test-key", "test-section", {})
        sdk = GravitySDK.instance
        runBlocking { sdk.restoreUserId("reinitialized") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("reinitialized", heard.last())

        val previous = sdk
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "test-key", "test-section", {})
        sdk = GravitySDK.instance
        previous.dispose()
        runBlocking { sdk.restoreUserId("initialized-again") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("server", "queued-before-dispose", "after-dispose", "reinitialized", "initialized-again"), heard)

        sdk.setUserIdListener(null)
        runBlocking { sdk.restoreUserId("removed") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("initialized-again", heard.last())
    }

    @Test fun everyGetterDiscardsResponseAcrossReinitializeWithOrWithoutCustomUser() = runBlocking<Unit> {
        val page = PageContext(ContextType.OTHER, emptyList(), "reinitialize")
        for (custom in listOf(false, true)) for (kind in 0..4) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val server = ServerSocket(0)
            val executor = Executors.newSingleThreadExecutor()
            executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().buffered()
                    val headers = buildString {
                        while (!endsWith("\r\n\r\n")) { val next = input.read(); check(next >= 0); append(next.toChar()) }
                    }
                    val length = headers.lineSequence().first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    input.readNBytes(length)
                    entered.complete(Unit)
                    runBlocking { release.await() }
                    val body = """{"user":{"uid":"assigned","ses":"session"},"data":[{"payload":[{"campaignId":"1","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"obsolete","contentType":"html"}]}]}]}""".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body); flush()
                    }
                }
            }
            try {
                sdk.setOptions(null, null, "http://127.0.0.1:${server.localPort}")
                if (custom) sdk.setUser("custom", "session")
                val previous = sdk
                val pending = async(Dispatchers.IO) {
                    when (kind) {
                        0 -> previous.getContentBySelector("\"slot\"", page)
                        1 -> previous.getContentBySelectorWithDetails("\"slot\"", page)
                        2 -> previous.getContentByCampaignId("1", page)
                        3 -> previous.getContentByCampaignIdWithDetails("1", page)
                        else -> previous.getContentByGroupSelector("\"group\"", page)
                    }
                }
                withTimeout(5000) { entered.await() }
                GravitySDK.initialize(RuntimeEnvironment.getApplication(), "test-key", "test-section", {})
                sdk = GravitySDK.instance
                release.complete(Unit)
                assertNull("getter=$kind custom=$custom", withTimeout(5000) { pending.await() })
            } finally {
                release.complete(Unit); server.close(); executor.shutdownNow()
            }
        }
    }

    @Test fun getContentDiscardsLateResultButRetainsAnonymousUidAfterSetUser() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executor = Executors.newSingleThreadExecutor()
        val server = ServerSocket(0)
        executor.submit {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var contentLength = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) contentLength = line.substringAfter(":").trim().toInt()
                }
                repeat(contentLength) { input.read() }
                entered.complete(Unit)
                runBlocking { release.await() }
                val body = """{"user":{"uid":"assigned","ses":"anonymous-session"},"data":[]}""".toByteArray()
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(headers.toByteArray()); write(body); flush() }
            }
        }
        sdk.setOptions(null, null, "http://127.0.0.1:${server.localPort}")
        try {
            val old = async(Dispatchers.IO) {
                sdk.getContentBySelectorWithDetails("\"slot\"", PageContext(ContextType.OTHER, emptyList(), "test"))
            }
            withTimeout(10000) {
                kotlinx.coroutines.selects.select<Unit> {
                    entered.onAwait { }
                    old.onAwait {
                        throw AssertionError("Request ended before reaching local server: " +
                            org.robolectric.shadows.ShadowLog.getLogs().joinToString { log -> log.msg + ": " + log.throwable })
                    }
                }
            }
            sdk.setUser("custom", "session")
            release.complete(Unit)
            assertNull(withTimeout(10000) { old.await() })
            assertEquals("assigned", sdk.getUserId())
        } finally {
            release.complete(Unit)
            server.close()
            executor.shutdownNow()
        }
    }
}
