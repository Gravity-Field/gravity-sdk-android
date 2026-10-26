package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.User
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VisibleImpressionQueueTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun event() = QueuedVisibleImpression(campaignId = "campaign", contentId = "content", identity = "old", user = User(custom = "old", ses = "original"), urls = listOf("https://example.com/wrimp?uid=old"))

    @Test fun offlineCloseAndRestartKeepProtectionThenRestoreDeliversOriginalUrlAndUser() = runBlocking {
        val file = File(temporary.root, "queue")
        val store = VisibleImpressionStore(file)
        val original = event()
        val offline = VisibleImpressionDelivery(send = { throw IOException() }, pause = {}, store = store)
        offline.enqueue(original)
        offline.resume()
        assertTrue(store.blocks("campaign", "old"))
        assertFalse(store.blocks("campaign", "new"))
        val reloaded = VisibleImpressionStore(file)
        assertEquals(listOf(original), reloaded.all())
        val sent = mutableListOf<String>()
        val online = VisibleImpressionDelivery(send = { sent += it }, store = reloaded)
        online.resume()
        assertEquals(listOf("https://example.com/wrimp?uid=old"), sent)
        assertEquals(User(custom = "old", ses = "original"), original.user)
        assertFalse(reloaded.blocks("campaign", "old"))
        assertTrue(VisibleImpressionStore(file).all().isEmpty())
    }

    @Test fun partialSuccessIsDurableAndNewOpeningMayReuseUrl() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        var online = false
        val sent = mutableListOf<String>()
        val delivery = VisibleImpressionDelivery(send = {
            if (it == "bad" && !online) throw IOException()
            sent += it
        }, pause = {}, store = store)
        val first = event().copy(urls = listOf("good", "bad"))
        delivery.enqueue(first)
        delivery.resume()
        assertEquals(listOf("bad"), VisibleImpressionStore(File(temporary.root, "queue")).all().single().urls)
        online = true
        delivery.resume()
        delivery.enqueue(event().copy(urls = listOf("good")))
        delivery.resume()
        assertEquals(listOf("good", "bad", "good"), sent)
    }
    @Test fun restoredPendingQueueRejectsParallelStaleRequestsAndOnlyFreshRequestsCanReopenAfterAck() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        val first = event()
        store.update { listOf(first) }
        var identity = "old"
        val gate = InAppPresentationGate(deliveryPending = { store.blocks(it, identity) })
        val stale = gate.beginRequest("home", true)
        val parallel = gate.beginRequest("home", false)
        assertNull(gate.reserve("campaign", stale))
        assertNull(gate.reserve("campaign", parallel))
        identity = "new"
        val newUserRequest = gate.beginRequest("home", true)
        assertNotNull(gate.reserve("campaign", newUserRequest))
        gate.reset()
        identity = "old"
        val awaiting = gate.beginRequest("next", true)
        assertNull(gate.reserve("campaign", awaiting))
        val delivery = VisibleImpressionDelivery(send = {}, store = store, complete = { entry, remove ->
            gate.scoped { remove(); gate.acknowledgedCampaign(entry.campaignId) }
        })
        delivery.resume()
        assertNull(gate.reserve("campaign", awaiting))
        assertNotNull(gate.reserve("campaign", gate.beginRequest("next", false)))
    }

    @Test fun permanentFailureIsRetainedWithoutRetry() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        var count = 0
        val delivery = VisibleImpressionDelivery(send = { count++; throw IllegalArgumentException() },
            retryable = { false }, store = store)
        delivery.enqueue(event())
        delivery.resume()
        delivery.resume()
        assertEquals(1, count)
        assertTrue(store.blocks("campaign", "old"))
        assertEquals(event().urls, store.all().single().terminalUrls)
    }

    @Test fun sectionsHaveIndependentStores() {
        val first = VisibleImpressionStore(File(temporary.root, "section-a"))
        val second = VisibleImpressionStore(File(temporary.root, "section-b"))
        first.update { listOf(event()) }
        assertFalse(second.blocks("campaign", "old"))
    }

    @Test fun permanentFailureOfOneUrlDoesNotStopRetryingOtherUrls() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        var online = false
        val attempts = mutableListOf<String>()
        val delivery = VisibleImpressionDelivery(send = {
            attempts += it
            if (it == "permanent") throw IllegalArgumentException()
            if (!online) throw IOException()
        }, retryable = { it is IOException }, pause = {}, store = store)
        delivery.enqueue(event().copy(urls = listOf("permanent", "transient")))
        delivery.resume()
        online = true
        delivery.resume()
        assertEquals(listOf("permanent", "transient", "transient", "transient", "transient"), attempts)
        assertEquals(listOf("permanent"), store.all().single().urls)
    }

    @Test fun parallelResumeSharesSendAndCancellationLeavesDurableWorkForNextStart() = runBlocking {
        val file = File(temporary.root, "queue")
        val store = VisibleImpressionStore(file)
        var count = 0
        val started = CompletableDeferred<Unit>()
        val delivery = VisibleImpressionDelivery(send = {
            assertTrue(VisibleImpressionStore(file).blocks("campaign", "old"))
            count++
            started.complete(Unit)
            awaitCancellation()
        }, store = store)
        delivery.enqueue(event())
        val owner = launch { delivery.resume() }
        started.await()
        List(20) { async { delivery.resume() } }.awaitAll()
        assertEquals(1, count)
        owner.cancelAndJoin()
        assertTrue(VisibleImpressionStore(file).blocks("campaign", "old"))
        val restarted = VisibleImpressionDelivery(send = { count++ }, store = VisibleImpressionStore(file))
        restarted.resume()
        assertEquals(2, count)
        assertTrue(VisibleImpressionStore(file).all().isEmpty())
    }

}
