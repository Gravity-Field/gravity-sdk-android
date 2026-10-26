package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.User
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
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
)

/** SDK-private file, partitioned by API key and section. Commit precedes visibility delivery. */
internal class VisibleImpressionStore(private val file: File) {
    companion object {
        private val stores = mutableMapOf<String, VisibleImpressionStore>()
        // SDK reinitialization may overlap a cancelled HTTP response. Share the file's lock/state.
        @Synchronized fun open(file: File): VisibleImpressionStore =
            stores.getOrPut(file.absolutePath) { VisibleImpressionStore(file) }
    }

    private var entries: List<QueuedVisibleImpression> = if (file.exists()) {
        Json.decodeFromString(file.readText())
    } else emptyList()

    @Synchronized fun all(): List<QueuedVisibleImpression> = entries.toList()

    @Synchronized fun update(change: (List<QueuedVisibleImpression>) -> List<QueuedVisibleImpression>) {
        val next = change(entries)
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(Json.encodeToString(next).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot commit visible impression queue" }
        entries = next
    }

    fun blocks(campaignId: String, identity: String): Boolean =
        all().any { it.campaignId == campaignId && it.identity == identity }
}
