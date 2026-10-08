package ai.gravityfield.gravity_sdk.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares concurrent deliveries of an event URL and retries only transient failures. */
internal class VisibleImpressionDelivery(
    private val send: suspend (String) -> Unit,
    private val retryable: (Throwable) -> Boolean = { true },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val delivered = mutableMapOf<String, Long>()

    suspend fun deliver(urls: List<String>): Boolean {
        if (urls.isEmpty()) return false
        var success = true
        for (url in urls.distinct()) {
            val result = deliver(url)
            success = success && result
        }
        return success
    }

    private suspend fun deliver(url: String): Boolean {
        var owner = false
        val result = mutex.withLock {
            val time = System.nanoTime() / 1_000_000
            delivered.entries.removeAll { time - it.value >= 900_000 }
            if (delivered.containsKey(url)) return true
            flights[url] ?: CompletableDeferred<Boolean>().also {
                owner = true
                flights[url] = it
            }
        }
        if (!owner) return result.await()

        var success = false
        try {
            for (attempt in 0..2) {
                try {
                    send(url)
                    success = true
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (attempt == 2 || !retryable(error)) break
                    pause(if (attempt == 0) 250 else 1_000)
                }
            }
            return success
        } finally {
            // Completing waiters must happen even when the owner is cancelled.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    flights.remove(url)
                    if (success) {
                        delivered[url] = System.nanoTime() / 1_000_000
                        if (delivered.size > 512) {
                            delivered.minByOrNull { it.value }?.key?.let { delivered.remove(it) }
                        }
                    }
                    result.complete(success)
                }
            }
        }
    }
}
