package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.Products
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProductsRow(
    element: Element,
    products: Products,
) {
    val style = element.style
    val height = style.size?.height
    val margin = style.margin

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
            .conditional(height != null) {
                height(height!!.dp)
            }
    ) {
        LazyRow(
            modifier = Modifier
                .conditional(style.backgroundColor != null)
                {
                    background(color = style.backgroundColor!!)
                },
            // todo: add to style
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products.slots.size) { index ->
                val slot = products.slots[index]
                GravityProduct(slot)
            }
        }
    }
}
