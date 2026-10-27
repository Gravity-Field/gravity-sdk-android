package ai.gravityfield.gravity_sdk.utils

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
        val event = QueuedVisibleImpression(campaignId = "c", contentId = "content", identity = "u", urls = listOf("url"))
        file.writeText(Json { encodeDefaults = true }.encodeToString(listOf(event)).replaceFirst("{", "{\"futureField\":true,"))
        assertEquals(listOf(event), VisibleImpressionStore(file).all())
    }
    @Test fun siblingCleanupRetainsOneDayQueuesAndDeletesOnlySevenDayOldFiles() {
        val now = System.currentTimeMillis()
        val oneDay = File(temporary.root, "one-day.json").apply {
            writeText("[]"); setLastModified(now - VisibleImpressionStore.PROTECTION_MILLIS)
        }
        val sevenDays = File(temporary.root, "seven-days.json").apply {
            writeText("[]"); setLastModified(now - VisibleImpressionStore.DELIVERY_TTL_MILLIS)
        }
        VisibleImpressionStore(File(temporary.root, "active.json"), now = { now })
        assertTrue(oneDay.exists())
        assertFalse(sevenDays.exists())
    }

    @Test fun protectionReadersDoNotWaitForDiskMutation() {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        val writing = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val thread = Thread {
            store.update {
                writing.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                it
            }
        }
        thread.start()
        assertTrue(writing.await(5, java.util.concurrent.TimeUnit.SECONDS))
        try {
            val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                assertFalse(reader.submit<Boolean> { store.blocks("c", "u") }.get(1, java.util.concurrent.TimeUnit.SECONDS))
            } finally { reader.shutdownNow() }
        } finally { release.countDown(); thread.join() }
    }

    @Test fun unknownIdentityDoesNotShareProtectionWithOtherUnknownUsers() {
        val store = VisibleImpressionStore(File(temporary.root, "queue"))
        store.update { listOf(QueuedVisibleImpression(campaignId = "c", contentId = "content",
            identity = "unidentified", urls = listOf("url"))) }
        assertFalse(store.blocks("c", null))
        assertEquals(1, store.all().size) // Delivery still retains the original URL.
    }

    private fun legacyPayload() = """[{
        "id":"legacy-event","campaignId":"c","contentId":"content","identity":"custom:old",
        "user":{"uid":"unused-uid","custom":"old","ses":"unused-session",
            "attributes":{"email":"private@example.invalid","name":"Private Name"}},
        "urls":["https://example.com/wrimp?uid=old"],"createdAtMillis":${System.currentTimeMillis()}
    }]"""

    @Test fun durableWritesContainOnlyDeliveryMetadataWithoutUserAttributes() {
        val event = Json { ignoreUnknownKeys = true }.decodeFromString<List<QueuedVisibleImpression>>(legacyPayload()).single()
        val file = File(temporary.root, "queue")
        VisibleImpressionStore(file).update { listOf(event) }
        val persisted = file.readText()
        for (unused in listOf("user", "attributes", "unused-uid", "unused-session", "private@example.invalid", "Private Name")) {
            assertFalse("Unexpected persisted field/value: $unused", persisted.contains("\"$unused\""))
        }
        assertTrue(persisted.contains("custom:old"))
        assertTrue(persisted.contains("https://example.com/wrimp?uid=old"))
    }

    @Test fun legacyUserSnapshotIsIgnoredAndScrubbedDuringLoad() {
        val file = File(temporary.root, "queue").apply { writeText(legacyPayload()) }
        val store = VisibleImpressionStore(file)
        assertEquals("legacy-event", store.all().single().id)
        assertEquals(listOf("https://example.com/wrimp?uid=old"), store.all().single().urls)
        assertTrue(store.blocks("c", "custom:old"))
        assertFalse(file.readText().contains("attributes"))
        assertFalse(file.readText().contains("unused-session"))
        assertEquals(store.all(), VisibleImpressionStore(file).all())
    }

}
