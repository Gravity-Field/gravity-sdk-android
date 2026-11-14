package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.ProductContainerType
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.Style
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.utils.ProductEventService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

internal data class ProductsGridParameters(
    val columns: Int,
    val horizontalSpacing: Double,
    val verticalSpacing: Double,
    val aspectRatio: Float,
) {
    companion object {
        fun fromStyle(style: Style): ProductsGridParameters {
            val settings = style.gridSettings
            val columns = settings?.columns?.takeIf { it in 1..12 }
                ?: style.gridColumns?.takeIf { it in 1..12 } ?: 2
            return ProductsGridParameters(
                columns = columns,
                horizontalSpacing = settings?.horizontalSpacing?.takeIf { it.isFinite() && it >= 0 } ?: 8.0,
                verticalSpacing = settings?.verticalSpacing?.takeIf { it.isFinite() && it >= 0 } ?: 8.0,
                aspectRatio = 1f,
            )
        }
    }
}

@Composable
internal fun ProductsGrid(
    element: Element,
    slots: List<Slot>,
    content: CampaignContent,
    campaign: Campaign,
) {
    if (slots.isEmpty()) return
    val style = element.style
    val parameters = ProductsGridParameters.fromStyle(style)
    val padding = style.padding
    val builder = GravitySDK.instance.productViewBuilder
    val presentationAnalytics = LocalPresentationAnalytics.current
    val visibleProducts = remember(content.contentId, campaign, presentationAnalytics) {
        presentationAnalytics?.gridProducts(content.contentId) ?: mutableSetOf()
    }

    // Ordinary rows take their full content height and can live in any scrolling parent.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .conditional(padding != null) {
                padding(start = padding!!.left.dp, top = padding.top.dp,
                    end = padding.right.dp, bottom = padding.bottom.dp)
            },
        verticalArrangement = Arrangement.spacedBy(parameters.verticalSpacing.dp),
    ) {
        for (rowStart in slots.indices step parameters.columns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(parameters.horizontalSpacing.dp),
            ) {
                repeat(parameters.columns) { column ->
                    val index = rowStart + column
                    val cell = Modifier.weight(1f).aspectRatio(parameters.aspectRatio)
                    if (index < slots.size && builder != null) {
                        val slot = slots[index]
                        key(slot.slotId, index) {
                            Box(cell.clipToBounds(), propagateMinConstraints = true) {
                                VisibilityDetector(
                                    once = true,
                                    observeViewportChanges = true,
                                    visibilityKey = listOf(content.contentId, campaign, slot.slotId, index),
                                    onVisible = {
                                        val slotId = slot.slotId
                                        if (slotId != null && visibleProducts.add(slotId)) {
                                            ProductEventService.instance.sendProductVisibleImpression(slot, content, campaign)
                                        }
                                    },
                                ) {
                                    builder.Build(slot, content, campaign)
                                }
                            }
                        }
                    } else {
                        Spacer(cell)
                    }
                }
            }
        }
    }
}


internal fun CampaignContent.hasProductsGrid(): Boolean = variables?.elements?.any {
    it.type == ElementType.PRODUCTS_CONTAINER && it.style.productContainerType == ProductContainerType.GRID
} == true
