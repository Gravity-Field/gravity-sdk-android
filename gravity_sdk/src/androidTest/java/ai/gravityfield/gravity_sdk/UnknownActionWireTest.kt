package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnknownActionWireTest {
    private val wasReportingEnabled = Logger.errorReportingEnabled
    @Before fun disableRemoteReports() { Logger.errorReportingEnabled = false }
    @After fun restoreReports() { Logger.errorReportingEnabled = wasReportingEnabled }

    @Test fun unknownContentActionsNeverSendAnotherUnknownEventsUrl() {
        val server = RecordingServer().also { it.start() }
        try {
            GravitySDK.initialize(InstrumentationRegistry.getInstrumentation().targetContext,
                "content-action-key", "content-action-section", {})
            val variables = Variables.fromJson(listOf("onLoad", "onImpression", "onVisibleImpression", "onClose")
                .associateWith { mapOf("action" to "future-action") })
            val unknown = CampaignContent("content-action", null, DeliveryMethod.INLINE, "test", 1,
                variables, null, null, null, listOf(
                    Event(Action.fromString("click"), listOf("${server.url}/track/click")),
                    Event(Action.CLOSE, listOf("${server.url}/track/close"))))
            val campaign = Campaign(null, emptyList())
            val service = ContentEventService.instance
            service.sendContentLoaded(unknown, campaign, false)
            service.sendContentImpression(unknown, campaign, false)
            service.sendContentVisibleImpression(unknown, campaign, false)
            service.sendContentClosed(unknown, campaign, false)
            // A known content action proves that this transport path remains operational.
            val known = unknown.copy(variables = Variables.fromJson(mapOf("onClose" to mapOf("action" to "close"))))
            service.sendContentClosed(known, campaign, false)
            awaitTrackingJobs()
            assertEquals(listOf("/track/close"), server.recorded.map { it.path })
        } finally { GravitySDK.instance.dispose(); server.stop() }
    }

    @Test fun unknownButtonNeverSendsAnotherUnknownEventsUrl() {
        val server = RecordingServer().also { it.start() }
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            GravitySDK.initialize(context, "action-key", "action-section", {})
            val content = CampaignContent("action", null, DeliveryMethod.INLINE, "test", 1,
                null, null, null, null, listOf(
                    Event(Action.fromString("click"), listOf("${server.url}/track/click")),
                    Event(Action.COPY, listOf("${server.url}/track/copy"))))
            val campaign = Campaign(null, emptyList())
            GravitySDK.instance.onClickHandler(OnClickModel.fromJson(mapOf(
                "action" to "future-action", "closeOnClick" to false)), content, campaign, context)
            // A known action proves that the real asynchronous transport is working.
            GravitySDK.instance.onClickHandler(OnClickModel.fromJson(mapOf(
                "action" to "copy", "closeOnClick" to false)), content, campaign, context)
            awaitTrackingJobs()
            assertEquals(listOf("/track/copy"), server.recorded.map { it.path })
        } finally { GravitySDK.instance.dispose(); server.stop() }
    }    private fun awaitTrackingJobs() = runBlocking {
        withTimeout(30_000) {
            for ((owner, name) in listOf(ContentEventService.instance to "scope", GravitySDK.instance to "ioScope")) {
                val scope = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as CoroutineScope
                scope.coroutineContext[Job]!!.children.toList().joinAll()
            }
        }
    }

}
