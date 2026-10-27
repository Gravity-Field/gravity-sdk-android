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
    private val complete: (QueuedVisibleImpression, () -> Boolean) -> Unit = { _, remove -> remove() },
    private val discard: (QueuedVisibleImpression, () -> Boolean) -> Unit = { _, remove -> remove() },
) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val delivered = mutableMapOf<String, Long>()

    private fun finish(entry: QueuedVisibleImpression, acknowledged: Boolean) {
        val queue = requireNotNull(store)
        var removed = false
        queue.update(publish = { commit ->
            val remove = { commit(); removed }
            if (acknowledged) complete(entry, remove) else discard(entry, remove)
        }) { entries ->
            removed = entries.any { it.id == entry.id }
            entries.filterNot { it.id == entry.id }
        }
    }

    fun enqueue(event: QueuedVisibleImpression) {
        require(event.urls.isNotEmpty())
        val queue = requireNotNull(store)
        queue.load()
        if (queue.all().any { it.id == event.id }) return
        var evicted = emptyList<QueuedVisibleImpression>()
        queue.update(publish = { commit ->
            commit()
            for (old in evicted) discard(old) { true }
        }) { entries ->
            val retained = entries.filterNot { queue.expired(it) || it.terminalUrls.isNotEmpty() }
                .takeLast(VisibleImpressionStore.MAX_ENTRIES - 1)
            val retainedIds = retained.map { it.id }.toSet()
            evicted = entries.filterNot { it.id in retainedIds }
            retained + event.copy(urls = event.urls.distinct())
        }
    }

    fun persistStaged() {
        val queue = store ?: return
        queue.load()
        for (entry in queue.staged()) {
            enqueue(entry)
            queue.persisted(entry)
        }
    }

    /** Drains share the store's lock across SDK replacement; entries added mid-flight join this pass. */
    suspend fun resume() {
        val queue = store ?: return
        queue.drain.withLock {
            persistStaged()
            val expired = queue.all().filter { queue.expired(it) || it.terminalUrls.isNotEmpty() }
            if (expired.isNotEmpty()) {
                val ids = expired.map { it.id }.toSet()
                queue.update(publish = { commit ->
                    commit()
                    for (entry in expired) discard(entry) { true }
                }) { entries -> entries.filterNot { it.id in ids } }
            }
            val processed = mutableSetOf<String>()
            while (true) {
                val entry = queue.all().firstOrNull { it.id !in processed } ?: break
                processed += entry.id
                if (queue.expired(entry) || entry.terminalUrls.isNotEmpty()) {
                    finish(entry, false)
                    continue
                }
                for (url in entry.urls) {
                    var permanent = false
                    if (!deliver(url, entry.id + url) { permanent = true }) {
                        if (permanent) { finish(entry, false); break }
                        continue
                    }
                    val remaining = queue.all().find { it.id == entry.id } ?: break
                    if (remaining.urls.size == 1) {
                        finish(entry, true)
                    } else {
                        queue.update { list -> list.map {
                            if (it.id == entry.id) it.copy(urls = it.urls - url) else it
                        } }
                    }
                }
            }
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

    private suspend fun deliver(url: String, key: String = url, permanentFailure: () -> Unit = {}): Boolean {
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
                        permanentFailure()
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

/** Completion policy shared by GravitySDK callbacks, including a late response after initialize. */
internal data class VisibleImpressionOwner(
    val queueKey: String,
    val gate: InAppPresentationGate,
    val identity: () -> String?,
)

internal fun finishVisibleImpression(
    entry: QueuedVisibleImpression,
    acknowledged: Boolean,
    source: VisibleImpressionOwner,
    current: VisibleImpressionOwner?,
    remove: () -> Boolean,
) {
    val owner = current?.takeIf { it.queueKey == source.queueKey } ?: source
    owner.gate.scoped {
        if (entry.identity == owner.identity()) {
            owner.gate.finishDelivery(entry.campaignId, entry.contentId, acknowledged, entry.id, remove)
        } else remove()
    }
}
