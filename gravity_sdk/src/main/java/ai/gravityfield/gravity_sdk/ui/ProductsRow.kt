package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.utils.ProductEventService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ProductsRow(
    element: Element,
    slots: List<Slot>,
    content: CampaignContent,
    campaign: Campaign,
) {
    val style = element.style
    val height = style.size?.height
    val margin = style.margin
    val paddingValues = if (style.padding != null)
        PaddingValues(
            start = style.padding.left.dp,
            top = style.padding.top.dp,
            end = style.padding.right.dp,
            bottom = style.padding.bottom.dp
        )
    else PaddingValues()
    val rowSpacing = style.rowSpacing?.dp ?: 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .conditional(margin != null) {
                padding(
                    start = margin!!.left.dp,
                    top = margin.top.dp,
                    end = margin.right.dp,
                    bottom = margin.bottom.dp
                )
            }
            .conditional(height != null && GravitySDK.instance.productViewBuilder == null) {
                height(height!!.dp)
            }
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .conditional(style.backgroundColor != null)
                {
                    background(color = style.backgroundColor!!)
                },
            contentPadding = paddingValues,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            items(slots.size) { index ->
                val slot = slots[index]
                VisibilityDetector(
                    onVisible = {
                        ProductEventService.instance.sendProductVisibleImpression(
                            slot,
                            content,
                            campaign
                        )
                    }
                ) {
                    if (GravitySDK.instance.productViewBuilder != null) {
                        GravitySDK.instance.productViewBuilder!!.Build(slot, content, campaign)
                    } else {
                        GravityProduct(slot)
                    }
                }
            }
        }
    }
}
