package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.*
import ai.gravityfield.gravity_sdk.utils.*
import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.compositionContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
// Use the target-SDK sandbox so Compose globals cannot retain another test class's Choreographer.
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class SnackbarEventsTest {
    @Test fun dismissBeforeAppearanceReportsRemovalWithoutImpressionOrClose() = runBlocking {
        val h = Harness()
        try {
            val view = h.show()
            repeat(8) { h.composeWithoutDrawing() }
            val results = mutableListOf<Boolean>()
            h.sdk.dismissInApp(h.content.contentId) {
                assertNull("Completion must follow physical removal", view.parent)
                results += it
            }
            h.flush(); h.awaitTrackingJobs(); h.flush()
            assertEquals(listOf(true), results)
            assertNull(view.parent)
            assertEquals(0, h.callbacks.count { it is ContentCloseEvent })
            assertEquals(0, h.callbacks.count { it is ContentImpressionEvent })
            assertEquals(0, h.paths.count { it == "/close" || it == "/impression" })
            h.advanceWithoutDrawing(1500); h.awaitTrackingJobs(); h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertEquals(0, h.paths.count { it == "/visible" })
            assertNotNull(h.reserveAgain())
            h.sdk.dismissInApp(h.content.contentId) { results += it }; h.flush()
            assertEquals(listOf(true, false), results)
        } finally { h.close() }
    }

    @Test fun dismissUnappearedSnackbarWithCountedSharedOpeningReleasesReservation() = runBlocking {
        val h = Harness()
        var count = 0
        val opening = ModalImpressionSession { count++; true }
        opening.contentFrame(true) // An earlier modal step already qualified this opening.
        try {
            val view = h.show(opening)
            repeat(8) { h.composeWithoutDrawing() }
            var removed: Boolean? = null
            h.sdk.dismissInApp(h.content.contentId) { removed = it }; h.flush()
            assertEquals(true, removed)
            assertNull(view.parent)
            assertEquals(0, h.callbacks.count { it is ContentCloseEvent || it is ContentImpressionEvent })
            assertEquals(1, count)
            assertNotNull("Silent removal still releases the UI reservation", h.reserveAgain())
        } finally { h.close() }
    }

    @Test fun dismissAfterAppearanceReportsRemovalAndExactlyOneClose() = runBlocking {
        val h = Harness()
        try {
            val view = h.show(); h.drawContent(); h.awaitPath("/impression")
            val results = mutableListOf<Boolean>()
            h.sdk.dismissInApp(h.content.contentId) {
                assertNull("Completion must follow physical removal", view.parent)
                results += it
            }
            h.flush(); h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertEquals(listOf(true), results)
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertEquals(1, h.callbacks.count { it is ContentImpressionEvent })
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.paths.count { it == "/impression" })
            h.sdk.dismissInApp(h.content.contentId) { results += it }; h.flush()
            assertEquals(listOf(true, false), results)
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertEquals(1, h.paths.count { it == "/close" })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }

    @Test fun crossReviewFocusLossBetweenFramesRestartsVisibilityClock() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.advanceWithoutDrawing(600)
            h.focus(false)
            // Focus can be lost and regained between two composition frames.
            h.focus(true)
            h.advanceWithoutDrawing(600)
            h.awaitTrackingJobs(); h.flush()
            assertEquals("Refocus needs its own uninterrupted 1 second", 0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            repeat(8) { h.composeWithoutDrawing() }
            h.advanceWithoutDrawing(1100); h.awaitPath("/visible"); h.awaitVisibleCallback(); h.awaitTrackingJobs()
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.focus(false); h.focus(true)
            repeat(8) { h.composeWithoutDrawing() }
            h.advanceWithoutDrawing(1100); h.awaitTrackingJobs()
            assertEquals("Refocus cannot count a completed opening twice", 1, h.callbacks.count { it is ContentVisibleImpressionEvent })
        } finally { h.close() }
    }

    @Test fun presentationLockAfterDrawKeepsPendingWrimp() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.sdk.lockPresentation(); h.advanceWithoutDrawing(1500)
            h.awaitTrackingJobs(); h.awaitPath("/visible"); h.flush()
            assertEquals(1, h.paths.count { it == "/impression" })
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.awaitPath("/visible")
        } finally { h.close() }
    }
    @Test fun newScreenAfterDrawKeepsPendingWrimp() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.trackNewScreen(); h.advanceWithoutDrawing(1500)
            h.awaitPath("/visible"); h.flush()
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.awaitPath("/visible")
        } finally { h.close() }
    }
    @Test fun newScreenBeforeDrawStillImpressesBeforeAutomaticClose() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.trackNewScreen(); h.drawContent()
            h.advanceWithoutDrawing(1500)
            h.awaitPath("/impression"); h.awaitPath("/visible"); h.flush()
            assertEquals(1, h.callbacks.count { it is ContentImpressionEvent })
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.advanceWithoutDrawing(3500); h.awaitTrackingJobs()
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            h.awaitPath("/impression"); h.awaitPath("/visible")
        } finally { h.close() }
    }
    @Test fun focusAfterLastDrawCountsWithoutAnotherDraw() = runBlocking {
        val h = Harness()
        try {
            h.focus(false); h.show(); h.drawContent()
            assertEquals(0, h.callbacks.count { it is ContentImpressionEvent })
            h.focus(true); repeat(8) { h.composeWithoutDrawing() }
            h.advanceWithoutDrawing(1500)
            h.awaitPath("/impression"); h.awaitPath("/visible"); h.flush()
            assertEquals(1, h.callbacks.count { it is ContentImpressionEvent })
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.awaitPath("/impression"); h.awaitPath("/visible")
        } finally { h.close() }
    }
    @Test fun templateOneDoesNotImpressBeforeItsFirstDraw() = runBlocking { noPreDrawImpression(TemplateSystemName.SNACKBAR_1) }
    @Test fun templateTwoDoesNotImpressBeforeItsFirstDraw() = runBlocking { noPreDrawImpression(TemplateSystemName.SNACKBAR_2) }
    private suspend fun noPreDrawImpression(template: TemplateSystemName) {
        val h = Harness(template)
        try {
            val view = h.show()
            repeat(8) { h.composeWithoutDrawing() }
            h.sdk.dismissInApp(h.content.contentId); h.flush(); h.awaitTrackingJobs(); h.flush()
            assertEquals(0, h.paths.count { it == "/close" })
            assertEquals(0, h.callbacks.count { it is ContentCloseEvent })
            assertNull(view.parent)
            assertEquals("Composition alone must not count as appearance", 0, h.paths.count { it == "/impression" })
            h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun actualDrawImpressesOnceAcrossRedrawAndForegroundChanges() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.awaitPath("/impression")
            h.drawContent(); h.flush()
            assertEquals(1, h.paths.count { it == "/impression" })
            h.pauseAndResume(); h.drawContent(); h.flush()
            assertEquals(1, h.paths.count { it == "/impression" })
        } finally { h.close() }
    }
    @Test fun qualifiedVisibilityStagesDurableWrimpBeforeTheRealGetCompletes() = runBlocking {
        val h = Harness()
        try {
            h.blockVisible = true
            h.show(); h.drawContent()
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.advanceWithoutDrawing(900)
            assertEquals("Visibility must last a full second", 0, h.paths.count { it == "/visible" })
            h.advanceWithoutDrawing(200)
            h.awaitPath("/visible"); h.flush()
            assertTrue("A real pending GET must have a durable entry", h.store.all().any { it.contentId == h.content.contentId })
            assertEquals(1, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.drawContent(); h.advanceWithoutDrawing(1100)
            assertEquals(1, h.paths.count { it == "/visible" })
            h.sdk.dismissInApp(h.content.contentId); h.flush()
            assertNull("Visible durable reservation remains protected until ACK", h.reserveAgain())
            h.release.countDown()
            withTimeout(30_000) { while (h.store.all().isNotEmpty()) { h.flush(); delay(5) } }
            assertNotNull("ACK releases durable protection", h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun snackbarUsesTheSuppliedOpeningAndCannotCountItTwice() = runBlocking {
        val h = Harness()
        var count = 0
        val opening = ModalImpressionSession { count++; true }
        try {
            h.show(opening); h.drawContent(); h.advanceWithoutDrawing(1100)
            assertEquals("showBackendContent's opening must reach the snackbar detector", 1, count)
            opening.contentFrame(true); opening.contentFrame(true); opening.explicitClose()
            assertEquals("Later modal-step confirmations share this opening", 1, count)
        } finally { h.close() }
    }
    @Test fun closingBeforeTheVisibilityIntervalDoesNotSendWrimp() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.sdk.dismissInApp(h.content.contentId); h.flush()
            h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertTrue(h.store.all().isEmpty()); assertTrue(h.store.staged().isEmpty())
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun changingUserDuringVisibilityWaitCannotSendOldWrimp() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.sdk.setUser("different-user", "different-session")
            h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertTrue(h.store.all().isEmpty()); assertTrue(h.store.staged().isEmpty())
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun backgroundBeforeQualificationClosesWithoutWrimpAndReleasesReservation() = runBlocking {
        val h = Harness()
        try {
            val view = h.show(); h.drawContent(); h.background(); h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertNull(view.parent)
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun closeActionAndOnCloseSendOneUrlAndOneHostCallback() = runBlocking {
        val h = Harness(TemplateSystemName.SNACKBAR_2)
        try {
            h.show(); h.drawContent(); h.clickAction()
            h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun autoWithoutOnCloseSendsOnlyTheCallbackAndReleasesReservation() = runBlocking {
        val h = Harness(hasOnClose = false)
        try {
            val view = h.show(); h.drawContent(); h.awaitPath("/impression"); h.advanceWithoutDrawing(5000)
            h.awaitPath("/visible")
            withTimeout(30_000) {
                while (h.store.all().isNotEmpty() || h.store.staged().isNotEmpty()) { h.flush(); delay(5) }
            }
            h.flush()
            assertEquals(1, h.callbacks.count { it is ContentImpressionEvent })
            assertNull(view.parent)
            assertEquals(0, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun backWithoutOnCloseSendsOnlyTheCallbackAndReleasesReservation() = runBlocking {
        val h = Harness(hasOnClose = false)
        try {
            val view = h.show(); h.drawContent(); h.awaitPath("/impression"); h.pressBack(); h.flush()
            assertEquals(1, h.callbacks.count { it is ContentImpressionEvent })
            assertNull(view.parent)
            assertEquals(0, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun closeButtonWithoutOnCloseSendsItsOwnUrlOnce() = runBlocking {
        val h = Harness(TemplateSystemName.SNACKBAR_2, hasOnClose = false)
        try {
            h.show(); h.drawContent(); h.clickAction(); h.awaitPath("/close"); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun destroyedActivityShowFailureReleasesReservation() = runBlocking {
        val h = Harness()
        try { h.destroyActivity(); assertFalse(h.tryShow()); assertNotNull(h.reserveAgain()) } finally { h.close() }
    }
    @Test fun focusLossCannotConsumeVisibilityAndRefocusStartsANewInterval() = runBlocking {
        val h = Harness()
        try {
            h.show(); h.drawContent(); h.focus(false); h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            h.focus(true); h.drawContent(); h.advanceWithoutDrawing(1100); h.awaitPath("/visible")
            assertEquals(1, h.paths.count { it == "/visible" })
        } finally { h.close() }
    }
    @Test fun unknownTemplateShowFailureReleasesReservation() = runBlocking {
        val h = Harness(TemplateSystemName.UNKNOWN)
        try { assertFalse(h.tryShow()); assertNotNull(h.reserveAgain()) } finally { h.close() }
    }
    @Test fun unknownSnackbarHooksAndButtonsCannotSendForeignUrls() = runBlocking {
        val h = Harness(unknownActions = true)
        try {
            h.show(); h.drawContent()
            h.sdk.onClickHandler(OnClickModel.fromJson(mapOf("action" to "future", "closeOnClick" to false)), h.content, h.campaign, h.context)
            h.sdk.dismissInApp(h.content.contentId); h.flush(); h.awaitTrackingJobs()
            assertFalse(h.paths.toString(), "/foreign" in h.paths)
        } finally { h.close() }
    }

    @Test fun resetDuringVisibilityWaitCannotSendOldWrimp() = runBlocking { identityChange { it.resetUser() } }
    @Test fun restoreDuringVisibilityWaitCannotSendOldWrimp() = runBlocking { identityChange { it.restoreUserId("replacement") } }
    private suspend fun identityChange(change: suspend (GravitySDK) -> Unit) {
        val h = Harness()
        try {
            h.show(); h.drawContent(); change(h.sdk); h.advanceWithoutDrawing(1500)
            assertEquals(0, h.paths.count { it == "/visible" })
            h.flush()
            assertEquals(0, h.callbacks.count { it is ContentVisibleImpressionEvent })
            assertTrue(h.store.all().isEmpty()); assertTrue(h.store.staged().isEmpty())
            assertNotNull(h.reserveAgain())
        } finally { h.close() }
    }
    @Test fun missingFrameUiShowFailureReleasesReservation() = runBlocking {
        val h = Harness(frameUi = false)
        try { assertFalse(h.tryShow()); assertNotNull(h.reserveAgain()) } finally { h.close() }
    }
    @Test fun openStepFromVisibleSnackbarToModalKeepsOneDurableOpening() = runBlocking {
        val h = Harness(TemplateSystemName.SNACKBAR_2, openStep = true)
        try {
            h.blockVisible = true
            val view = h.show()
            val opening = h.openingFor(view)
            assertNotNull("Snackbar must own the backend opening", opening)
            h.drawContent(); h.advanceWithoutDrawing(1100); h.awaitPath("/visible")
            h.clickAction(); h.drawModalStep(); h.advanceWithoutDrawing(1100)
            assertSame("OPEN_STEP must pass the exact same opening to the modal", opening, h.openingFor(h.currentOverlay()))
            assertEquals("Snackbar and modal step must share their WRIMP", 1, h.paths.count { it == "/visible" })
            assertEquals(1, h.store.all().size)
            assertEquals("snackbar", h.store.all().single().contentId)
        } finally { h.close() }
    }

    private class Harness(template: TemplateSystemName = TemplateSystemName.SNACKBAR_1, hasOnClose: Boolean = true, unknownActions: Boolean = false, frameUi: Boolean = true, openStep: Boolean = false) {
        private val reporting = Logger.errorReportingEnabled
        private val freshProcess = UserIdTestProcess.start()
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        private var frameNanos = System.nanoTime()
        private val recomposer = Recomposer(scope.coroutineContext)
        val paths = CopyOnWriteArrayList<String>()
        val callbacks = CopyOnWriteArrayList<TrackingEvent>()
        val context get() = activity.get()
        suspend fun trackNewScreen() {
            sdk.trackView(PageContext(ContextType.OTHER, emptyList(), "/next-screen"), context)
            awaitTrackingJobs(); flush()
        }
        fun pressBack() = activity.get().onBackPressedDispatcher.onBackPressed()
        @Volatile var blockVisible = false
        val release = CountDownLatch(1)
        private val server = HeadlessRecordingServer { path, _ ->
            paths += path
            if (path == "/visible" && blockVisible) check(release.await(30, TimeUnit.SECONDS))
            200 to "{}"
        }
        val sdk: GravitySDK
        val campaign: Campaign
        val content: CampaignContent
        private val campaignId = "snackbar-campaign"
        init {
            Logger.errorReportingEnabled = false
            activity.get().setContentView(android.widget.FrameLayout(activity.get()))
            activity.visible()
            activity.windowFocusChanged(true)
            server.start()
            GravitySDK.initialize(activity.get(), "snackbar-observation-${java.util.UUID.randomUUID()}", "section", { callbacks += it })
            sdk = GravitySDK.instance
            sdk.setOptions(null, null, server.url)
            runBlocking { sdk.restoreUserId("snackbar-user") }
            sdk.setUser("user", "session")
            // Direct backend tests bypass prepareAndShowBackendContent's durable-store load barrier.
            runBlocking(Dispatchers.IO) { store.load() }
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            activity.get().window.decorView.compositionContext = recomposer
            content = CampaignContent("snackbar", template, DeliveryMethod.SNACK_BAR, "native", 1,
                Variables.fromJson(mapOf(
                    "frameUI" to (if (frameUi) mapOf("container" to mapOf("style" to emptyMap<String, Any>())) else null),
                    "elements" to listOf(mapOf("type" to "text", "text" to "Hello"),
                        mapOf("type" to "button", "text" to "Action", "onClick" to mapOf("action" to if (openStep) "open_step" else "close", "step" to 2))),
                    "onImpression" to mapOf("action" to if (unknownActions) "future" else "impression"),
                    "onClose" to (if (hasOnClose) mapOf("action" to if (unknownActions) "future" else "close") else null)).filterValues { it != null }),
                null, null, null, listOf(
                    Event(Action.IMPRESSION, listOf("${server.url}/impression")),
                    Event(Action.VISIBLE_IMPRESSION, listOf("${server.url}/visible")),
                    Event(Action.CLOSE, listOf("${server.url}/close")),
                    Event(Action.UNKNOWN, listOf("${server.url}/foreign"))))
            campaign = Campaign(null, listOf(CampaignVariation(campaignId, "e", "v", "d", listOf(content,
                content.copy(contentId = "step-two", step = 2, templateSystemName = null, deliveryMethod = DeliveryMethod.MODAL)))))
        }
        private var overlay: View? = null
        val store: VisibleImpressionStore get() {
            val field = GravitySDK::class.java.getDeclaredField("visibleImpressionStore\$delegate").apply { isAccessible = true }
            return (field.get(sdk) as Lazy<*>).value as VisibleImpressionStore
        }
        fun tryShow(opening: ModalImpressionSession? = null): Boolean {
            if (content.variables?.onClose == null) assertFalse(content.rawVariables.containsKey("onClose"))
            val request = sdk.presentationGate.beginRequest("/snackbar", true)
            val reservation = requireNotNull(sdk.presentationGate.reserve(campaignId, request))
            return sdk.presentationGate.present(reservation, listOf(content.contentId)) {
                val shown = GravitySDK::class.java.getDeclaredMethod("showBackendContent", Activity::class.java,
                    CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
                    .apply { isAccessible = true }.invoke(sdk, activity.get(), content, campaign, null, opening) as Boolean
                if (shown) overlay = (activity.get().window.decorView as ViewGroup).getChildAt(0)
                shown
            }
        }
        fun show(opening: ModalImpressionSession? = null): View {
            assertTrue(tryShow(opening)); return requireNotNull(overlay)
        }
        suspend fun drawContent() {
            repeat(8) { composeWithoutDrawing() }
            drawView(requireNotNull(overlay))
        }
        private suspend fun drawView(view: View) {
            val width = activity.get().window.decorView.width
            val height = activity.get().window.decorView.height
            assertTrue("Real activity viewport must be laid out", width > 0 && height > 0)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            view.viewTreeObserver.dispatchOnPreDraw()
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap)); bitmap.recycle()
            repeat(3) { composeWithoutDrawing() }
        }
        fun currentOverlay(): View = (activity.get().window.decorView as ViewGroup).getChildAt(0)
        fun openingFor(view: View): ModalImpressionSession? {
            val info = View::class.java.getDeclaredMethod("getListenerInfo").apply { isAccessible = true }.invoke(view)
            val field = info.javaClass.getDeclaredField("mOnAttachStateChangeListeners").apply { isAccessible = true }
            val listeners = field.get(info) as? List<*> ?: return null
            return listeners.filterNotNull().flatMap { listener ->
                listener.javaClass.declaredFields.map { it.isAccessible = true; it.get(listener) }
            }.filterIsInstance<ModalImpressionSession>().firstOrNull()
        }
        fun clickAction() {
            fun roots(view: View): Sequence<androidx.compose.ui.platform.ViewRootForTest> = sequence {
                if (view is androidx.compose.ui.platform.ViewRootForTest) yield(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(roots(view.getChildAt(index)))
            }
            fun nodes(node: androidx.compose.ui.semantics.SemanticsNode): Sequence<androidx.compose.ui.semantics.SemanticsNode> = sequence {
                yield(node); node.children.forEach { yieldAll(nodes(it)) }
            }
            val node = roots(requireNotNull(overlay)).flatMap { nodes(it.semanticsOwner.unmergedRootSemanticsNode) }
                .first { it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            assertTrue(requireNotNull(node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action).invoke())
            flush()
        }
        suspend fun drawModalStep() {
            withTimeout(30_000) {
                while (org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing != true) composeWithoutDrawing()
            }
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val decor = requireNotNull(dialog.window).decorView
            val root = View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }.invoke(decor)
            org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowViewRootImpl>(root).callWindowFocusChanged(true)
            repeat(8) { composeWithoutDrawing() }
            drawView(decor)
        }
        fun destroyActivity() { activity.pause().stop().destroy(); flush() }
        fun focus(value: Boolean) { activity.windowFocusChanged(value); flush() }
        fun background() { activity.pause().stop(); flush() }
        fun pauseAndResume() { activity.pause(); activity.resume(); flush() }
        fun reserveAgain() = sdk.presentationGate.reserve(campaignId, sdk.presentationGate.beginRequest("/snackbar", false))
        fun flush() = shadowOf(Looper.getMainLooper()).idle()
        suspend fun composeWithoutDrawing() {
            flush()
            // Deliver external focus/state writes to this test-owned recomposer explicitly.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            flush()
            frameNanos += TimeUnit.MILLISECONDS.toNanos(16)
            clock.sendFrame(frameNanos)
            flush(); delay(5)
        }
        suspend fun advanceWithoutDrawing(millis: Long) {
            shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
            composeWithoutDrawing()
        }
        suspend fun awaitTrackingJobs() {
            withTimeout(30_000) {
                for ((owner, name) in listOf(ContentEventService.instance to "scope", sdk to "ioScope")) {
                    val jobs = (owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as CoroutineScope)
                        .coroutineContext[Job]!!.children.toList()
                    while (jobs.any { !it.isCompleted }) { flush(); delay(5) }
                    jobs.joinAll()
                }
            }
        }
        suspend fun awaitVisibleCallback() {
            withTimeout(30_000) {
                while (callbacks.none { it is ContentVisibleImpressionEvent }) { flush(); delay(5) }
            }
        }
        suspend fun awaitPath(path: String) {
            withTimeout(30_000) { while (path !in paths) { flush(); delay(5) } }
        }
        fun close() {
            release.countDown()
            sdk.dismissInApp("step-two"); flush()
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.dismiss()
            sdk.dispose(); UserIdTestProcess.stop(sdk)
            recomposer.cancel(); scope.cancel()
            if (!activity.get().isDestroyed) activity.pause().stop().destroy()
            server.stop(); Logger.errorReportingEnabled = reporting
        }
    }
}
