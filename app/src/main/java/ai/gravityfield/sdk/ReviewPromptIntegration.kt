package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/** Implement with your authenticated API, using cancellable suspending requests. */
interface ReviewEligibilityAPI {
    suspend fun eligibleSkus(skus: List<String>): Set<String>
}

/** Keep one instance per signed-in user; create a new one when the account changes. */
class ReviewPromptIntegration(
    private val api: ReviewEligibilityAPI,
    private val selector: String,
    private val fallbackProductViewBuilder: ProductViewBuilder,
    private val onOpenReview: (String) -> Unit,
) {
    private val submitted = ConcurrentHashMap.newKeySet<String>()

    fun preparation() = InAppPreparation(timeoutMillis = 5_000) { content, campaign ->
        if (campaign.selector != selector) return@InAppPreparation content
        val skus = content.products?.slots.orEmpty().mapNotNull { it.item["sku"] as? String }.distinct()
        if (skus.isEmpty()) return@InAppPreparation null
        val eligible = api.eligibleSkus(skus)
        content.filteringProducts { slot ->
            val sku = slot.item["sku"] as? String
            sku != null && sku in eligible && sku !in submitted
        }
    }

    val productViewBuilder = object : ProductViewBuilder {
        @Composable
        override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
            if (campaign.selector != selector) {
                fallbackProductViewBuilder.Build(slot, content, campaign)
                return
            }
            val sku = slot.item["sku"] as? String ?: return
            Column(modifier = Modifier.width(160.dp)) {
                Text(slot.item["name"] as? String ?: sku)
                Button(onClick = {
                    GravitySDK.instance.sendProductEngagement(ProductClickEngagement(slot, content, campaign))
                    GravitySDK.instance.dismissInApp(content.contentId) { dismissed ->
                        if (dismissed) onOpenReview(sku)
                    }
                }) { Text("Оставить отзыв") }
            }
        }
    }

    /** Call after a successful submission, not when the review screen opens. */
    fun didSubmitReview(sku: String) {
        submitted.add(sku)
    }
}
