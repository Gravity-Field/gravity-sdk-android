package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.User
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VisibleImpressionStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun corruptedAndIncompatibleFilesAreQuarantinedAndDoNotCrashStartup() {
        for (payload in listOf("garbage", "[{\"campaignId\":42}]")) {
            val file = File(temporary.root, "queue")
            file.writeText(payload)
            var reported = false
            val store = VisibleImpressionStore(file, onCorrupt = { reported = true })
            assertTrue(store.all().isEmpty())
            assertTrue(reported)
            assertFalse(file.exists())
            assertTrue(File(file.path + ".corrupt").exists())
        }
    }

    @Test fun newerSchemaFieldsDoNotDiscardValidEvents() {
        val file = File(temporary.root, "queue")
        val event = QueuedVisibleImpression(campaignId = "c", contentId = "content", identity = "u", user = User(custom = "u"), urls = listOf("url"))
        file.writeText(Json { encodeDefaults = true }.encodeToString(listOf(event)).replaceFirst("{", "{\"futureField\":true,"))
        assertEquals(listOf(event), VisibleImpressionStore(file).all())
    }
}
