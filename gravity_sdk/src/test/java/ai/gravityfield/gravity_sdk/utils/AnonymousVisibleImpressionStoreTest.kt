package ai.gravityfield.gravity_sdk.utils

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnonymousVisibleImpressionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun legacyNullIdentityRemainsReadableAndProtectsAnonymousUsers() {
        val file = File(temporary.root, "queue.json")
        file.writeText("""[{"id":"legacy","campaignId":"c","contentId":"content","identity":null,"urls":["https://local.test/wrimp"],"createdAtMillis":1000}]""")
        val store = VisibleImpressionStore(file, now = { 2000L })
        assertEquals(1, store.all().size)
        assertTrue(store.blocks("c", null))
        assertFalse(store.blocks("c", "custom:identified"))
        assertFalse(File(file.path + ".corrupt").exists())
    }

    @Test fun anonymousProtectionExpiresAt24HoursWhileDeliveryRemainsAvailable() {
        var now = 1000L
        val store = VisibleImpressionStore(File(temporary.root, "queue.json"), now = { now })
        val entry = QueuedVisibleImpression(id = "anonymous", campaignId = "c", contentId = "content", identity = "uid:",
            urls = listOf("https://local.test/wrimp"), createdAtMillis = now)
        store.update { listOf(entry) }
        assertTrue(store.blocks("c", null))
        assertTrue(store.blocks("c", "uid:"))
        assertFalse(store.blocks("other", null))
        assertFalse(store.blocks("c", "uid:identified"))
        now += VisibleImpressionStore.PROTECTION_MILLIS
        assertFalse(store.blocks("c", null))
        assertEquals(listOf("https://local.test/wrimp"), store.all().single().urls)
        assertFalse(store.expired(entry))
        now = entry.createdAtMillis + VisibleImpressionStore.DELIVERY_TTL_MILLIS
        assertTrue(store.expired(entry))
    }

    @Test fun legacyAnonymousAcknowledgementReleasesItsMatchingOpening() {
        val gate = InAppPresentationGate()
        val reservation = gate.reserve("c", gate.beginRequest("page", false))!!
        gate.present(reservation, listOf("content")) { true }
        gate.visibleDurable("content", "legacy")
        val entry = QueuedVisibleImpression(id = "legacy", campaignId = "c", contentId = "content", identity = null,
            urls = listOf("https://local.test/wrimp"))
        var removed = false
        finishVisibleImpression(entry, true, VisibleImpressionOwner("key", gate) { "uid:" }, null) { removed = true; true }
        assertTrue(removed)
        assertNotNull(gate.reserve("c", gate.beginRequest("page", false)))
    }

}
