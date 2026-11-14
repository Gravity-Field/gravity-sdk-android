package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HostRemovalDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private enum class Removal { OVERLAY, DIALOG, FINISH, RECREATE, REPARENT, BACKGROUND }

    @Test fun hostRemovesAppearedFullscreenOverlay() = exercise(DeliveryMethod.FULL_SCREEN, Removal.OVERLAY)
    @Test fun hostRemovesAppearedModalOverlay() = exercise(DeliveryMethod.MODAL, Removal.OVERLAY)
    @Test fun hostRemovesAppearedSheetOverlay() = exercise(DeliveryMethod.BOTTOM_SHEET, Removal.OVERLAY)
    @Test fun hostRemovesAppearedSnackbarOverlay() = exercise(DeliveryMethod.SNACK_BAR, Removal.OVERLAY)
    @Test fun hostDismissesNativeModalDialog() = exercise(DeliveryMethod.MODAL, Removal.DIALOG)
    @Test fun hostDismissesNativeSheetDialog() = exercise(DeliveryMethod.BOTTOM_SHEET, Removal.DIALOG)
    @Test fun activityFinishClosesAppearedPresentations() {
        for (delivery in deliveries) exercise(delivery, Removal.FINISH)
    }
    @Test fun hostRemovalBeforeFirstFrameRemainsSilent() {
        for (delivery in deliveries) {
            // removeView is synchronous; Activity.finish may still draw a real outgoing frame.
            exercise(delivery, Removal.OVERLAY, appeared = false)
        }
    }
    @Test fun immediateOverlayReparentingDoesNotCloseRestoredContent() {
        for (delivery in deliveries) exercise(delivery, Removal.REPARENT)
    }
    @Test fun recreationKeepsTheSameShowingInTheNewActivity() {
        // As on iOS/Flutter, a configuration change keeps the presentation: no close, then one real close.
        for (delivery in deliveries) exercise(delivery, Removal.RECREATE)
    }
    @Test fun appearedModalsWithoutWrimpSurviveBackgroundAndCloseExplicitly() {
        for (delivery in deliveries.filter { it != DeliveryMethod.SNACK_BAR }) exercise(delivery, Removal.BACKGROUND)
    }

    private val deliveries = listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET, DeliveryMethod.SNACK_BAR)

    private fun exercise(delivery: DeliveryMethod, removal: Removal, appeared: Boolean = true) {
        val server = RecordingServer().also { it.start() }
        val closes = AtomicInteger()
        val confirmations = AtomicInteger()
        lateinit var sdk: GravitySDK
        var initialized = false
        lateinit var overlay: View
        lateinit var oldActivity: ComponentActivity
        val content = CampaignContent("host-removal", if (delivery == DeliveryMethod.SNACK_BAR) TemplateSystemName.SNACKBAR_2 else null,
            delivery, "native", 1,
            Variables.fromJson(mapOf(
                "frameUI" to mapOf("container" to emptyMap<String, Any>()),
                "elements" to listOf(mapOf("type" to "text", "text" to "Host removal content")),
                "onClose" to mapOf("action" to "close"))),
            null, null, null, listOf(Event(Action.CLOSE, listOf("${server.url}/close"))))
        val opening = ModalImpressionSession { confirmations.incrementAndGet(); removal != Removal.BACKGROUND }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { activity ->
                oldActivity = activity
                activity.setContentView(FrameLayout(activity))
                GravitySDK.initialize(activity, "host-removal-device", java.util.UUID.randomUUID().toString(), {
                    if (it is ContentCloseEvent) closes.incrementAndGet()
                })
                sdk = GravitySDK.instance
                initialized = true
            }
            val storeGetter = GravitySDK::class.java.getDeclaredMethod("getVisibleImpressionStore").apply { isAccessible = true }
            (storeGetter.invoke(sdk) as ai.gravityfield.gravity_sdk.utils.VisibleImpressionStore).load()
            scenario.onActivity { activity ->
                val request = sdk.presentationGate.beginRequest("host-removal", true)
                val reservation = requireNotNull(sdk.presentationGate.reserve("host-campaign", request))
                assertTrue(sdk.presentationGate.present(reservation, listOf(content.contentId)) {
                    val method = GravitySDK::class.java.getDeclaredMethod("showBackendContent", Activity::class.java,
                        CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
                    method.isAccessible = true
                    method.invoke(sdk, activity, content, Campaign(null, emptyList()), null, opening) as Boolean
                })
                overlay = (activity.window.decorView as ViewGroup).getChildAt(0)
                if (!appeared) remove(activity, overlay, removal)
            }
            if (appeared) {
                await { confirmations.get() > 0 }
                when (removal) {
                    Removal.RECREATE -> scenario.recreate()
                    Removal.BACKGROUND -> {
                        scenario.moveToState(Lifecycle.State.CREATED)
                        instrumentation.runOnMainSync {
                            assertTrue("Appeared $delivery must remain mounted without WRIMP", overlay.isAttachedToWindow)
                            assertEquals(0, closes.get())
                        }
                        scenario.moveToState(Lifecycle.State.RESUMED)
                        instrumentation.runOnMainSync { sdk.dismissInApp(content.contentId) }
                    }
                    else -> scenario.onActivity { remove(it, overlay, removal) }
                }
            }
            if (!appeared) assertFalse("The negative fixture must never draw", opening.hasAppeared)
            val expected = if (appeared && removal !in listOf(Removal.RECREATE, Removal.REPARENT)) 1 else 0
            if (expected == 1) await { closes.get() == 1 && server.recorded.count { it.path == "/close" } >= 1 }
            if (removal !in listOf(Removal.RECREATE, Removal.REPARENT)) await {
                !overlay.isAttachedToWindow && (removal != Removal.FINISH || oldActivity.isDestroyed)
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(150) // Allow an incorrectly duplicated asynchronous URL to reach the local recorder.
            assertEquals("$delivery/$removal callback", expected, closes.get())
            assertEquals("$delivery/$removal URL", expected, server.recorded.count { it.path == "/close" })
            if (removal == Removal.REPARENT) assertTrue(overlay.isAttachedToWindow)
            if (removal == Removal.RECREATE) {
                assertTrue(oldActivity.isChangingConfigurations)
                assertFalse("The old Activity's overlay is gone", overlay.isAttachedToWindow)
                lateinit var restored: View
                scenario.onActivity { activity ->
                    assertNotSame(oldActivity, activity)
                    restored = (activity.window.decorView as ViewGroup).getChildAt(0)
                }
                await { restored.isAttachedToWindow }
                assertNotSame("$delivery must be remounted in the recreated Activity", overlay, restored)
                assertTrue("$delivery restored SDK overlay", restored is androidx.compose.ui.platform.ComposeView)
                assertEquals("$delivery opening stays confirmed once", 1, confirmations.get())
                instrumentation.runOnMainSync { sdk.dismissInApp(content.contentId) }
                await { closes.get() == 1 && server.recorded.count { it.path == "/close" } >= 1 && !restored.isAttachedToWindow }
                instrumentation.waitForIdleSync()
                Thread.sleep(150)
                assertEquals("$delivery one close after rotation", 1, closes.get())
                assertEquals("$delivery one close URL after rotation", 1, server.recorded.count { it.path == "/close" })
                instrumentation.runOnMainSync {
                    assertNotNull("UI reservation must be released", sdk.presentationGate.reserve("host-campaign", sdk.presentationGate.beginRequest("after", false)))
                }
            }
            if (removal !in listOf(Removal.RECREATE, Removal.REPARENT)) instrumentation.runOnMainSync {
                assertFalse("$delivery/$removal overlay must be removed", overlay.isAttachedToWindow)
                assertNotNull("UI reservation must be released", sdk.presentationGate.reserve("host-campaign", sdk.presentationGate.beginRequest("after", false)))
            }
        } finally {
            if (initialized) instrumentation.runOnMainSync { sdk.dispose() }
            scenario.close()
            server.stop()
        }
    }

    private fun remove(activity: ComponentActivity, overlay: View, removal: Removal) {
        when (removal) {
            Removal.OVERLAY -> (overlay.parent as ViewGroup).removeView(overlay)
            Removal.FINISH -> activity.finish()
            Removal.DIALOG -> {
                fun descendants(view: View): Sequence<View> = sequence {
                    yield(view)
                    if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
                }
                val provider = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
                    .filterIsInstance<DialogWindowProvider>().single()
                val dialog = provider.window.callback as Dialog
                dialog.dismiss()
            }
            Removal.REPARENT -> {
                val parent = overlay.parent as ViewGroup
                val params = overlay.layoutParams
                parent.removeView(overlay)
                parent.addView(overlay, 0, params)
            }
            else -> error("Handled by ActivityScenario")
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Timed out waiting for host-removal effects", condition())
    }
}
