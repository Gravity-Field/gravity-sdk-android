package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.models.ProductContainerType
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.ItemsRow
import ai.gravityfield.gravity_sdk.ui.ProductsGrid
import ai.gravityfield.gravity_sdk.ui.ProductsRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ColumnScope.GravityElements(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
    item: Item? = null,
) {
    val elements = content.variables.elements
    val products = content.products

    elements.forEach {
        when (it.type) {
            ElementType.IMAGE -> GravityImage(it, onClickCallback, item)
            ElementType.TEXT -> GravityText(it, onClickCallback, item)
            ElementType.BUTTON -> GravityButton(it, onClickCallback, item)
            ElementType.WEBVIEW -> GravityWebview(it)
            ElementType.SPACER -> Spacer(modifier = Modifier.weight(it.style.weight ?: 1f))
            ElementType.PRODUCTS_CONTAINER -> {
                val slots = products?.slots
                if (slots != null) {
                    when (it.style.productContainerType) {
                        ProductContainerType.ROW ->
                            ProductsRow(it, slots, content, campaign)

                        ProductContainerType.GRID ->
                            ProductsGrid(it, slots, content, campaign)

                        else -> {}
                    }
                }
            }

            ElementType.ITEMS_CONTAINER -> {
                val items = content.items
                if (items != null) {
                    ItemsRow(it, items, onClickCallback)
                }
            }

            ElementType.UNKNOWN -> {}
        }
    }
}


@Composable
internal fun ColumnScope.GravityElementsInCard(
    elements: List<Element>,
    onClickCallback: (model: OnClickModel) -> Unit,
    item: Item,
) {
    elements.forEach {
        when (it.type) {
            ElementType.IMAGE -> GravityImage(it, onClickCallback, item)
            ElementType.TEXT -> GravityText(it, onClickCallback, item)
            ElementType.BUTTON -> GravityButton(it, onClickCallback, item)
            ElementType.WEBVIEW -> GravityWebview(it)
            ElementType.SPACER -> Spacer(modifier = Modifier.weight(it.style.weight ?: 1f))
            else -> {}
        }
    }
}
