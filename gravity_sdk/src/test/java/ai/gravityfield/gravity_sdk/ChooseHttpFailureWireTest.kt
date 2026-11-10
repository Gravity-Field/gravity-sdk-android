package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import android.os.Looper
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChooseHttpFailureWireTest {
    private lateinit var server: HeadlessRecordingServer
    private lateinit var sdk: GravitySDK
    private val callbacks = CopyOnWriteArrayList<TrackingEvent>()
    private val paths = CopyOnWriteArrayList<String>()
    private val page = PageContext(ContextType.OTHER, emptyList(), "/choose-http-failure")
    private val wasReportingEnabled = Logger.errorReportingEnabled
    private var status = 200
    private var body = """{"user":{"uid":"response-user","ses":"response-session"},"data":[]}"""

    @Before fun initialize() = runBlocking {
        Logger.errorReportingEnabled = false
        server = HeadlessRecordingServer { path, _ ->
            paths += path
            status to body
        }.also { it.start() }
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "choose-key", "choose-section", { callbacks += it })
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, server.url)
        sdk.restoreUserId("previous-user")
    }

    @After fun dispose() {
        sdk.dispose()
        server.stop()
        Logger.errorReportingEnabled = wasReportingEnabled
    }

    private suspend fun allPublicChooseMethods(): List<Any?> = listOf(
        sdk.getContentBySelector("101", page),
        sdk.getContentByGroupSelector("101", page),
        sdk.getContentByCampaignId("101", page),
        sdk.getContentBySelectorWithDetails("101", page),
        sdk.getContentByCampaignIdWithDetails("101", page),
    )

    @Test fun errorResponsesNeverReturnContentPublishIdentityOrNotifyLoad() = runBlocking {
        body = """{"user":{"uid":"error-user","ses":"error-session"},"data":[{"payload":[{"campaignId":"101","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"error-content","contentType":"native","deliveryMethod":"modal"}]}]}]}"""
        for (code in listOf(400, 404, 500)) {
            status = code
            assertTrue("HTTP $code must fail for all five public entrypoints", allPublicChooseMethods().all { it == null })
            assertEquals("Error bodies cannot replace the UID", "previous-user", sdk.getUserId())
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Error content must not trigger load callbacks", callbacks.isEmpty())
        }
        assertEquals(15, paths.size)
        assertTrue(paths.all { it == "/choose" })
    }

    @Test fun notFoundWithoutContentIsFailure() = runBlocking {
        status = 404
        body = """{"error":"campaign not found"}"""
        assertTrue(allPublicChooseMethods().all { it == null })
        assertEquals(5, paths.size)
    }

    @Test fun successfulEmptyContentRemainsSuccess() = runBlocking {
        assertTrue(allPublicChooseMethods().all { it != null })
        assertEquals(5, paths.size)
        assertEquals("response-user", sdk.getUserId())
    }
}
