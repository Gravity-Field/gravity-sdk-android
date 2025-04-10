package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.ui.ProductsGrid
import ai.gravityfield.gravity_sdk.ui.ProductsRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GravityElement(
    element: Element,
    products: Products?,
) {
    when (element.type) {
        ElementType.IMAGE -> GravityImage(element = element)
        ElementType.TEXT -> GravityText(element = element)
        ElementType.BUTTON -> GravityButton(element = element)
        ElementType.SPACER -> Spacer(modifier = Modifier)
        ElementType.PRODUCTS_CONTAINER -> {
            if (products != null) {
                val row = element.style.rows
                if (row == null)
                    ProductsRow(element, products)
                else
                    ProductsGrid(element, products)
            }
        }

        ElementType.UNKNOWN -> {}
    }
}
