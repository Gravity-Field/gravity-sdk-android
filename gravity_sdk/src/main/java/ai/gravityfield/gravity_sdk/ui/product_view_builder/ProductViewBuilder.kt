package ai.gravityfield.gravity_sdk.ui.product_view_builder

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

interface ProductViewBuilder {

    @Composable
    fun Build(slot: Slot, content: CampaignContent, campaign: Campaign)
}

interface LegacyProductViewBuilder : ProductViewBuilder {

    @Composable
    override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
        AndroidView(factory = {
            createView(it, slot, content, campaign)
        })
    }

    fun createView(
        context: Context,
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
    ): View
}
