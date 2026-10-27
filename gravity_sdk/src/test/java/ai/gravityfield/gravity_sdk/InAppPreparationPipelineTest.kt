package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.InAppPreparation
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.filteringProducts
import ai.gravityfield.gravity_sdk.network.Campaign
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class InAppPreparationPipelineTest {
    private fun content(skus: List<String>): CampaignContent {
        val slots = skus.mapIndexed { index, sku ->
            Slot(mapOf("sku" to sku), false, 1, "slot-$index", null)
        }
        return CampaignContent(
            "content", null, DeliveryMethod.BOTTOM_SHEET, "products", null,
            null, Products("strategy", false, "recently purchased", slots, 1, 1),
            null, null, null,
        )
    }

    @Test fun noPreparationPassesContentThroughUnchanged() = runBlocking {
        val original = content(listOf("A", "B", "C"))
        val result = GravitySDK.resolvedContent(original, Campaign("any", emptyList()), preparation = null)
        assertSame(original, result)
    }

    @Test fun preparationPresentDelegatesToItsPrepare() = runBlocking {
        val original = content(listOf("A", "B", "C"))
        val preparation = InAppPreparation { received, _ ->
            received.filteringProducts { it.item["sku"] != "B" }
        }
        val result = GravitySDK.resolvedContent(original, Campaign(null, emptyList()), preparation)
        assertEquals(listOf("A", "C"), result!!.products!!.slots!!.map { it.item["sku"] })
    }

    @Test fun skipDecisionPropagates() = runBlocking {
        val original = content(listOf("A"))
        val preparation = InAppPreparation { _, _ -> null }
        val result = GravitySDK.resolvedContent(original, Campaign(null, emptyList()), preparation)
        assertNull(result)
    }
}
