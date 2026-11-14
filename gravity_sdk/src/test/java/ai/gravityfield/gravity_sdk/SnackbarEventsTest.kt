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
import androidx.compose.ui.semantics.getOrNull
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
    @Test fun fullscreenCloseActionAndOnCloseSendOneUrlAndOneHostCallback() = runBlocking {
        val h = Harness(delivery = DeliveryMethod.FULL_SCREEN)
        try {
            h.show(); h.drawContent(); h.clickAction()
            h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
        } finally { h.close() }
    }
    @Test fun modalCloseActionAndOnCloseSendOneUrlAndOneHostCallback() = sharedModalClose(DeliveryMethod.MODAL)
    @Test fun sheetCloseActionAndOnCloseSendOneUrlAndOneHostCallback() = sharedModalClose(DeliveryMethod.BOTTOM_SHEET)
    private fun sharedModalClose(delivery: DeliveryMethod) = runBlocking {
        val h = Harness(delivery = delivery)
        try {
            h.show(); h.drawModalStep(); h.clickAction(inDialog = true)
            repeat(60) { h.composeWithoutDrawing() }
            h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
        } finally { h.close() }
    }
    @Test fun sheetCloseStillReportsOnceWhenHostIsDestroyedDuringHide() = runBlocking {
        val h = Harness(delivery = DeliveryMethod.BOTTOM_SHEET)
        try {
            h.show(); h.drawModalStep()
            h.clickAction(inDialog = true)
            // hide() has started but cannot complete until more animation frames.
            assertEquals(0, h.callbacks.count { it is ContentCloseEvent })
            h.destroyActivity()
            repeat(60) { h.composeWithoutDrawing() }
            h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
        } finally { h.close() }
    }

    @Test fun repeatedSheetCloseTapDuringHideDoesNotSendTwice() = runBlocking {
        val h = Harness(delivery = DeliveryMethod.BOTTOM_SHEET)
        try {
            h.show(); h.drawModalStep()
            val click = h.clickAction(inDialog = true)
            assertTrue(click.invoke()); h.flush()
            repeat(60) { h.composeWithoutDrawing() }
            h.awaitPath("/close"); h.awaitTrackingJobs(); h.flush()
            assertTrue(click.invoke()); h.flush(); h.awaitTrackingJobs()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
        } finally { h.close() }
    }

    @Test fun fullscreenCloseWithoutVisibleImpressionUrlStillReportsOnce() = closeWithoutVisibleUrl(DeliveryMethod.FULL_SCREEN)
    @Test fun modalCloseWithoutVisibleImpressionUrlStillReportsOnce() = closeWithoutVisibleUrl(DeliveryMethod.MODAL)
    @Test fun sheetCloseWithoutVisibleImpressionUrlStillReportsOnce() = closeWithoutVisibleUrl(DeliveryMethod.BOTTOM_SHEET)
    private fun closeWithoutVisibleUrl(delivery: DeliveryMethod) = runBlocking {
        val h = Harness(delivery = delivery, hasVisibleUrl = false)
        try {
            h.show()
            if (delivery == DeliveryMethod.FULL_SCREEN) h.drawContent() else h.drawModalStep()
            h.clickAction(inDialog = delivery != DeliveryMethod.FULL_SCREEN)
            repeat(60) { h.composeWithoutDrawing() }
            h.awaitTrackingJobs(); h.flush()
            assertEquals(1, h.paths.count { it == "/close" })
            assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
        } finally { h.close() }
    }

    @Test fun modalDismissalBeforeFirstDrawRemainsSilentWithOrWithoutVisibleUrl() = runBlocking {
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) {
            for (hasVisibleUrl in listOf(true, false)) {
                val h = Harness(delivery = delivery, hasVisibleUrl = hasVisibleUrl)
                try {
                    h.show()
                    h.sdk.dismissInApp(h.content.contentId); h.flush()
                    repeat(60) { h.composeWithoutDrawing() }
                    h.awaitTrackingJobs(); h.flush()
                    assertEquals(0, h.paths.count { it == "/close" })
                    assertEquals(0, h.callbacks.count { it is ContentCloseEvent })
                } finally { h.close() }
            }
        }
    }

    @Test fun externallyDismissedNativeDialogReportsClose() = runBlocking {
        for (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) {
            for (hasVisibleUrl in listOf(true, false)) {
                val h = Harness(delivery = delivery, hasVisibleUrl = hasVisibleUrl)
                try {
                    h.show(); h.drawModalStep(); h.hostRemove(HostRemoval.DIALOG)
                    repeat(60) { h.composeWithoutDrawing() }; h.awaitTrackingJobs(); h.flush()
                    assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
                    assertEquals(1, h.paths.count { it == "/close" })
                } finally { h.close() }
            }
        }
    }

    @Test fun externallyRemovedOverlayWithoutWrimpReportsClose() = runBlocking {
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET, DeliveryMethod.SNACK_BAR)) {
            val h = Harness(delivery = delivery, hasVisibleUrl = false)
            try {
                h.show()
                if (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) h.drawModalStep() else h.drawContent()
                h.hostRemove(HostRemoval.OVERLAY)
                repeat(60) { h.composeWithoutDrawing() }; h.awaitTrackingJobs(); h.flush()
                assertEquals(1, h.callbacks.count { it is ContentCloseEvent })
                assertEquals(1, h.paths.count { it == "/close" })
            } finally { h.close() }
        }
    }

    private enum class HostRemoval { OVERLAY, FINISH, DESTROY, RECREATE, DIALOG }

    @Test fun hostRemovalReportsExactlyOnceOnlyAfterAppearance() = runBlocking {
        val failures = mutableListOf<String>()
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET, DeliveryMethod.SNACK_BAR)) {
            for (removal in HostRemoval.entries) {
                if (removal == HostRemoval.DIALOG && delivery !in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) continue
                for (appeared in listOf(false, true)) {
                    val h = Harness(delivery = delivery)
                    try {
                        h.show()
                        if (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) {
                            if (appeared) h.drawModalStep() else repeat(8) { h.composeWithoutDrawing() }
                        } else if (appeared) h.drawContent()
                        val dialog = if (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) org.robolectric.shadows.ShadowDialog.getLatestDialog() else null
                        h.hostRemove(removal)
                        repeat(60) { h.composeWithoutDrawing() }
                        h.awaitTrackingJobs(); h.flush()
                        println("HOST_MATRIX $delivery $removal appeared=$appeared close=${h.callbacks.count { it is ContentCloseEvent }} url=${h.paths.count { it == "/close" }} overlay=${h.isOverlayAttached()} dialog=${dialog?.isShowing} dialogAttached=${dialog?.window?.decorView?.isAttachedToWindow} changing=${h.changingConfigurations()} gateFree=${h.reserveAgain() != null}")
                        if (removal != HostRemoval.RECREATE) {
                            val expected = if (appeared) 1 else 0
                            if (h.callbacks.count { it is ContentCloseEvent } != expected || h.paths.count { it == "/close" } != expected) failures += "$delivery/$removal appeared=$appeared"
                            if (h.isOverlayAttached()) failures += "$delivery/$removal left an overlay"
                            var removedAgain: Boolean? = null
                            h.sdk.dismissInApp(h.content.contentId) { removedAgain = it }; h.flush()
                            assertEquals("External removal must retire the registry entry", false, removedAgain)
                            assertFalse("External removal must release the presentation guard", h.isRegistered())
                        }
                    } finally { h.close() }
                }
            }
        }
        assertEquals("Close mismatch cases", emptyList<String>(), failures)
    }

    @Test fun destroyedHostCannotKeepTheShowingGuardLocked() = runBlocking {
        val h = Harness(delivery = DeliveryMethod.MODAL, hasVisibleUrl = false)
        try {
            h.show(); h.drawModalStep()
            h.hostRemove(HostRemoval.DESTROY)
            repeat(8) { h.composeWithoutDrawing() }
            assertFalse("A destroyed host has no showing window", h.isRegistered())
        } finally { h.close() }
    }

    @Test fun configurationRecreationKeepsTheSameShowing() = runBlocking {
        val failures = mutableListOf<String>()
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET, DeliveryMethod.SNACK_BAR)) {
            for (appearedBefore in listOf(true, false)) {
                val h = Harness(delivery = delivery)
                val case = "$delivery appearedBefore=$appearedBefore"
                val native = delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)
                try {
                    val oldOverlay = h.show()
                    val opening = h.openingFor(oldOverlay)
                    if (appearedBefore) {
                        if (native) h.drawModalStep() else h.drawContent()
                        h.advanceWithoutDrawing(1100); h.awaitTrackingJobs()
                    } else repeat(8) { h.composeWithoutDrawing() }
                    val oldActivity = h.context
                    val oldDialog = if (native) org.robolectric.shadows.ShadowDialog.getLatestDialog() else null

                    h.recreate()
                    assertTrue("$case must be a configuration change", oldActivity.isChangingConfigurations)
                    assertNotSame(oldActivity, h.context)
                    if (native) h.drawModalStep() else h.drawContent()
                    h.advanceWithoutDrawing(1100)
                    repeat(20) { h.composeWithoutDrawing() }
                    h.awaitTrackingJobs(); h.flush()

                    val restored = h.currentOverlay()
                    if (restored === oldOverlay || !restored.isAttachedToWindow) failures += "$case not restored in the new Activity"
                    if (h.openingFor(restored) !== opening) failures += "$case restored with another opening"
                    if (native && org.robolectric.shadows.ShadowDialog.getLatestDialog().let { it === oldDialog || !it.isShowing }) failures += "$case native window not restored"
                    if (h.callbacks.count { it is ContentCloseEvent } != 0 || h.paths.count { it == "/close" } != 0) failures += "$case closed on rotation"
                    if (h.callbacks.count { it is ContentImpressionEvent } != 1 || h.paths.count { it == "/impression" } != 1) failures += "$case impression=${h.paths.count { it == "/impression" }}"
                    if (h.paths.count { it == "/visible" } > 1) failures += "$case visible=${h.paths.count { it == "/visible" }}"
                    if (opening?.hasAppeared != true) failures += "$case did not appear in the new Activity"
                    if (!h.isRegistered()) failures += "$case registry released on rotation"
                    if (h.tryShowDirect()) failures += "$case same content stacked after rotation"
                    if (h.tryShowDirect(content = h.content.copy(contentId = "replacement"))) failures += "$case same campaign stacked after rotation"

                    var dismissed: Boolean? = null
                    h.sdk.dismissInApp(h.content.contentId) { dismissed = it }
                    repeat(20) { h.composeWithoutDrawing() }
                    h.awaitTrackingJobs(); h.flush()
                    if (dismissed != true) failures += "$case dismiss after rotation=$dismissed"
                    if (h.callbacks.count { it is ContentCloseEvent } != 1 || h.paths.count { it == "/close" } != 1) failures += "$case close after rotation=${h.paths.count { it == "/close" }}"
                    // Native modals confirm WRIMP at the latest on explicit close; still exactly one.
                    if (h.paths.count { it == "/visible" } != 1) failures += "$case visible after close=${h.paths.count { it == "/visible" }}"
                    if (restored.isAttachedToWindow) failures += "$case restored overlay left attached"
                    if (h.isRegistered()) failures += "$case registry kept after close"
                    var again: Boolean? = null
                    h.sdk.dismissInApp(h.content.contentId) { again = it }; h.flush()
                    if (again != false) failures += "$case released twice"
                } finally { h.close() }
            }
        }
        assertEquals("Rotation mismatch cases", emptyList<String>(), failures)
    }

    @Test fun repeatedRecreationThenRealDestroyClosesOnce() = runBlocking {
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET, DeliveryMethod.SNACK_BAR)) {
            val h = Harness(delivery = delivery)
            val native = delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)
            try {
                h.show()
                if (native) h.drawModalStep() else h.drawContent()
                repeat(2) {
                    h.recreate()
                    if (native) h.drawModalStep() else h.drawContent()
                }
                h.awaitTrackingJobs()
                assertEquals("$delivery no close on rotation", 0, h.callbacks.count { it is ContentCloseEvent })
                assertEquals("$delivery one impression", 1, h.paths.count { it == "/impression" })
                h.destroyActivity()
                repeat(20) { h.composeWithoutDrawing() }
                h.awaitTrackingJobs(); h.flush()
                assertEquals("$delivery real destroy closes once", 1, h.callbacks.count { it is ContentCloseEvent })
                assertEquals("$delivery real destroy close URL once", 1, h.paths.count { it == "/close" })
                assertFalse("$delivery registry released", h.isRegistered())
            } finally { h.close() }
        }
    }

    @Test fun attachedContentCannotStackAfterGateExpiryOrDirectShow() = runBlocking {
        for (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET,
            DeliveryMethod.FULL_SCREEN, DeliveryMethod.SNACK_BAR)) {
            for (background in listOf(false, true)) {
                if (background && delivery == DeliveryMethod.SNACK_BAR) continue // closes on STOP
                val h = Harness(delivery = delivery, hasVisibleUrl = false)
                try {
                    h.expireGateImmediately()
                    h.show()
                    if (delivery in listOf(DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) h.drawModalStep()
                    else h.drawContent()
                    h.awaitTrackingJobs()
                    if (background) { h.background(); h.resume() }
                    assertFalse("$delivery background=$background automatic duplicate", h.tryShow())
                    assertFalse("$delivery direct duplicate", h.tryShowDirect())
                    assertFalse("$delivery same campaign with another content ID", h.tryShowDirect(content = h.content.copy(contentId = "replacement")))
                    h.sdk.presentationGate.acknowledgedCampaign(h.campaign.payload.first().campaignId)
                    assertFalse("$delivery acknowledged but still attached", h.tryShow())
                    h.awaitTrackingJobs()
                    assertEquals(1, h.paths.count { it == "/impression" })
                    h.sdk.dismissInApp(h.content.contentId); h.flush()
                    repeat(40) { h.composeWithoutDrawing() }
                    assertTrue("$delivery must reopen after close", h.tryShow())
                } finally { h.close() }
            }
        }
    }

    @Test fun appearedModalsWithoutVisibleUrlSurviveBackground() = runBlocking {
        val results = mutableMapOf<DeliveryMethod, Boolean>()
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) {
            val h = Harness(delivery = delivery, hasVisibleUrl = false)
            try {
                h.show()
                if (delivery == DeliveryMethod.FULL_SCREEN) h.drawContent() else h.drawModalStep()
                h.background(); repeat(60) { h.composeWithoutDrawing() }; h.awaitTrackingJobs(); h.flush()
                println("BACKGROUND_MATRIX $delivery overlay=${h.isOverlayAttached()} close=${h.callbacks.count { it is ContentCloseEvent }} url=${h.paths.count { it == "/close" }}")
                results[delivery] = h.isOverlayAttached() && h.callbacks.none { it is ContentCloseEvent } && h.paths.none { it == "/close" }
            } finally { h.close() }
        }
        assertTrue("Appeared modals must remain available on resume without WRIMP: $results", results.values.all { it })
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
    @Test fun openStepReplacesTheCurrentStepRegardlessOfCloseOnClick() = runBlocking {
        val failures = mutableListOf<String>()
        for (delivery in listOf(DeliveryMethod.FULL_SCREEN, DeliveryMethod.MODAL, DeliveryMethod.BOTTOM_SHEET)) {
            for (closeOnClick in listOf(null, true, false)) {
                val case = "$delivery closeOnClick=$closeOnClick"
                val native = delivery != DeliveryMethod.FULL_SCREEN
                val h = Harness(delivery = delivery, openStep = true, closeOnClick = closeOnClick)
                try {
                    val first = h.show()
                    val opening = h.openingFor(first)
                    if (native) h.drawModalStep() else h.drawContent()
                    h.awaitTrackingJobs()
                    val firstDialog = if (native) org.robolectric.shadows.ShadowDialog.getLatestDialog() else null
                    h.clickAction(inDialog = native)
                    h.drawModalStep()
                    repeat(30) { h.composeWithoutDrawing() }
                    h.awaitTrackingJobs(); h.flush()
                    val overlays = (h.context.window.decorView as ViewGroup).let { decor ->
                        (0 until decor.childCount).map { decor.getChildAt(it) }.filter { h.openingFor(it) != null }
                    }
                    if (first.isAttachedToWindow) failures += "$case step 1 overlay still attached"
                    if (firstDialog?.isShowing == true) failures += "$case step 1 dialog still showing"
                    if (overlays.size != 1 || h.openingFor(overlays.single()) !== opening) failures += "$case overlays=${overlays.size}"
                    if (h.callbacks.count { it is ContentCloseEvent } != 0 || h.paths.count { it == "/close" } != 0) failures += "$case step 1 reported a close"
                    var dismissedFirst: Boolean? = null
                    h.sdk.dismissInApp(h.content.contentId) { dismissedFirst = it }; h.flush()
                    if (dismissedFirst != false) failures += "$case step 1 still registered"

                    var dismissed: Boolean? = null
                    h.sdk.dismissInApp("step-two") { dismissed = it }
                    repeat(30) { h.composeWithoutDrawing() }
                    h.awaitTrackingJobs(); h.flush()
                    if (dismissed != true) failures += "$case step 2 dismiss=$dismissed"
                    if (h.callbacks.count { it is ContentCloseEvent } != 1 || h.paths.count { it == "/close" } != 1) failures += "$case closes after step 2=${h.paths.count { it == "/close" }}"
                    if (h.paths.count { it == "/visible" } > 1) failures += "$case visible=${h.paths.count { it == "/visible" }}"
                    if ((h.context.window.decorView as ViewGroup).let { d -> (0 until d.childCount).any { h.openingFor(d.getChildAt(it)) != null } }) failures += "$case overlay left after close"
                } finally { h.close() }
            }
        }
        assertEquals("Step replacement mismatch", emptyList<String>(), failures)
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

    private class Harness(template: TemplateSystemName = TemplateSystemName.SNACKBAR_1, hasOnClose: Boolean = true, unknownActions: Boolean = false, frameUi: Boolean = true, openStep: Boolean = false, delivery: DeliveryMethod = DeliveryMethod.SNACK_BAR, hasVisibleUrl: Boolean = true, closeOnClick: Boolean? = null) {
        private val reporting = Logger.errorReportingEnabled
        private val freshProcess = UserIdTestProcess.start()
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        private var frameNanos = System.nanoTime()
        private val recomposer = Recomposer(scope.coroutineContext)
        // A recreated Activity gets a new decor; keep it on this test-owned frame clock.
        private val compositionHook = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: android.os.Bundle?) {
                a.window.decorView.compositionContext = recomposer
                // Hosts set their content in onCreate; that also installs the view-tree owners.
                (a as? ComponentActivity)?.setContentView(android.widget.FrameLayout(a))
            }
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        }
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
            activity.get().application.registerActivityLifecycleCallbacks(compositionHook)
            content = CampaignContent("snackbar", template, delivery, "native", 1,
                Variables.fromJson(mapOf(
                    "frameUI" to (if (frameUi) mapOf("container" to mapOf("style" to emptyMap<String, Any>())) else null),
                    "elements" to listOf(mapOf("type" to "text", "text" to "Hello"),
                        mapOf("type" to "button", "text" to "Action", "onClick" to mapOf("action" to if (openStep) "open_step" else "close", "step" to 2, "closeOnClick" to closeOnClick).filterValues { it != null })),
                    "onImpression" to mapOf("action" to if (unknownActions) "future" else "impression"),
                    "onClose" to (if (hasOnClose) mapOf("action" to if (unknownActions) "future" else "close") else null)).filterValues { it != null }),
                null, null, null, listOf(
                    Event(Action.IMPRESSION, listOf("${server.url}/impression")),
                    Event(Action.VISIBLE_IMPRESSION, if (hasVisibleUrl) listOf("${server.url}/visible") else emptyList()),
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
            val reservation = sdk.presentationGate.reserve(campaignId, request) ?: return false
            return sdk.presentationGate.present(reservation, listOf(content.contentId)) {
                val shown = tryShowDirect(opening)
                if (shown) overlay = (activity.get().window.decorView as ViewGroup).getChildAt(0)
                shown
            }
        }
        fun isRegistered(): Boolean {
            val registry = GravitySDK::class.java.getDeclaredField("inAppPresentations")
                .apply { isAccessible = true }.get(sdk) as ai.gravityfield.gravity_sdk.utils.InAppPresentationRegistry
            return registry.contains(content.contentId) || registry.containsCampaign(campaignId)
        }
        fun expireGateImmediately() {
            sdk.presentationGate.javaClass.getDeclaredField("acknowledgementTimeoutMillis")
                .apply { isAccessible = true }.setLong(sdk.presentationGate, 0)
        }
        fun resume() { activity.start().resume(); activity.windowFocusChanged(true); flush() }
        fun tryShowDirect(opening: ModalImpressionSession? = null, content: CampaignContent = this.content): Boolean =
            GravitySDK::class.java.getDeclaredMethod("showBackendContent", Activity::class.java,
                CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
                .apply { isAccessible = true }.invoke(sdk, activity.get(), content, campaign, null, opening) as Boolean
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
        fun clickAction(inDialog: Boolean = false): () -> Boolean {
            fun roots(view: View): Sequence<androidx.compose.ui.platform.ViewRootForTest> = sequence {
                if (view is androidx.compose.ui.platform.ViewRootForTest) yield(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(roots(view.getChildAt(index)))
            }
            fun nodes(node: androidx.compose.ui.semantics.SemanticsNode): Sequence<androidx.compose.ui.semantics.SemanticsNode> = sequence {
                yield(node); node.children.forEach { yieldAll(nodes(it)) }
            }
            val rootView = if (inDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else requireNotNull(overlay)
            val node = roots(rootView).flatMap { nodes(it.semanticsOwner.unmergedRootSemanticsNode) }
                .single { candidate ->
                    candidate.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Button &&
                        nodes(candidate).any { child -> child.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text == "Action" } == true }
                }
            val click = requireNotNull(node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action)
            assertTrue(click.invoke())
            flush()
            return click
        }
        suspend fun drawModalStep() {
            repeat(8) { composeWithoutDrawing() }
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
        fun isOverlayAttached() = overlay?.isAttachedToWindow == true
        private var removedDuringConfigurationChange = false
        fun changingConfigurations() = removedDuringConfigurationChange
        fun hostRemove(removal: HostRemoval) {
            val removedActivity = activity.get()
            when (removal) {
                HostRemoval.OVERLAY -> (requireNotNull(overlay).parent as ViewGroup).removeView(overlay)
                HostRemoval.FINISH -> { activity.get().finish(); activity.pause().stop().destroy() }
                HostRemoval.DESTROY -> activity.pause().stop().destroy()
                HostRemoval.RECREATE -> activity.recreate()
                HostRemoval.DIALOG -> org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss()
            }
            removedDuringConfigurationChange = removedActivity.isChangingConfigurations
            flush()
        }
        fun recreate() {
            activity.recreate()
            activity.visible()
            activity.windowFocusChanged(true)
            flush()
            overlay = currentOverlay()
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
            activity.get().application.unregisterActivityLifecycleCallbacks(compositionHook)
            recomposer.cancel(); scope.cancel()
            if (!activity.get().isDestroyed) activity.pause().stop().destroy()
            server.stop(); Logger.errorReportingEnabled = reporting
        }
    }
}
