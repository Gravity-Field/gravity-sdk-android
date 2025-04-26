package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.Content
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.models.ProductContainerType
import ai.gravityfield.gravity_sdk.ui.ProductsGrid
import ai.gravityfield.gravity_sdk.ui.ProductsRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ColumnScope.GravityElements(
    content: Content,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val elements = content.variables.elements
    val products = content.products

    elements.forEach {
        when (it.type) {
            ElementType.IMAGE -> GravityImage(it, onClickCallback)
            ElementType.TEXT -> GravityText(it, onClickCallback)
            ElementType.BUTTON -> GravityButton(it, onClickCallback)
            ElementType.SPACER -> Spacer(modifier = Modifier.weight(it.style.weight ?: 1f))
            ElementType.PRODUCTS_CONTAINER -> {
                if (products != null) {
                    when (it.style.productContainerType) {
                        ProductContainerType.ROW -> ProductsRow(it, products)
                        ProductContainerType.GRID -> ProductsGrid(it, products)
                        else -> {}
                    }
                }
            }

            ElementType.UNKNOWN -> {}
        }
    }
}
