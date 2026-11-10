package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import android.os.Looper
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class TrackingHttpFailureWireTest {
    private val reporting = Logger.errorReportingEnabled
    private val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val callbacks = CopyOnWriteArrayList<TrackingEvent>()
    private val notifications = CopyOnWriteArrayList<String?>()
    private val preparations = AtomicInteger()
    private val page = PageContext(ContextType.OTHER, emptyList(), "/tracking-http")
    private lateinit var sdk: GravitySDK
    private lateinit var server: HeadlessRecordingServer
    private lateinit var activity: org.robolectric.android.controller.ActivityController<ComponentActivity>
    @Volatile private var status = 200
    @Volatile private var body = """{"user":{"uid":"previous-user","ses":"previous-session"},"campaigns":[]}"""

    @Before fun setup() = runBlocking {
        Logger.errorReportingEnabled = false
        UserIdTestProcess.start()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        server = HeadlessRecordingServer { path, request ->
            requests += path to request
            if (path == "/choose") 200 to """{"data":[{"payload":[{"campaignId":"101","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"forbidden","contentType":"native","deliveryMethod":"modal","variables":{"elements":[{"type":"text","text":"Error content"}]}}]}]}]}"""
            else status to body
        }.also { it.start() }
        GravitySDK.initialize(activity.get(), "tracking-http", "section", { callbacks += it },
            inAppPreparation = InAppPreparation { _, _ -> preparations.incrementAndGet(); null })
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, server.url)
        sdk.restoreUserId("previous-user")
        sdk.trackViewNoShow(page) // Establish a real accepted server session before the failure.
        shadowOf(Looper.getMainLooper()).idle()
        requests.clear(); callbacks.clear()
        sdk.setUserIdListener { notifications += it }
    }
    @After fun cleanup() {
        sdk.setUserIdListener(null); sdk.dispose(); UserIdTestProcess.stop(sdk)
        server.stop(); activity.pause().stop().destroy(); Logger.errorReportingEnabled = reporting
    }
    private suspend fun awaitAutomaticRequest() {
        val scope = GravitySDK::class.java.getDeclaredField("ioScope").apply { isAccessible = true }.get(sdk) as CoroutineScope
        val jobs = requireNotNull(scope.coroutineContext[Job]).children.toList()
        withTimeout(30000) {
            while (jobs.any { !it.isCompleted }) { shadowOf(Looper.getMainLooper()).idle(); delay(5) }
        }
        jobs.joinAll()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun successLookingError() {
        body = """{"user":{"uid":"error-user","ses":"error-session"},"campaigns":[{"campaignId":"101","priority":1,"delayTime":0}],"detail":"BODY-SECRET"}"""
    }
    private suspend fun assertRejected() {
        val actual = listOf(sdk.getUserId(), GravityRepository.sessionManager.cachedUser().ses,
            notifications.toList(), requests.filterNot { it.first == "/visit" || it.first == "/event" }.map { it.first },
            callbacks.map { it.javaClass.simpleName }, preparations.get())
        val expected = listOf("previous-user", "previous-session", emptyList<String>(), emptyList<String>(), emptyList<String>(), 0)
        assertEquals("Refused tracking must preserve identity/listener and never process content", expected, actual)
    }
    @Test fun failedAutomaticVisitDoesNotPublishIdentityOrProcessCampaigns() = runBlocking {
        successLookingError()
        for (code in listOf(400, 404, 500)) {
            status = code; requests.clear()
            sdk.trackView(page, activity.get()); awaitAutomaticRequest()
            assertRejected()
            assertEquals(listOf("/visit"), requests.map { it.first })
        }
    }
    @Test fun failedAutomaticEventDoesNotPublishIdentityOrProcessCampaigns() = runBlocking {
        successLookingError()
        for (code in listOf(400, 404, 500)) {
            status = code; requests.clear()
            sdk.triggerEvent(listOf(CustomEvent("test", "Test")), page, activity.get()); awaitAutomaticRequest()
            assertRejected()
            assertEquals(listOf("/event"), requests.map { it.first })
        }
    }
    @Test fun noShowFailuresRemainRejectedForBothEndpoints() = runBlocking {
        successLookingError(); status = 500
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(listOf(CustomEvent("test", "Test")), page))
        shadowOf(Looper.getMainLooper()).idle(); assertRejected()
    }
    @Test fun successfulTrackingStillPublishesServerIdentityAndKeepsEventTime() = runBlocking {
        body = """{"user":{"uid":"accepted-user","ses":"accepted-session"},"campaigns":[]}"""
        val time = java.util.Date(0)
        sdk.triggerEventNoShow(listOf(CustomEvent("test", "Test", eventTime = time)), page)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("accepted-user", sdk.getUserId())
        assertEquals("accepted-session", GravityRepository.sessionManager.cachedUser().ses)
        assertEquals(listOf("accepted-user"), notifications.toList())
        assertEquals("1970-01-01T00:00:00.000Z", requests.single().second.getJSONArray("data").getJSONObject(0).getString("eventTime"))
    }
}
