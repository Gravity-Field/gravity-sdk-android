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
            identity = "unidentified", user = User(), urls = listOf("url"))) }
        assertFalse(store.blocks("c", null))
        assertEquals(1, store.all().size) // Delivery still retains the original URL.
    }

}
