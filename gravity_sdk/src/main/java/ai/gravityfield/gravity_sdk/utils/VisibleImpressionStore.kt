package ai.gravityfield.gravity_sdk.utils

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
internal data class QueuedVisibleImpression(
    val id: String = UUID.randomUUID().toString(),
    val campaignId: String,
    val contentId: String,
    val identity: String,
    val urls: List<String>,
    val terminalUrls: List<String> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),
)

/** SDK-private file. Disk operations are confined to the delivery IO dispatcher by the SDK. */
internal class VisibleImpressionStore private constructor(
    fileProvider: () -> File,
    private val now: () -> Long,
    private val onCorrupt: (Exception) -> Unit,
) {
    constructor(file: File, now: () -> Long = System::currentTimeMillis, onCorrupt: (Exception) -> Unit = {}) :
        this({ file }, now, onCorrupt) { load() }

    companion object {
        const val PROTECTION_MILLIS = 24 * 60 * 60 * 1000L
        const val DELIVERY_TTL_MILLIS = 7 * PROTECTION_MILLIS
        const val MAX_ENTRIES = 512
        private val stores = mutableMapOf<String, VisibleImpressionStore>()

        // Do not even resolve noBackupFilesDir on main: Android may create that directory.
        @Synchronized fun open(key: String, file: () -> File, onCorrupt: (Exception) -> Unit = {}): VisibleImpressionStore =
            stores.getOrPut(key) { VisibleImpressionStore(file, System::currentTimeMillis, onCorrupt) }
    }

    private val file by lazy(fileProvider)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val drain = Mutex()
    private val diskLock = Any()
    @Volatile private var loaded = false
    @Volatile private var entries: List<QueuedVisibleImpression> = emptyList()
    @Volatile private var staged: List<QueuedVisibleImpression> = emptyList()
    private val persistedCallbacks = mutableMapOf<String, () -> Unit>()

    fun load() = synchronized(diskLock) {
        if (loaded) return@synchronized
        try {
            file.parentFile?.listFiles()?.filter {
                it != file && it.isFile && now() - it.lastModified() >= DELIVERY_TTL_MILLIS
            }?.forEach { it.delete() }
        } catch (_: Exception) { }
        var containsLegacyUser = false
        val restored: List<QueuedVisibleImpression> = try {
            if (file.exists()) {
                val document = json.parseToJsonElement(file.readText())
                val decoded = json.decodeFromJsonElement<List<QueuedVisibleImpression>>(document)
                containsLegacyUser = document.jsonArray.any { "user" in it.jsonObject }
                decoded
            } else emptyList()
        } catch (error: Exception) {
            try { onCorrupt(error) } catch (_: Exception) { }
            try {
                val quarantine = File(file.path + ".corrupt")
                quarantine.delete()
                if (!file.renameTo(quarantine)) file.delete()
            } catch (_: Exception) { }
            emptyList()
        }
        // Older queues included an unused User snapshot. Scrub it on the same IO path before
        // replay, even while offline, rather than retaining host attributes until delivery.
        // A write failure leaves loading incomplete so the runtime retries this migration.
        if (containsLegacyUser) write(restored)
        entries = restored
        loaded = true
    }

    /** Synchronous memory protection, including across SDK replacement, before UI dismissal. */
    @Synchronized fun stage(entry: QueuedVisibleImpression, onPersisted: () -> Unit = {}) {
        if (staged.any { it.id == entry.id } || entries.any { it.id == entry.id }) return
        staged = staged + entry
        persistedCallbacks[entry.id] = onPersisted
    }
    fun staged(): List<QueuedVisibleImpression> = staged
    fun hasWork(): Boolean = !loaded || staged.isNotEmpty() || entries.isNotEmpty()
    fun persisted(entry: QueuedVisibleImpression) {
        val callback = synchronized(this) {
            staged = staged.filterNot { it.id == entry.id }
            persistedCallbacks.remove(entry.id)
        }
        callback?.invoke()
    }
    fun expired(entry: QueuedVisibleImpression): Boolean = now() - entry.createdAtMillis >= DELIVERY_TTL_MILLIS
    fun all(): List<QueuedVisibleImpression> = entries

    /** Disk commit precedes publication; callers can atomically publish with gate acknowledgement. */
    fun update(
        publish: ((() -> Unit) -> Unit) = { it() },
        change: (List<QueuedVisibleImpression>) -> List<QueuedVisibleImpression>,
    ) = synchronized(diskLock) {
        load()
        val next = change(entries)
        write(next)
        publish { entries = next }
    }

    private fun write(next: List<QueuedVisibleImpression>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.encodeToString(next).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot commit visible impression queue" }
    }

    // Readers never wait for a disk lock. Startup conservatively defers automatic presentation
    // until the persisted protection snapshot is available.
    fun blocks(campaignId: String, identity: String?): Boolean = !loaded ||
        identity != null &&
        // Read staging first: publication writes entries before removing the staged copy.
        (staged + entries).any { it.campaignId == campaignId && it.identity == identity &&
            now() - it.createdAtMillis < PROTECTION_MILLIS && it.terminalUrls.isEmpty() }
}
