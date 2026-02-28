package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GravityLayoutWidth
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun GravityImage(
    element: Element,
    onClickCallback: (model: OnClickModel) -> Unit,
    item: Item? = null,
) {
    val url = if (item != null) item.values[element.src] else element.src

    val style = element.style
    val cornerRadius = style.cornerRadius?.dp ?: 0.0.dp
    val margin = style.margin
    val width = style.size?.width
    val height = style.size?.height
    val layoutWidth = style.layoutWidth
    val contentScale = style.fit ?: ContentScale.Crop

    Image(
        modifier = Modifier
            .conditional(margin != null) {
                padding(
                    start = margin!!.left.dp,
                    top = margin.top.dp,
                    end = margin.right.dp,
                    bottom = margin.bottom.dp
                )
            }
            .conditional(width != null) {
                width(width!!.dp)
            }
            .conditional(height != null) {
                height(height!!.dp)
            }
            .conditional(layoutWidth == GravityLayoutWidth.MATCH_PARENT) {
                fillMaxWidth()
            }
            .conditional(element.onClick != null) {
                clickable { onClickCallback.invoke(element.onClick!!) }
            }
            .clip(RoundedCornerShape(cornerRadius)),
        painter = rememberAsyncImagePainter(model = url),
        contentDescription = null,
        contentScale = contentScale,
    )
}
