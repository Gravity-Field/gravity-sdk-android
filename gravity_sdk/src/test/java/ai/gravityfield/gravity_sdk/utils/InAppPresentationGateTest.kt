package ai.gravityfield.gravity_sdk.utils

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class InAppPresentationGateTest {
    @Test fun concurrentChooseReservesCampaignOnce() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val winners = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)
        repeat(100) { executor.submit { if (gate.reserve("c", request) != null) winners.incrementAndGet() } }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
    }

    @Test fun visibleCloseKeepsReservationUntilAckAndRejectsStaleResponses() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("c", request))
        assertTrue(gate.present(reservation, listOf("step1:e1", "step2:e1")) { true })
        gate.visible("step1:e1")
        gate.closed("step1:e1")
        val inFlight = gate.beginRequest("next", true)
        assertNull(gate.reserve("c", inFlight))
        gate.acknowledged("step2:e1")
        assertNull(gate.reserve("c", inFlight))
        assertNotNull(gate.reserve("c", gate.beginRequest("next", false)))
    }

    @Test fun failedPresentationAndCloseBeforeVisibilityReleaseCampaign() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val first = requireNotNull(gate.reserve("c", request))
        assertFalse(gate.present(first, listOf("c:e1")) { false })
        val second = requireNotNull(gate.reserve("c", request))
        assertTrue(gate.present(second, listOf("c:e2")) { true })
        gate.closed("c:e2")
        assertNotNull(gate.reserve("c", request))
    }

    @Test fun screenChangeReleasesDelayedChooseAndOldCancelCannotReleaseNewOwner() {
        val gate = InAppPresentationGate()
        val first = gate.beginRequest("home", true)
        val old = requireNotNull(gate.reserve("c", first))
        val second = gate.beginRequest("next", true)
        val current = requireNotNull(gate.reserve("c", second))
        gate.cancel(old)
        var shown = false
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.present(old, listOf("old")) { shown = true; true })
        assertFalse(shown)
        assertTrue(gate.present(current, listOf("new")) { true })
        assertNull(gate.reserve("c", second))
    }

    @Test fun identityResetInvalidatesOldRequestAndLateAck() {
        val gate = InAppPresentationGate()
        val oldRequest = gate.beginRequest("home", true)
        val old = requireNotNull(gate.reserve("c", oldRequest))
        assertTrue(gate.present(old, listOf("old:event")) { true })
        gate.reset()
        val request = gate.beginRequest("home", true)
        val current = requireNotNull(gate.reserve("c", request))
        assertTrue(gate.present(current, listOf("new:event")) { true })
        gate.acknowledged("old:event")
        assertFalse(gate.isCurrent(oldRequest))
        assertNull(gate.reserve("c", request))
    }

    @Test fun missingAckExpiresAndCampaignsAreIndependent() {
        var now = 0L
        val gate = InAppPresentationGate(now = { now })
        val request = gate.beginRequest("home", true)
        val first = requireNotNull(gate.reserve("c", request))
        assertTrue(gate.present(first, listOf("event")) { true })
        assertNotNull(gate.reserve("other", request))
        now = 59_999
        assertNull(gate.reserve("c", request))
        now = 60_000
        assertNotNull(gate.reserve("c", request))
    }

    @Test fun throwingUiReleasesReservation() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("c", request))
        try {
            gate.present(reservation, listOf("event")) { throw IllegalStateException("UI cancelled") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertNotNull(gate.reserve("c", request))
    }
}
