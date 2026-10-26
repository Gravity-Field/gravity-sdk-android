package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.User
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class QueuedVisibleImpression(
    val id: String = UUID.randomUUID().toString(),
    val campaignId: String,
    val contentId: String,
    val identity: String,
    val user: User,
    val urls: List<String>,
    val terminalUrls: List<String> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),
)

/** SDK-private file, partitioned by API key and section. Commit precedes visibility delivery. */
internal class VisibleImpressionStore(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val onCorrupt: (Exception) -> Unit = {},
) {
    companion object {
        const val RETENTION_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 512
        private val stores = mutableMapOf<String, VisibleImpressionStore>()
        // SDK reinitialization may overlap a cancelled HTTP response. Share the file's lock/state.
        @Synchronized fun open(file: File, onCorrupt: (Exception) -> Unit): VisibleImpressionStore {
            // Old API-key files cannot be replayed with new credentials; remove only expired files.
            try {
                file.parentFile?.listFiles()?.filter {
                    it != file && it.isFile && System.currentTimeMillis() - it.lastModified() >= RETENTION_MILLIS
                }?.forEach { it.delete() }
            } catch (_: Exception) { }
            return stores.getOrPut(file.absolutePath) { VisibleImpressionStore(file, onCorrupt = onCorrupt) }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Shared across replacement SDK instances to avoid overlapping drains on reinitialization.
    val drain = Mutex()
    private var entries: List<QueuedVisibleImpression> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyList()
    } catch (error: Exception) {
        try { onCorrupt(error) } catch (_: Exception) { }
        try {
            val quarantine = File(file.path + ".corrupt")
            quarantine.delete()
            if (!file.renameTo(quarantine)) file.delete()
        } catch (_: Exception) { }
        emptyList()
    }

    fun expired(entry: QueuedVisibleImpression): Boolean =
        now() - entry.createdAtMillis >= RETENTION_MILLIS

    @Synchronized fun all(): List<QueuedVisibleImpression> = entries.toList()

    @Synchronized fun update(change: (List<QueuedVisibleImpression>) -> List<QueuedVisibleImpression>) {
        val next = change(entries)
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.encodeToString(next).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot commit visible impression queue" }
        entries = next
    }

    fun blocks(campaignId: String, identity: String): Boolean =
        all().any { it.campaignId == campaignId && it.identity == identity && !expired(it) && it.terminalUrls.isEmpty() }
}
