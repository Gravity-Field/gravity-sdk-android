package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GravityLayoutWidth
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
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
    val hasDeterminedWidth = width != null || layoutWidth == GravityLayoutWidth.MATCH_PARENT
    val contentScale = if (height == null && hasDeterminedWidth) {
        ContentScale.FillWidth
    } else {
        style.fit ?: ContentScale.Crop
    }

    val painter = rememberAsyncImagePainter(model = url)
    val painterState by painter.state.collectAsState()
    val isImageReady = painterState is AsyncImagePainter.State.Success

    val interactionSource = remember { MutableInteractionSource() }

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
            .conditional(height == null && !isImageReady) {
                height(0.dp)
            }
            .conditional(layoutWidth == GravityLayoutWidth.MATCH_PARENT) {
                fillMaxWidth()
            }
            .conditional(element.onClick != null) {
                clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClickCallback.invoke(element.onClick!!) }
            }
            .clip(RoundedCornerShape(cornerRadius)),
        painter = painter,
        contentDescription = null,
        contentScale = contentScale,
    )
}
