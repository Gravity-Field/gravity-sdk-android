package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.LocalModalImpression
import ai.gravityfield.gravity_sdk.ui.ModalVisibilityDetector
import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests: real Compose placement, Material sheet animation and SDK overlay lifecycle wiring. */
@RunWith(AndroidJUnit4::class)
class ModalVisibilityWiringTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cachedContentMovingIntoWindowCountsWithoutContentRedraw() {
        val impressions = AtomicInteger()
        val opening = ModalImpressionSession { impressions.incrementAndGet(); true }
        var translation by mutableFloatStateOf(5000f)
        rule.setContent {
            CompositionLocalProvider(LocalModalImpression provides opening) {
                Box(Modifier.graphicsLayer { translationY = translation }) {
                    ModalVisibilityDetector { Text("Static content", Modifier.size(200.dp)) }
                }
            }
        }
        rule.runOnIdle { assertEquals(0, impressions.get()); translation = 0f }
        rule.waitUntil(3000) { impressions.get() == 1 }
        rule.runOnIdle { translation = 100f }
        rule.runOnIdle { assertEquals(1, impressions.get()) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun textOnlyBottomSheetCountsDuringItsOpeningAnimation() {
        rule.mainClock.autoAdvance = false
        val impressions = AtomicInteger()
        var valueAtImpression: SheetValue? = null
        lateinit var state: SheetState
        val opening = ModalImpressionSession {
            valueAtImpression = state.currentValue
            impressions.incrementAndGet()
            true
        }
        rule.setContent {
            state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            CompositionLocalProvider(LocalModalImpression provides opening) {
                ModalBottomSheet(onDismissRequest = {}, sheetState = state) {
                    ModalVisibilityDetector { Text("Text only", Modifier.size(240.dp)) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(96)
        rule.waitUntil(3000) { impressions.get() == 1 }
        rule.runOnIdle {
            assertEquals(SheetValue.Hidden, valueAtImpression)
            assertEquals(1, impressions.get())
        }
        rule.mainClock.advanceTimeBy(1000)
    }

    @Test fun scrimAndLayoutWithoutContentDrawCannotConfirmAnOpening() {
        val impressions = AtomicInteger()
        val opening = ModalImpressionSession { impressions.incrementAndGet(); true }
        rule.setContent {
            CompositionLocalProvider(LocalModalImpression provides opening) {
                // No drawn window content: component creation and the surrounding window are insufficient.
                ModalVisibilityDetector { Box(Modifier.size(0.dp)) }
            }
        }
        rule.runOnIdle { opening.explicitClose(); assertEquals(0, impressions.get()) }
    }

    @Test fun countedSdkFullscreenSurvivesStopAndEmitsCloseOnlyOnExplicitDismissal() {
        rule.setContent { }
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        val content = CampaignContent("lifecycle-test", null, DeliveryMethod.FULL_SCREEN, "test", 1,
            Variables.fromJson(mapOf("elements" to listOf(mapOf("type" to "text", "text" to "Foreground content")))),
            null, null, null, null)
        val opening = ModalImpressionSession { impressions.incrementAndGet(); true }
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "ui-test-key", "ui-test-section", {
                if (it is ContentCloseEvent) closes.incrementAndGet()
            })
            val method = GravitySDK::class.java.getDeclaredMethod("showFullScreen", Activity::class.java,
                CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
            method.isAccessible = true
            method.invoke(GravitySDK.instance, rule.activity, content, Campaign(null, emptyList()), null, opening)
        }
        rule.waitUntil(3000) { impressions.get() == 1 }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(0, closes.get())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnUiThread { GravitySDK.instance.dismissInApp(content.contentId) }
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }
    @Test fun skippedNextStepHonorsCloseOnClick() {
        rule.setContent { }
        val preparations = AtomicInteger()
        val closes = AtomicInteger()
        val content = CampaignContent("first", null, DeliveryMethod.FULL_SCREEN, "test", 1,
            null, null, null, null, null)
        val next = content.copy(contentId = "second", step = 2)
        val campaign = Campaign(null, listOf(CampaignVariation("campaign", "e", "v", "d", listOf(content, next))))
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "steps-key", "steps-section", {}, inAppPreparation = InAppPreparation { _, _ ->
                preparations.incrementAndGet()
                null
            })
            GravitySDK.instance.onClickHandler(OnClickModel.fromJson(mapOf("action" to "open_step", "step" to 2, "closeOnClick" to true)),
                content, campaign, rule.activity, { closes.incrementAndGet() }, ModalImpressionSession { true })
        }
        rule.waitUntil(3000) { closes.get() == 1 }
        rule.runOnUiThread {
            GravitySDK.instance.onClickHandler(OnClickModel.fromJson(mapOf("action" to "open_step", "step" to 2, "closeOnClick" to false)),
                content, campaign, rule.activity, { closes.incrementAndGet() }, ModalImpressionSession { true })
        }
        rule.waitUntil(3000) { preparations.get() == 2 }
        rule.runOnIdle { assertEquals(1, closes.get()) }
    }

    private fun showSdkWindow(methodName: String, variables: Map<String, Any?>,
        impressions: AtomicInteger, closes: AtomicInteger) {
        rule.setContent { }
        val format = when (methodName) {
            "showBottomSheet" -> DeliveryMethod.BOTTOM_SHEET
            "showModal" -> DeliveryMethod.MODAL
            else -> DeliveryMethod.FULL_SCREEN
        }
        val content = CampaignContent("close-path-test", null, format, "test", 1,
            Variables.fromJson(variables), null, null, null, null)
        val opening = ModalImpressionSession { impressions.incrementAndGet(); true }
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "close-path-key", "close-path-section", {
                if (it is ContentCloseEvent) closes.incrementAndGet()
            })
            val method = GravitySDK::class.java.getDeclaredMethod(methodName, Activity::class.java,
                CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
            method.isAccessible = true
            method.invoke(GravitySDK.instance, rule.activity, content, Campaign(null, emptyList()), null, opening)
        }
        rule.waitUntil(3000) { impressions.get() == 1 }
    }

    @Test fun sdkCloseIconRunsTheSharedDismissalPath() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showFullScreen", mapOf(
            "elements" to listOf(mapOf("type" to "text", "text" to "Foreground content")),
            "frameUI" to mapOf("container" to emptyMap<String, Any>(), "close" to mapOf("onClick" to mapOf("action" to "close")))
        ), impressions, closes)
        rule.onNode(hasClickAction()).performClick()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun sdkNotNowButtonRunsTheSharedDismissalPath() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showFullScreen", mapOf("elements" to listOf(mapOf(
            "type" to "button", "text" to "Not now", "onClick" to mapOf("action" to "cancel")))) , impressions, closes)
        rule.onNodeWithText("Not now").performClick()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun sdkBackRunsTheSharedDismissalPath() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showFullScreen", mapOf("elements" to listOf(mapOf("type" to "text", "text" to "Foreground content"))),
            impressions, closes)
        Espresso.pressBack()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun sdkBottomSheetSwipeRunsTheSharedDismissalPath() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showBottomSheet", mapOf("elements" to listOf(mapOf("type" to "text", "text" to "Foreground content")),
            "frameUI" to mapOf("container" to mapOf("style" to mapOf("padding" to mapOf("top" to 100, "bottom" to 100))))) ,
            impressions, closes)
        rule.onNodeWithText("Foreground content").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 1200f), durationMillis = 200)
        }
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun missingStepStillHonorsCloseOnClick() {
        rule.setContent { }
        val closes = AtomicInteger()
        val content = CampaignContent("first", null, DeliveryMethod.FULL_SCREEN, "test", 1,
            null, null, null, null, null)
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "invalid-step-key", "invalid-step-section", {})
            for (payload in listOf(
                mapOf("action" to "open_step", "closeOnClick" to true),
                mapOf("action" to "open_step", "step" to 999, "closeOnClick" to true),
                mapOf("action" to "open_step", "closeOnClick" to false))) {
                GravitySDK.instance.onClickHandler(OnClickModel.fromJson(payload), content, Campaign(null, emptyList()),
                    rule.activity, { closes.incrementAndGet() }, ModalImpressionSession { true })
            }
            assertEquals(2, closes.get())
        }
    }

    @Test fun sdkDialogFirstFrameAndBackCountOneOpening() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showModal", mapOf("elements" to listOf(mapOf("type" to "text", "text" to "Dialog content"))),
            impressions, closes)
        Espresso.pressBack()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun sdkDialogCloseIconCountsOneOpening() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showModal", mapOf(
            "elements" to listOf(mapOf("type" to "text", "text" to "Dialog content")),
            "frameUI" to mapOf("container" to emptyMap<String, Any>(), "close" to mapOf(
                "style" to mapOf("positioned" to mapOf("top" to 0, "right" to 0),
                    "size" to mapOf("width" to 24, "height" to 24)),
                "onClick" to mapOf("action" to "close")))
        ), impressions, closes)
        rule.onNode(hasClickAction()).performClick()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun sdkDialogNotNowCountsOneOpening() {
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        showSdkWindow("showModal", mapOf("elements" to listOf(mapOf(
            "type" to "button", "text" to "Not now", "onClick" to mapOf("action" to "cancel")))), impressions, closes)
        rule.onNodeWithText("Not now").performClick()
        rule.waitUntil(3000) { closes.get() == 1 }
        assertEquals(1, impressions.get())
    }

    @Test fun oldSdkEnqueueAfterReinitializeWakesCurrentRuntimeAndProtectsBeforeReturning() {
        rule.setContent { }
        val persisted = AtomicInteger()
        val releasePersistence = java.util.concurrent.CountDownLatch(1)
        lateinit var current: GravitySDK
        lateinit var store: ai.gravityfield.gravity_sdk.utils.VisibleImpressionStore
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "replacement-key", "replacement-section", {})
            val old = GravitySDK.instance
            GravitySDK.initialize(rule.activity, "replacement-key", "replacement-section", {})
            current = GravitySDK.instance
            val field = GravitySDK::class.java.getDeclaredField("visibleImpressionStore").apply { isAccessible = true }
            store = field.get(current) as ai.gravityfield.gravity_sdk.utils.VisibleImpressionStore
            val content = CampaignContent("replacement-content", null, DeliveryMethod.MODAL, "test", 1,
                null, null, null, null, listOf(Event(Action.VISIBLE_IMPRESSION, listOf("http://["))))
            assertTrue(old.enqueueModalVisibleImpression(content, User(custom = "old"), "campaign") {
                persisted.incrementAndGet()
                check(releasePersistence.await(5, java.util.concurrent.TimeUnit.SECONDS))
            })
            try { assertTrue(store.blocks("campaign", "custom:old")) }
            finally { releasePersistence.countDown() }
        }
        // Invalid URL is a permanent drop. This requires the replacement runtime to wake and drain.
        rule.waitUntil(5000) { persisted.get() == 1 && !store.hasWork() }
    }

    @Test fun sdkStopBeforeFirstFrameClosesWithoutCloseAnalyticsOrImpression() {
        rule.setContent { }
        val impressions = AtomicInteger()
        val closes = AtomicInteger()
        rule.runOnUiThread {
            GravitySDK.initialize(rule.activity, "unshown-key", "unshown-section", {
                if (it is ContentCloseEvent) closes.incrementAndGet()
            })
            val content = CampaignContent("unshown", null, DeliveryMethod.FULL_SCREEN, "test", 1,
                Variables.fromJson(mapOf("elements" to listOf(mapOf("type" to "text", "text" to "Not drawn yet")))),
                null, null, null, null)
            val opening = ModalImpressionSession { impressions.incrementAndGet(); true }
            val method = GravitySDK::class.java.getDeclaredMethod("showFullScreen", Activity::class.java,
                CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
            method.isAccessible = true
            method.invoke(GravitySDK.instance, rule.activity, content, Campaign(null, emptyList()), null, opening)
            // Deliver STOP before returning control to the first traversal.
            val lifecycle = rule.activity.lifecycle as androidx.lifecycle.LifecycleRegistry
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            assertTrue(opening.isAbandoned)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        rule.runOnIdle {
            assertEquals(0, impressions.get())
            assertEquals(0, closes.get())
        }
    }

}
