package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.NotificationPermissionStatus
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ContentImpressionEngagement
import ai.gravityfield.gravity_sdk.models.ProductClickEngagement
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.prefs.Prefs
import ai.gravityfield.gravity_sdk.session.SessionManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.IOException
import java.lang.reflect.Proxy
import android.os.Looper
import java.lang.reflect.Modifier
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
class UserIdBeforeInitializeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    private val page = PageContext(ContextType.OTHER, emptyList(), "startup")
    private var initialized: GravitySDK? = null

    @Before fun startApplication() {
        UserIdTestProcess.start()
    }

    @After fun cleanUp() {
        initialized?.dispose()
        initialized?.let { UserIdTestProcess.stop(it) }
        // The getter itself is under test and can throw on the unfixed SDK.
        runCatching { GravitySDK.instance.setUserIdListener(null) }
    }

    @Test fun getterBeforeInitializeWaitsAndReturnsStoredUid() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val getter = async(start = CoroutineStart.UNDISPATCHED) { GravitySDK.instance.getUserId() }
        try {
            assertFalse("Missing Context is not an absent UID", getter.isCompleted)
            initialize()
            assertEquals("stored", withTimeout(3000) { getter.await() })
        } finally { getter.cancelAndJoin() }
    }

    @Test fun waitingGetterCanBeCancelledWithoutInitializingSdk() = runBlocking<Unit> {
        val getter = async(start = CoroutineStart.UNDISPATCHED) { GravitySDK.instance.getUserId() }
        assertFalse(getter.isCompleted)
        getter.cancelAndJoin()
        assertTrue(getter.isCancelled)
        assertFalse(GravitySDK.isInitialized)
    }

    @Test fun isInitializedDistinguishesPlaceholderFromConfiguredSdk() {
        assertFalse(GravitySDK.isInitialized)
        initialize()
        assertTrue(GravitySDK.isInitialized)
        GravitySDK.instance.dispose()
        assertTrue(GravitySDK.isInitialized)
    }

    @Test fun restoreBeforeInitializePublishesMemoryAndWaitsForDurableWrite() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val sdk = GravitySDK.instance
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener { heard.add(it) }
        val restore = async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.restoreUserId("  restored  ") } }
        val getter = async(start = CoroutineStart.UNDISPATCHED) { sdk.getUserId() }
        try {
            assertFalse(restore.isCompleted)
            assertFalse(getter.isCompleted)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("restored"), heard)
            assertEquals("restored", GravityRepository.sessionManager.cachedUser().uid)
            assertEquals("stored", preferences.getString("gravity_user_id", null))
            initialize()
            assertTrue(withTimeout(3000) { restore.await() }.isSuccess)
            assertEquals("restored", withTimeout(3000) { getter.await() })
            assertEquals("restored", preferences.getString("gravity_user_id", null))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("restored"), heard)
        } finally {
            if (!GravitySDK.isInitialized) initialize()
            withTimeout(3000) { restore.await() }
            getter.cancelAndJoin()
        }
    }

    @Test fun resetBeforeInitializeNotifiesOnlyAfterReadingStoredUid() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val sdk = GravitySDK.instance
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener { heard.add(it) }
        val reset = async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.resetUser() } }
        val getter = async(start = CoroutineStart.UNDISPATCHED) { sdk.getUserId() }
        try {
            assertFalse(reset.isCompleted)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(heard.isEmpty())
            assertNull(GravityRepository.sessionManager.cachedUser().uid)
            assertEquals("stored", preferences.getString("gravity_user_id", null))
            initialize()
            assertTrue(withTimeout(3000) { reset.await() }.isSuccess)
            assertNull(withTimeout(3000) { getter.await() })
            assertFalse(preferences.contains("gravity_user_id"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<String?>(null), heard)
            val request = firstVisit()
            assertTrue(request.isNull("uid"))
            assertTrue(request.isNull("ses"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(null, "server-user"), heard)
        } finally {
            if (!GravitySDK.isInitialized) initialize()
            withTimeout(3000) { reset.await() }
            getter.cancelAndJoin()
        }
    }

    @Test fun resetBeforeInitializeWithEmptyStorageNeverNotifiesNull() = runBlocking<Unit> {
        preferences.edit().remove("gravity_user_id").commit()
        val sdk = GravitySDK.instance
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener { heard.add(it) }
        val reset = async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.resetUser() } }
        val getter = async(start = CoroutineStart.UNDISPATCHED) { sdk.getUserId() }
        try {
            assertFalse(reset.isCompleted)
            assertFalse(getter.isCompleted)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(heard.isEmpty())
            initialize()
            assertTrue(withTimeout(3000) { reset.await() }.isSuccess)
            assertNull(withTimeout(3000) { getter.await() })
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(heard.isEmpty())
            assertFalse(preferences.contains("gravity_user_id"))
        } finally {
            if (!GravitySDK.isInitialized) initialize()
            withTimeout(3000) { reset.await() }
            getter.cancelAndJoin()
        }
    }

    @Test fun queuedRestoreResetRestoreKeepsOrderAcrossInitialize() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val sdk = GravitySDK.instance
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener { heard.add(it) }
        val pending = listOf(
            async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.restoreUserId("first") } },
            async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.resetUser() } },
            async(start = CoroutineStart.UNDISPATCHED) { runCatching { sdk.restoreUserId("last") } },
        )
        try {
            assertTrue(pending.none { it.isCompleted })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first", null, "last"), heard)
            initialize()
            pending.forEach { assertTrue(withTimeout(3000) { it.await() }.isSuccess) }
            assertEquals("last", sdk.getUserId())
            assertEquals("last", preferences.getString("gravity_user_id", null))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first", null, "last"), heard)
        } finally {
            if (!GravitySDK.isInitialized) initialize()
            pending.forEach { withTimeout(3000) { it.await() } }
        }
    }

    @Test fun listenerIgnoresStoredReadsAndReceivesFirstServerResponse() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val heard = mutableListOf<String?>()
        GravitySDK.instance.setUserIdListener {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            heard.add(it)
        }
        initialize()
        assertEquals("stored", GravitySDK.instance.getUserId())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(heard.isEmpty())
        initialize()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(heard.isEmpty())
        firstVisit("stored")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("stored"), heard)
        GravitySDK.instance.restoreUserId("after")
        GravitySDK.instance.resetUser()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("stored", "after", null), heard)
    }

    @Test fun initializeReadsStoredUidWithoutNotifyingListener() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val heard = mutableListOf<String?>()
        GravitySDK.instance.setUserIdListener { heard.add(it) }
        initialize()
        withTimeout(3000) {
            while (GravityRepository.sessionManager.cachedUser().uid != "stored") delay(1)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(heard.isEmpty())
    }

    @Test fun queuedRestoreFailurePropagatesAndRollsBackListener() = assertQueuedFailure(restore = true)
    @Test fun queuedResetFailurePropagatesAndKeepsMemoryCleared() = assertQueuedFailure(restore = false)

    private fun assertQueuedFailure(restore: Boolean) = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val sdk = GravitySDK.instance
        val heard = mutableListOf<String?>()
        sdk.setUserIdListener { heard.add(it) }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { if (restore) sdk.restoreUserId("rejected") else sdk.resetUser() }
        }
        try {
            assertFalse(pending.isCompleted)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(if (restore) listOf("rejected") else emptyList<String?>(), heard)
            val failingPreferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                if (method.name != "edit") method.invoke(preferences, *(args ?: emptyArray())) else {
                    val editor = preferences.edit()
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, editMethod, editArgs ->
                        if (editMethod.name == "commit") false else {
                            val result = editMethod.invoke(editor, *(editArgs ?: emptyArray()))
                            if (result === editor) proxy else result
                        }
                    }
                }
            } as SharedPreferences
            val failingContext = object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String?, mode: Int) = failingPreferences
            }
            initialize(failingContext)
            assertTrue(withTimeout(3000) { pending.await() }.exceptionOrNull() is IOException)
            assertEquals(if (restore) "stored" else null, sdk.getUserId())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(if (restore) listOf("rejected", "stored") else listOf<String?>(null), heard)
            assertEquals("stored", preferences.getString("gravity_user_id", null))
        } finally {
            if (!GravitySDK.isInitialized) initialize()
            withTimeout(3000) { pending.await() }
            Prefs.initialize(context)
        }
    }

    @Test fun invalidRestoreBeforeInitializeDoesNotQueueOrChangeIdentity() = runBlocking<Unit> {
        preferences.edit().putString("gravity_user_id", "stored").commit()
        val heard = mutableListOf<String?>()
        GravitySDK.instance.setUserIdListener { heard.add(it) }
        for (uid in listOf("", "  \t ")) {
            assertTrue(runCatching { GravitySDK.instance.restoreUserId(uid) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertFalse(GravitySDK.isInitialized)
        initialize()
        assertEquals("stored", GravitySDK.instance.getUserId())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(heard.isEmpty())
    }

    @Test fun otherPublicApisStillThrowBeforeInitialize() = runBlocking<Unit> {
        val sdk = GravitySDK.instance
        val content = CampaignContent.fromJson(mapOf("contentId" to "content", "contentType" to "html"))
        val campaign = Campaign(null, emptyList())
        val slot = Slot(emptyMap(), false, 0, null, null)
        val calls: List<suspend () -> Unit> = listOf(
            { sdk.setOptions(null, null, null) },
            { sdk.setUser("custom", "session") },
            { sdk.setNotificationPermissionStatus(NotificationPermissionStatus.UNKNOWN) },
            { sdk.setPresentationLockListener(null) },
            { sdk.lockPresentation() },
            { sdk.unlockPresentation() },
            { sdk.trackView(page, context) },
            { sdk.triggerEvent(emptyList(), page, context) },
            { sdk.getContentBySelector("slot", page); Unit },
            { sdk.getContentBySelectorWithDetails("slot", page); Unit },
            { sdk.getContentByCampaignId("1", page); Unit },
            { sdk.getContentByCampaignIdWithDetails("1", page); Unit },
            { sdk.getContentByGroupSelector("group", page); Unit },
            { sdk.sendContentEngagement(ContentImpressionEngagement(content, campaign)) },
            { sdk.sendProductEngagement(ProductClickEngagement(slot, content, campaign)) },
            { sdk.dismissInApp("content") },
            { sdk.resetInlineViewCache("slot", page) },
            { sdk.resetInlineListViewCache("group", page) },
            { sdk.dispose() },
        )
        for ((index, call) in calls.withIndex()) {
            try {
                call()
                fail("API $index was available before initialize")
            } catch (error: Exception) {
                assertEquals("API $index", "GravitySDK has not been initialized", error.message)
            }
        }
    }

    @Test fun retainedPreInitializationInstanceInvalidatesCurrentContent() = runBlocking<Unit> {
        val before = GravitySDK.instance
        preferences.edit().putString("gravity_user_id", "before").commit()
        initialize()
        val sdk = GravitySDK.instance
        assertNotSame(before, sdk)
        val request = sdk.presentationGate.beginRequest(page.location, false)
        before.restoreUserId("after")
        assertFalse(sdk.presentationGate.isCurrent(request))
        assertEquals("after", sdk.getUserId())
        val next = sdk.presentationGate.beginRequest(page.location, false)
        before.resetUser()
        assertFalse(sdk.presentationGate.isCurrent(next))
        assertNull(sdk.getUserId())
    }

    private fun initialize(storageContext: Context = context) {
        GravitySDK.initialize(storageContext, "test-key", "test-section", {})
        initialized = GravitySDK.instance
    }

    private suspend fun firstVisit(responseUid: String = "server-user"): JSONObject {
        val captured = CompletableDeferred<JSONObject>()
        val executor = Executors.newSingleThreadExecutor()
        val server = ServerSocket(0)
        executor.submit {
            try {
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
                    check(headers.lineSequence().first().startsWith("POST /visit "))
                    val length = headers.lineSequence()
                        .first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(":").trim().toInt()
                    val user = JSONObject(input.readNBytes(length).decodeToString()).getJSONObject("user")
                    val body = """{"user":{"uid":"$responseUid","ses":"server-session"},"campaigns":[]}""".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                    captured.complete(user)
                }
            } catch (error: Exception) { captured.completeExceptionally(error) }
        }
        try {
            GravitySDK.instance.setOptions(null, null, "http://127.0.0.1:${server.localPort}")
            GravitySDK.instance.trackView(page, context)
            return withTimeout(10000) {
                val user = captured.await()
                // Wait for session publication so no response work leaks into test cleanup.
                while (GravitySDK.instance.getUserId() != responseUid) delay(10)
                user
            }
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }
}

/** Robolectric retains SDK/library statics between test methods; model a fresh Android process. */
internal object UserIdTestProcess {
    @JvmStatic fun start() {
        GravitySDK::class.java.getDeclaredField("_instance").apply {
            isAccessible = true
            (get(null) as? GravitySDK)?.let { stop(it) }
            set(null, null)
        }
        val fresh = SessionManager(Prefs)
        for (field in SessionManager::class.java.declaredFields) {
            if (!Modifier.isFinal(field.modifiers) && !Modifier.isStatic(field.modifiers)) {
                field.isAccessible = true
                field.set(GravityRepository.sessionManager, field.get(fresh))
            }
        }
        Prefs::class.java.getDeclaredField("userIds").apply {
            isAccessible = true
            set(null, null)
        }
        Prefs::class.java.getDeclaredField("readiness").apply {
            isAccessible = true
            set(null, CompletableDeferred<Unit>())
        }
    }

    @JvmStatic fun stop(sdk: GravitySDK) {
        GravitySDK::class.java.getDeclaredMethod("stopDeliveryRuntime").apply {
            isAccessible = true
            invoke(sdk)
        }
    }
}
