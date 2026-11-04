package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import android.os.Looper
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Contracts where headless tracking and the shared session wire meet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RepositorySessionWireTest {
    private lateinit var sdk: GravitySDK
    private lateinit var server: HeadlessRecordingServer
    private val callbacks = CopyOnWriteArrayList<TrackingEvent>()
    private val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var blockedPath = ""
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val page = PageContext(ContextType.OTHER, emptyList(), "session-wire")

    @Before fun initialize() = runBlocking {
        Logger.errorReportingEnabled = false
        server = HeadlessRecordingServer { path, body ->
            requests += path to body
            if (path == blockedPath) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            200 to when (path) {
                "/visit", "/event" -> """{"user":{"uid":"old","ses":"old-session"},"campaigns":[{"campaignId":"101","priority":1}]}"""
                "/choose" -> """{"user":{"uid":"old","ses":"old-session"},"data":[{"payload":[{"campaignId":"101","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"old-content","contentType":"native","deliveryMethod":"modal"}]}]}]}"""
                else -> "{}"
            }
        }
        server.start()
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "wire-key", "wire-section", { callbacks += it })
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, server.url)
        sdk.restoreUserId("old")
    }

    @After fun dispose() {
        release.countDown()
        sdk.setUserIdListener(null)
        sdk.dispose()
        server.stop()
        Logger.errorReportingEnabled = true
    }

    private fun assertObsoleteResponseIsDiscarded(useEvent: Boolean, duringChoose: Boolean, restore: Boolean) = runBlocking<Unit> {
        blockedPath = if (duringChoose) "/choose" else if (useEvent) "/event" else "/visit"
        val pending = async(Dispatchers.IO) {
            if (useEvent) sdk.triggerEventNoShow(listOf(CustomEvent(type = "test", name = "Test")), page)
            else sdk.trackViewNoShow(page)
        }
        assertTrue("The old request must be in flight", entered.await(5, TimeUnit.SECONDS))
        if (restore) sdk.restoreUserId("  restored  ") else sdk.resetUser()
        release.countDown()
        assertNull(withTimeout(5000) { pending.await() })
        assertEquals(if (restore) "restored" else null, sdk.getUserId())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Obsolete content must not emit load or impression callbacks", callbacks.isEmpty())
        assertEquals(if (duringChoose) 1 else 0, requests.count { it.first == "/choose" })
        assertTrue(requests.all { it.first in listOf("/visit", "/event", "/choose") })
    }

    @Test fun resetDuringVisitDoesNotChooseOrResurrectOldUid() = assertObsoleteResponseIsDiscarded(false, false, false)
    @Test fun restoreDuringVisitDoesNotChooseOrOverwriteRestoredUid() = assertObsoleteResponseIsDiscarded(false, false, true)
    @Test fun resetDuringEventDoesNotChooseOrResurrectOldUid() = assertObsoleteResponseIsDiscarded(true, false, false)
    @Test fun restoreDuringEventDoesNotChooseOrOverwriteRestoredUid() = assertObsoleteResponseIsDiscarded(true, false, true)
    @Test fun resetDuringVisitChooseDoesNotReturnOldContent() = assertObsoleteResponseIsDiscarded(false, true, false)
    @Test fun restoreDuringVisitChooseDoesNotReturnOldContent() = assertObsoleteResponseIsDiscarded(false, true, true)
    @Test fun resetDuringEventChooseDoesNotReturnOldContent() = assertObsoleteResponseIsDiscarded(true, true, false)
    @Test fun restoreDuringEventChooseDoesNotReturnOldContent() = assertObsoleteResponseIsDiscarded(true, true, true)
}
