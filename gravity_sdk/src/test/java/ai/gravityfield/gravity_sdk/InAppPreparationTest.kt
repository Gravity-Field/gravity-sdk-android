package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.utils.PresentationRevision
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InAppPreparationTest {
    private val campaign = Campaign("reviews", emptyList())
    private fun content(): CampaignContent {
        val slots = listOf("A", "B", "C").mapIndexed { index, sku ->
            Slot(mapOf("sku" to sku), false, 5, "slot-$index",
                listOf(ProductEvent(ProductAction.CLICK, listOf("https://example.test/click/$sku"))))
        }
        return CampaignContent("content", null, DeliveryMethod.BOTTOM_SHEET, "products", null,
            null, Products("strategy", false, "recently purchased", slots, 1, 2),
            null, null, listOf(Event(Action.IMPRESSION, listOf("https://example.test/impression"))))
    }

    @Test fun filteringPreservesOrderTrackingAndMetadata() {
        val original = content()
        val filtered = original.filteringProducts { it.item["sku"] != "B" }!!
        assertEquals(listOf("A", "C"), filtered.products!!.slots!!.map { it.item["sku"] })
        assertSame(original.products!!.slots!![0], filtered.products!!.slots!![0])
        assertSame(original.products!!.slots!![2], filtered.products!!.slots!![1])
        assertSame(original.events, filtered.events)
        assertEquals(original.products!!.strategyId, filtered.products!!.strategyId)
        assertEquals(original.products!!.countPages, filtered.products!!.countPages)
        assertEquals(3, original.products!!.slots!!.size)
    }

    @Test fun emptyOrMissingProductsSkipContent() {
        assertNull(content().filteringProducts { false })
        assertNull(content().copy(products = null).filteringProducts { true })
        assertNull(content().copy(products = content().products!!.copy(slots = emptyList()))
            .filteringProducts { true })
    }

    @Test fun handlerReceivesCampaignAndWaitsForEligibility() = runBlocking {
        val original = content()
        val started = CompletableDeferred<Unit>()
        val eligibility = CompletableDeferred<Set<String>>()
        val preparation = InAppPreparation { received, receivedCampaign ->
            assertSame(original, received)
            assertSame(campaign, receivedCampaign)
            started.complete(Unit)
            val skus = eligibility.await()
            received.filteringProducts { it.item["sku"] in skus }
        }
        val result = async { preparation.prepare(original, campaign) }
        started.await()
        assertFalse(result.isCompleted)
        eligibility.complete(setOf("B"))
        assertEquals("B", result.await()!!.products!!.slots!!.single().item["sku"])
    }

    @Test fun otherCampaignCanPassThroughUnchanged() = runBlocking {
        val original = content()
        val preparation = InAppPreparation { received, _ -> received }
        assertSame(original, preparation.prepare(original, campaign))
    }

    @Test fun explicitSkipReturnsNull() = runBlocking {
        assertNull(InAppPreparation { _, _ -> null }.prepare(content(), campaign))
    }

    @Test fun errorsSkipContent() = runBlocking {
        assertNull(InAppPreparation { _, _ -> error("API unavailable") }.prepare(content(), campaign))
    }

    @Test fun clientApiTimeoutSkipsWithoutCancellingCampaignSelection() = runBlocking {
        val preparation = InAppPreparation { received, _ ->
            withTimeout(25) { delay(10_000); received }
        }
        assertNull(preparation.prepare(content(), campaign))
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun timeoutSkipsAndCancelsClientWork() = runBlocking {
        var cancelled = false
        val preparation = InAppPreparation(timeoutMillis = 25) { received, _ ->
            try { delay(10_000); received }
            finally { cancelled = true }
        }
        assertNull(preparation.prepare(content(), campaign))
        assertTrue(cancelled)
    }

    @Test fun parentCancellationPropagates() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch {
            InAppPreparation { received, _ ->
                started.complete(Unit)
                try { delay(10_000); received }
                finally { cancelled = true }
            }.prepare(content(), campaign)
            fail("Cancelled preparation must not resume presentation")
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cancelled)
    }

    @Test fun invalidTimeoutDoesNotInvokeClient() = runBlocking {
        assertNull(InAppPreparation(timeoutMillis = 0) { _, _ ->
            fail("Invalid timeout must skip without invoking the handler"); null
        }.prepare(content(), campaign))
    }

    @Test fun allInvalidTimeoutsSkipWithoutInvokingTheHandler() = runBlocking {
        for (timeoutMillis in listOf(0L, -1L, Long.MIN_VALUE, 3_600_001L, Long.MAX_VALUE)) {
            var handlerCalled = false
            val result = InAppPreparation(timeoutMillis = timeoutMillis) { _, _ ->
                handlerCalled = true
                null
            }.prepare(content(), campaign)
            assertNull("timeoutMillis=$timeoutMillis must skip", result)
            assertFalse("timeoutMillis=$timeoutMillis must not invoke the handler", handlerCalled)
        }
    }

    @Test fun boundaryTimeoutOfExactlyOneHourIsAllowed() = runBlocking {
        val preparation = InAppPreparation(timeoutMillis = 3_600_000L) { received, _ -> received }
        assertNotNull(preparation.prepare(content(), campaign))
    }

    @Test fun userChangeDuringPreparationStillBlocksPresentationAfterItCompletes() = runBlocking {
        val revision = PresentationRevision()
        val expected = revision.current
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val preparation = InAppPreparation { received, _ ->
            started.complete(Unit)
            resume.await()
            received
        }
        val result = async { preparation.prepare(content(), campaign) }
        started.await()
        revision.invalidate() // simulates a user/account switch mid-flight
        resume.complete(Unit)
        assertNotNull(result.await())
        assertFalse(revision.canPresent(expected, active = true, locked = false))
    }

    @Test fun leavingTheScreenDuringPreparationStillBlocksPresentationAfterItCompletes() = runBlocking {
        val revision = PresentationRevision()
        val expected = revision.current
        var screenActive = true
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val preparation = InAppPreparation { received, _ ->
            started.complete(Unit)
            resume.await()
            received
        }
        val result = async { preparation.prepare(content(), campaign) }
        started.await()
        screenActive = false // simulates navigating away mid-flight
        resume.complete(Unit)
        assertNotNull(result.await())
        assertFalse(revision.canPresent(expected, active = screenActive, locked = false))
    }

    @Test fun presentationGateRejectsInactiveLockedOrChangedUser() {
        val revision = PresentationRevision()
        val before = revision.current
        assertTrue(revision.canPresent(before, active = true, locked = false))
        assertFalse(revision.canPresent(before, active = false, locked = false))
        assertFalse(revision.canPresent(before, active = true, locked = true))
        revision.invalidate()
        revision.invalidate()
        assertFalse(revision.canPresent(before, active = true, locked = false))
    }
}
