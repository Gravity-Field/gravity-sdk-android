package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.prefs.Prefs
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.Closeable
import java.net.ServerSocket
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
// A separate sandbox keeps Settings' process globals cold: no Startup provider is run here.
@Config(sdk = [28], instrumentedPackages = ["com.russhwolf.settings"])
@LooperMode(LooperMode.Mode.PAUSED)
class PrefsWithoutStartupSdkTest {
    @Test fun storedDeviceIdIsPreservedWithoutStartupAcrossReinitialization() = runBlocking<Unit> {
        checkContentRequests(initialDeviceId = "previous-install-device")
    }

    @Test fun generatedDeviceIdIsStableWithoutStartupAcrossReinitialization() = runBlocking<Unit> {
        checkContentRequests(initialDeviceId = null)
    }

    @Test fun nonStringDeviceIdIsReplacedAndStableAcrossReinitialization() = runBlocking<Unit> {
        checkContentRequests(initialDeviceId = null, malformedDeviceId = 42)
    }

    @Test fun genuineDeviceIdReadFailureStillPropagates() {
        val context = RuntimeEnvironment.getApplication()
        val expected = SecurityException("device ID storage unavailable")
        val preferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            assertEquals("gravity_device_id", args!![0])
            when (method.name) {
                "contains" -> true
                "getString" -> throw expected
                else -> throw AssertionError(method.name)
            }
        } as SharedPreferences
        val failingContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
        }
        try {
            Prefs.initialize(failingContext)
            try { Prefs.getDeviceId(); fail("device ID read failure was hidden") } catch (error: SecurityException) {
                assertSame(expected, error)
            }
        } finally {
            Prefs.initialize(context)
        }
    }

    private suspend fun checkContentRequests(initialDeviceId: String?, malformedDeviceId: Int? = null) {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().apply {
            if (initialDeviceId != null) putString("gravity_device_id", initialDeviceId)
            if (malformedDeviceId != null) putInt("gravity_device_id", malformedDeviceId)
            putString("host-key", "preserved")
        }.commit()
        var sdk: GravitySDK? = null
        ChooseServer().use { server ->
            try {
                val ids = (0..1).map {
                    GravitySDK.initialize(context, "test-key", "test-section", {})
                    val current = GravitySDK.instance
                    sdk = current
                    current.setOptions(null, null, server.url)
                    val content = withTimeout(10000) {
                        current.getContentBySelector("\"slot\"", PageContext(ContextType.OTHER, emptyList(), "no-startup"))
                    }
                    assertNotNull("Content requests must work without the Startup provider", content)
                    assertEquals("test-content", content!!.data.single().payload.single().contents.single().contentId)
                    server.requests[it].getJSONObject("device").getString("id")
                }
                assertTrue(ids.first().isNotBlank())
                assertEquals(ids.first(), ids.last())
                assertEquals(ids.first(), preferences.getString("gravity_device_id", null))
                if (initialDeviceId != null) assertEquals(initialDeviceId, ids.first())
                if (malformedDeviceId != null) assertNotEquals(malformedDeviceId.toString(), ids.first())
                assertEquals("preserved", preferences.getString("host-key", null))
            } finally {
                sdk?.dispose()
                sdk?.let { UserIdTestProcess.stop(it) }
            }
        }
    }

    private class ChooseServer : Closeable {
        private val server = ServerSocket(0)
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<JSONObject>()

        init {
            executor.submit {
                repeat(2) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().buffered()
                        val headers = buildString {
                            while (!endsWith("\r\n\r\n")) {
                                val next = input.read()
                                check(next >= 0) { "incomplete headers" }
                                append(next.toChar())
                            }
                        }
                        check(headers.startsWith("POST /choose "))
                        val length = headers.lineSequence()
                            .first { it.startsWith("Content-Length:", true) }
                            .substringAfter(':').trim().toInt()
                        requests.add(JSONObject(input.readNBytes(length).decodeToString()))
                        val body = """{"user":{"uid":"server-user","ses":"session"},"data":[{"selector":"slot","payload":[{"campaignId":"1","experienceId":"experience","variationId":"variation","decisionId":"decision","contents":[{"contentId":"test-content","contentType":"html"}]}]}]}""".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            executor.shutdownNow()
        }
    }
}
