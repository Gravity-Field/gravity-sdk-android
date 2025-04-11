package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.Content
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.ui.ProductsGrid
import ai.gravityfield.gravity_sdk.ui.ProductsRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ColumnScope.GravityElements(content: Content) {
    val elements = content.variables.elements
    val products = content.products

    elements.forEach {
        when (it.type) {
            ElementType.IMAGE -> GravityImage(it)
            ElementType.TEXT -> GravityText(it)
            ElementType.BUTTON -> GravityButton(it)
            ElementType.SPACER -> Spacer(modifier = Modifier.weight(it.style.weight ?: 1f))
            ElementType.PRODUCTS_CONTAINER -> {
                if (products != null) {
                    val row = it.style.rows
                    if (row == null)
                        ProductsRow(it, products)
                    else
                        ProductsGrid(it, products)
                }
            }

            ElementType.UNKNOWN -> {}
        }
    }
}
