package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.ItemCard
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElementsInCard
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun GravityItemCard(
    item: Item,
    itemCard: ItemCard,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val elements = itemCard.template.elements
    val frameUi = itemCard.template.frameUI

    val container = frameUi?.container
    val onClick = container?.onClick

    val style = container?.style
    val padding = style?.padding
    val width = style?.size?.width?.dp ?: 0.dp
    val height = style?.size?.height?.dp ?: 0.dp
    val cornerRadius = style?.cornerRadius?.dp ?: 0.dp
    val horizontalAlignment =
        style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
    val verticalArrangement =
        style?.verticalAlignment?.toVerticalArrangement() ?: Arrangement.Top
    val backgroundColor = style?.backgroundColor ?: MaterialTheme.colorScheme.background
    val backgroundImage = style?.backgroundImage
    val backgroundFit = style?.backgroundFit ?: ContentScale.Crop

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .conditional(onClick != null) {
                clickable(interactionSource = interactionSource, onClick = {
                    onClickCallback.invoke(onClick!!.copy(itemId = item.id))
                }, indication = null)
            },
    ) {
        if (backgroundImage != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = rememberAsyncImagePainter(model = backgroundImage),
                contentDescription = null,
                contentScale = backgroundFit,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .conditional(padding != null) {
                    padding(
                        start = padding!!.left.dp,
                        top = padding.top.dp,
                        end = padding.right.dp,
                        bottom = padding.bottom.dp
                    )
                },
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
        ) {
            GravityElementsInCard(elements, onClickCallback, item)
        }
    }
}
