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
    private val store: VisibleImpressionStore? = null,
    private val complete: (QueuedVisibleImpression, () -> Unit) -> Unit = { _, remove -> remove() },
) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val delivered = mutableMapOf<String, Long>()

    private val drain = Mutex()

    fun enqueue(event: QueuedVisibleImpression) {
        require(event.urls.isNotEmpty())
        requireNotNull(store).update { entries ->
            if (entries.any { it.id == event.id }) entries else entries + event.copy(urls = event.urls.distinct())
        }
    }

    /** Owned by the SDK, never by a window/composition. Also called after connectivity returns. */
    suspend fun resume() {
        val queue = store ?: return
        if (!drain.tryLock()) return
        try {
            for (entry in queue.all()) {
                for (url in entry.urls.filterNot { it in entry.terminalUrls }) {
                    if (!deliver(url, entry.id + url)) continue
                    val remaining = queue.all().find { it.id == entry.id } ?: break
                    if (remaining.urls.size == 1) {
                        complete(entry) { queue.update { list -> list.filterNot { it.id == entry.id } } }
                    } else {
                        queue.update { list -> list.map {
                            if (it.id == entry.id) it.copy(urls = it.urls - url) else it
                        } }
                    }
                }
            }
        } finally {
            drain.unlock()
        }
    }

    suspend fun deliver(urls: List<String>): Boolean {
        if (urls.isEmpty()) return false
        var success = true
        for (url in urls.distinct()) {
            val result = deliver(url)
            success = success && result
        }
        return success
    }

    private suspend fun deliver(url: String, key: String = url): Boolean {
        var owner = false
        val result = mutex.withLock {
            val time = System.nanoTime() / 1_000_000
            delivered.entries.removeAll { time - it.value >= 900_000 }
            if (delivered.containsKey(key)) return true
            flights[key] ?: CompletableDeferred<Boolean>().also {
                owner = true
                flights[key] = it
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
                    if (!retryable(error)) {
                        store?.update { entries -> entries.map {
                            if (it.id + url == key) it.copy(terminalUrls = (it.terminalUrls + url).distinct()) else it
                        } }
                        break
                    }
                    if (attempt == 2) break
                    pause(if (attempt == 0) 250 else 1_000)
                }
            }
            return success
        } finally {
            // Completing waiters must happen even when the owner is cancelled.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    flights.remove(key)
                    if (success) {
                        delivered[key] = System.nanoTime() / 1_000_000
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
