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

    @Test fun permanentFailureReleasesProtectionWithoutRetry() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        var count = 0
        val delivery = VisibleImpressionDelivery(send = { count++; throw IllegalArgumentException() },
            retryable = { false }, store = store)
        delivery.enqueue(event())
        delivery.resume()
        delivery.resume()
        assertEquals(1, count)
        assertFalse(store.blocks("campaign", "old"))
        assertTrue(store.all().isEmpty())
    }

    @Test fun sectionsHaveIndependentStores() {
        val first = VisibleImpressionStore(File(temporary.root, "section-a"))
        val second = VisibleImpressionStore(File(temporary.root, "section-b"))
        first.update { listOf(event()) }
        assertFalse(second.blocks("campaign", "old"))
    }

    @Test fun permanentFailureDropsTheOpeningAndReleasesTheGateWithoutAcknowledgement() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        val gate = InAppPresentationGate(deliveryPending = { store.blocks(it, "old") })
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("campaign", request))
        gate.present(reservation, listOf("content")) { true }
        val delivery = VisibleImpressionDelivery(send = { throw IllegalArgumentException() },
            retryable = { false }, store = store,
            complete = { _, _ -> fail("Permanent error is not an acknowledgement") },
            discard = { entry, remove -> gate.scoped { remove(); gate.deliveryAbandoned(entry.contentId) } })
        delivery.enqueue(event())
        gate.visibleDurable("content")
        delivery.resume()
        assertFalse(store.blocks("campaign", "old"))
        assertNotNull(gate.reserve("campaign", request)) // No acknowledged watermark.
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
        val waiters = List(20) { launch { delivery.resume() } }
        assertEquals(1, count)
        waiters.forEach { it.cancelAndJoin() }
        owner.cancelAndJoin()
        assertTrue(VisibleImpressionStore(file).blocks("campaign", "old"))
        val restarted = VisibleImpressionDelivery(send = { count++ }, store = VisibleImpressionStore(file))
        restarted.resume()
        assertEquals(2, count)
        assertTrue(VisibleImpressionStore(file).all().isEmpty())
    }

    @Test fun protectionExpiresAtOneDayButDeliverySurvivesRestartUntilSevenDays() = runBlocking {
        var now = 0L
        val store = VisibleImpressionStore(File(temporary.root, "queue"), now = { now })
        val gate = InAppPresentationGate(now = { now }, deliveryPending = { store.blocks(it, "old") })
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("campaign", request))
        gate.present(reservation, listOf("content")) { true }
        val delivery = VisibleImpressionDelivery(send = { throw IOException() }, pause = {}, store = store,
            discard = { entry, remove -> gate.scoped { remove(); gate.deliveryAbandoned(entry.contentId) } })
        delivery.enqueue(event().copy(createdAtMillis = 0))
        gate.visibleDurable("content")
        delivery.resume()
        now = 24 * 60 * 60 * 1000L - 1
        assertNull(gate.reserve("campaign", request))
        now++
        delivery.resume()
        assertEquals(1, store.all().size)
        assertFalse(store.blocks("campaign", "old"))
        assertNotNull(gate.reserve("campaign", request))
        val reloaded = VisibleImpressionStore(File(temporary.root, "queue"), now = { now })
        assertFalse(reloaded.blocks("campaign", "old"))
        val sent = mutableListOf<String>()
        VisibleImpressionDelivery(send = { sent += it }, store = reloaded).resume()
        assertEquals(listOf("https://example.com/wrimp?uid=old"), sent)
        now = 7 * 24 * 60 * 60 * 1000L
        delivery.resume()
        assertTrue(store.all().isEmpty())
    }

    @Test fun eventAddedDuringDrainIsSentWithoutWaitingForAnotherTimer() = runBlocking {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        val started = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val first = VisibleImpressionDelivery(send = {
            sent += it
            if (it == "first") { started.complete(Unit); proceed.await() }
        }, store = store)
        first.enqueue(event().copy(urls = listOf("first")))
        val running = launch { first.resume() }
        started.await()
        val replacement = VisibleImpressionDelivery(send = { sent += it }, store = store)
        replacement.enqueue(event().copy(urls = listOf("second")))
        val another = launch { replacement.resume() }
        proceed.complete(Unit)
        running.join()
        another.join()
        assertEquals(listOf("first", "second"), sent)
    }

    @Test fun queueCapacityEvictsOldestOpeningAndReleasesItsProtection() {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        val original = List(VisibleImpressionStore.MAX_ENTRIES) { event().copy(campaignId = "campaign-$it") }
        store.update { original }
        val discarded = mutableListOf<String>()
        val delivery = VisibleImpressionDelivery(send = {}, store = store, discard = { entry, remove ->
            discarded += entry.campaignId
            remove()
        })
        delivery.enqueue(event().copy(campaignId = "new"))
        assertEquals(listOf("campaign-0"), discarded)
        assertEquals(512, store.all().size)
        assertFalse(store.blocks("campaign-0", "old"))
        assertTrue(store.blocks("new", "old"))
    }

    @Test fun lazyStoreStagesProtectionWithoutDiskAndPersistsBeforeCallbackAndSend() = runBlocking {
        val file = File(temporary.root, "lazy")
        val uiThread = Thread.currentThread()
        var resolutions = 0
        val store = VisibleImpressionStore.open(file.path, file = {
            assertNotSame(uiThread, Thread.currentThread())
            resolutions++
            file
        })
        assertEquals(0, resolutions)
        assertTrue(store.blocks("restored-campaign", "old")) // Until disk restore finishes.
        var callback = false
        val original = event()
        store.stage(original) {
            assertEquals(listOf(original), VisibleImpressionStore(file).all())
            callback = true
        }
        assertTrue(store.blocks("campaign", "old"))
        assertEquals(0, resolutions)
        assertTrue(store.all().isEmpty()) // Only durable entries may be sent.
        val replacement = VisibleImpressionStore.open(file.path, file = { error("Already open") })
        assertSame(store, replacement)
        var sent = false
        val delivery = VisibleImpressionDelivery(send = {
            assertTrue(callback)
            assertEquals(listOf(original), VisibleImpressionStore(file).all())
            sent = true
        }, store = replacement)
        withContext(Dispatchers.IO) { delivery.resume() }
        assertTrue(sent)
        assertEquals(1, resolutions)
        assertFalse(store.hasWork())
    }

    @Test fun failedAsyncPersistenceKeepsMemoryProtectionAndRetriesWithoutSending() = runBlocking {
        val parent = File(temporary.root, "blocked-parent").apply { writeText("not a directory") }
        val file = File(parent, "queue")
        val store = VisibleImpressionStore.open(file.path, file = { file })
        var callbacks = 0
        var sends = 0
        store.stage(event()) { callbacks++ }
        val delivery = VisibleImpressionDelivery(send = { sends++ }, store = store)
        try { delivery.resume(); fail("Write must fail") } catch (_: IOException) { }
        assertEquals(0, callbacks)
        assertEquals(0, sends)
        assertTrue(store.blocks("campaign", "old"))
        parent.delete()
        delivery.resume()
        assertEquals(1, callbacks)
        assertEquals(1, sends)
    }

    @Test fun completionUsesCurrentOwnerOnlyForSameSectionAndNeverAcknowledgesAnotherUser() {
        for (sameSection in listOf(true, false)) for (sameUser in listOf(true, false)) {
            val oldGate = InAppPresentationGate()
            val newGate = InAppPresentationGate()
            val oldRequest = oldGate.beginRequest("home", true)
            val newRequest = newGate.beginRequest("home", true)
            var removed = false
            finishVisibleImpression(event(), true,
                VisibleImpressionOwner("section", oldGate) { "old" },
                VisibleImpressionOwner(if (sameSection) "section" else "other", newGate) { if (sameUser) "old" else "new" },
                { removed = true; true })
            assertTrue(removed)
            if (sameSection && sameUser) assertNull(newGate.reserve("campaign", newRequest))
            else assertNotNull(newGate.reserve("campaign", newRequest))
            if (!sameSection) assertNull(oldGate.reserve("campaign", oldRequest))
            else assertNotNull(oldGate.reserve("campaign", oldRequest))
        }
    }

    @Test fun discardReleasesCurrentReservationWithoutAnAcknowledgementWatermark() {
        val gate = InAppPresentationGate()
        val request = gate.beginRequest("home", true)
        val reservation = requireNotNull(gate.reserve("campaign", request))
        gate.present(reservation, listOf("content")) { true }
        val entry = event()
        gate.visibleDurable("content", entry.id)
        val owner = VisibleImpressionOwner("section", gate) { "old" }
        finishVisibleImpression(entry, false, owner, owner) { true }
        assertNotNull(gate.reserve("campaign", request))
    }

    @Test fun oldDeliveryAfterProtectionExpiryDoesNotReleaseANewerOpening() {
        for (acknowledged in listOf(true, false)) {
            val gate = InAppPresentationGate()
            val request = gate.beginRequest("home", true)
            val reservation = requireNotNull(gate.reserve("campaign", request))
            gate.present(reservation, listOf("content")) { true }
            val owner = VisibleImpressionOwner("section", gate) { "old" }
            finishVisibleImpression(event(), acknowledged, owner, owner) { true }
            assertNull(gate.reserve("campaign", gate.beginRequest("home", false)))
        }
    }

    @Test fun sevenDayDeliveryBoundaryDropsWithoutAnotherHttpAttempt() = runBlocking {
        var now = 0L
        val store = VisibleImpressionStore(File(temporary.root, "queue"), now = { now })
        var attempts = 0
        val delivery = VisibleImpressionDelivery(send = { attempts++; throw IOException() }, pause = {}, store = store)
        delivery.enqueue(event().copy(createdAtMillis = 0))
        now = VisibleImpressionStore.DELIVERY_TTL_MILLIS - 1
        delivery.resume()
        assertEquals(3, attempts)
        assertEquals(1, store.all().size)
        now++
        delivery.resume()
        assertEquals(3, attempts)
        assertTrue(store.all().isEmpty())
    }

}
