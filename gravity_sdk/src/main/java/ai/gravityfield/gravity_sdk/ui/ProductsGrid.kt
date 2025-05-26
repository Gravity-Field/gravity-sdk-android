package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.network.Campaign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ProductsGrid(
    element: Element,
    products: Products,
    content: CampaignContent,
    campaign: Campaign
) {
    val style = element.style
    val height = style.size?.height
    val margin = style.margin
    val columns = style.gridColumns ?: 3

    LazyVerticalGrid(
        modifier = Modifier
            .conditional(height != null) {
                height(height!!.dp)
            }
            .fillMaxWidth()
            .conditional(margin != null) {
                padding(
                    start = margin!!.left.dp,
                    top = margin.top.dp,
                    end = margin.right.dp,
                    bottom = margin.bottom.dp
                )
            }
            .conditional(style.backgroundColor != null)
            {
                background(color = style.backgroundColor!!)
            },
        columns = GridCells.Fixed(columns),

        // todo: add to style
        // contentPadding = PaddingValues(horizontal = 16.dp),
        // todo: add to style
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products.slots.size) { index ->
            val slot = products.slots[index]
            GravityProduct(slot)
        }
    }
}
