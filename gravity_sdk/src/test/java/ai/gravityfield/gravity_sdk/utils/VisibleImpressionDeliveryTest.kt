package ai.gravityfield.gravity_sdk.utils

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class VisibleImpressionDeliveryTest {
    @Test fun concurrentCallbacksShareDeliveryAndSuccessIsNotResent() = runBlocking {
        val count = AtomicInteger()
        val delivery = VisibleImpressionDelivery(send = { count.incrementAndGet(); delay(10) })
        val results = List(30) { async(Dispatchers.Default) { delivery.deliver(listOf("event", "event")) } }.awaitAll()
        assertTrue(results.all { it })
        assertTrue(delivery.deliver(listOf("event")))
        assertEquals(1, count.get())
    }

    @Test fun transientErrorsRetryThreeTimesBeforeSuccess() = runBlocking {
        var count = 0
        val pauses = mutableListOf<Long>()
        val delivery = VisibleImpressionDelivery(send = { if (++count < 3) throw java.io.IOException() }, pause = { pauses += it })
        assertTrue(delivery.deliver(listOf("event")))
        assertEquals(3, count)
        assertEquals(listOf(250L, 1000L), pauses)
    }

    @Test fun partialFailureDoesNotResendSuccessfulUrlsOrCacheFailure() = runBlocking {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val delivery = VisibleImpressionDelivery(send = { url ->
            val count = counts.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
            if (url == "bad" && count <= 3) throw java.io.IOException()
        }, pause = {})
        assertFalse(delivery.deliver(listOf("good", "bad")))
        assertTrue(delivery.deliver(listOf("good", "bad")))
        assertEquals(1, counts["good"]!!.get())
        assertEquals(4, counts["bad"]!!.get())
    }

    @Test fun permanentErrorAndEmptyUrlsCannotAcknowledge() = runBlocking {
        var count = 0
        val delivery = VisibleImpressionDelivery(send = { count++; throw IllegalArgumentException() }, retryable = { false }, pause = { fail("No retry expected") })
        assertFalse(delivery.deliver(listOf("bad")))
        assertFalse(delivery.deliver(emptyList()))
        assertEquals(1, count)
    }

    @Test fun ownerCancellationReleasesWaitersAndAllowsLaterDelivery() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var count = 0
        val delivery = VisibleImpressionDelivery(send = {
            count++
            if (count == 1) { started.complete(Unit); awaitCancellation() }
        })
        val owner = launch { delivery.deliver(listOf("event")) }
        started.await()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { delivery.deliver(listOf("event")) }
        owner.cancelAndJoin()
        assertFalse(withTimeout(1000) { waiter.await() })
        assertTrue(delivery.deliver(listOf("event")))
        assertEquals(2, count)
    }
}
