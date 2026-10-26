package ai.gravityfield.gravity_sdk.utils

import org.junit.Assert.*
import org.junit.Test

class ModalImpressionSessionTest {
    private fun fastClose(delayMillis: Long) = kotlinx.coroutines.runBlocking {
        var count = 0
        var persistedBeforeDismiss = false
        val opening = ModalImpressionSession { count++; true }
        opening.attach()
        opening.frameDrawn(active = true) // Confirmed content draw; detector's posted callback is pending.
        kotlinx.coroutines.delay(delayMillis)
        val presentation = InAppPresentation(performDismiss = { complete ->
            opening.explicitClose()
            persistedBeforeDismiss = opening.hasFired
            opening.detach { fail("Visible opening must keep protection") }
            complete(true)
        }, didDismiss = {})
        presentation.close()
        opening.contentFrame(active = true) // Delayed detector / next step of this opening.
        presentation.close()
        assertTrue(persistedBeforeDismiss)
        assertEquals(1, count)
    }

    @Test fun explicitCloseAt100msPersistsBeforeDismissal() = fastClose(100)
    @Test fun explicitCloseAt200msPersistsBeforeDismissal() = fastClose(200)
    @Test fun explicitCloseAt300msPersistsBeforeDismissal() = fastClose(300)
    @Test fun explicitCloseAt400msPersistsBeforeDismissal() = fastClose(400)

    @Test fun stepReplacementKeepsOneOpeningAndDoesNotAbandonItsReservation() {
        var count = 0
        var abandoned = 0
        val opening = ModalImpressionSession { count++; true }
        opening.attach()
        opening.attach() // Prepared next step mounts before old step is removed.
        opening.detach { abandoned++ }
        assertEquals(0, abandoned)
        opening.contentFrame(active = true)
        opening.contentFrame(active = true)
        opening.detach { abandoned++ }
        assertEquals(0, abandoned)
        assertEquals(1, count)
    }

    @Test fun navigationWithoutAppearanceReleasesReservationWithoutImpression() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("campaign", request))
        gate.present(reservation, listOf("content")) { true }
        var count = 0
        val opening = ModalImpressionSession { count++; true }
        opening.attach()
        opening.detach { gate.closed("content") }
        assertEquals(0, count)
        assertNotNull(gate.reserve("campaign", request))
    }

    @Test fun noFrameOrInactiveFrameDoesNotCountAsAppearance() {
        var count = 0
        val opening = ModalImpressionSession { count++; true }
        opening.explicitClose()
        opening.contentFrame(active = false)
        opening.explicitClose()
        assertEquals(0, count)
        assertFalse(opening.hasFired)
    }

    @Test fun failedPersistenceCanBeRetriedByCloseWithoutDuplicateAfterSuccess() {
        var attempts = 0
        val opening = ModalImpressionSession { ++attempts > 1 }
        opening.contentFrame(active = true)
        assertFalse(opening.hasFired)
        opening.explicitClose()
        assertTrue(opening.hasFired)
        opening.explicitClose()
        assertEquals(2, attempts)
    }
    @Test fun backgroundOrNavigationAfterDrawBeforeConfirmationCannotFireLateCallbacks() {
        var count = 0
        var released = 0
        val opening = ModalImpressionSession { count++; true }
        opening.attach()
        opening.frameDrawn(active = true)
        opening.detach { released++ }
        opening.contentFrame(active = true) // Stale posted callback after disposal.
        opening.explicitClose() // Stale registry callback after disposal.
        assertEquals(0, count)
        assertEquals(1, released)
    }

    @Test fun countedOpeningSurvivesBackgroundAndStillClosesOnceExplicitly() {
        var impressions = 0
        var closes = 0
        val opening = ModalImpressionSession { impressions++; true }
        opening.attach()
        opening.contentFrame(active = true)
        opening.background { closes++ }
        assertEquals(0, closes)
        assertFalse(opening.isAbandoned)
        opening.contentFrame(active = true)
        opening.explicitClose()
        assertEquals(1, impressions)
    }

    @Test fun unconfirmedBackgroundCloseRunsCloseSemanticsWithoutCreatingAnImpression() {
        var impressions = 0
        var closes = 0
        var released = 0
        val opening = ModalImpressionSession { impressions++; true }
        opening.attach()
        opening.frameDrawn(active = true)
        opening.background {
            opening.explicitClose() // Same production dismissal handler; the fallback must be disabled.
            opening.detach { released++ }
            closes++
        }
        opening.background { closes++ }
        opening.contentFrame(active = true)
        assertEquals(0, impressions)
        assertEquals(1, closes)
        assertEquals(1, released)
    }

}
