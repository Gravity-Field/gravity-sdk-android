package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import ai.gravityfield.gravity_sdk.utils.VisibleImpressionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnonymousImpressionScopeTest {
    @Test fun fastCloseProtectsTheNextAnonymousOpening() = runBlocking<Unit> {
        val release = CountDownLatch(1)
        val server = HeadlessRecordingServer { _, _ ->
            check(release.await(15, TimeUnit.SECONDS)); 503 to ""
        }
        server.start()
        Logger.errorReportingEnabled = false
        try {
            GravitySDK.initialize(RuntimeEnvironment.getApplication(), "anonymous-${UUID.randomUUID()}", "anonymous", {})
            val sdk = GravitySDK.instance
            sdk.resetUser()
            val store = GravitySDK::class.java.getDeclaredMethod("getVisibleImpressionStore").apply { isAccessible = true }
                .invoke(sdk) as VisibleImpressionStore
            store.load()
            val gate = sdk.presentationGate
            val first = gate.reserve("campaign", gate.beginRequest("page", false))!!
            assertTrue(gate.present(first, listOf("content")) { true })
            val content = CampaignContent("content", null, DeliveryMethod.MODAL, "native", null, null, null, null, null,
                listOf(Event(Action.VISIBLE_IMPRESSION, listOf("${server.url}/wrimp"))))
            assertTrue(sdk.enqueueModalVisibleImpression(content, User(), "campaign"))
            gate.closed("content")
            assertNull("A pending anonymous WRIMP must protect the next opening", gate.reserve("campaign", gate.beginRequest("page", false)))
        } finally {
            GravitySDK.instance.dispose()
            release.countDown()
            server.stop()
            Logger.errorReportingEnabled = true
        }
    }
}
