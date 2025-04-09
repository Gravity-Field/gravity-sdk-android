package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
fun GravityImage(
    element: Element,
) {
    val style = element.style
    Image(
        modifier = Modifier
            .conditional(style.margin != null) {
                padding(
                    start = style.margin!!.left.dp,
                    top = style.margin.top.dp,
                    end = style.margin.right.dp,
                    bottom = style.margin.bottom.dp
                )
            }
            .conditional(style.size?.width != null && style.size.height != null) {
                requiredSize(
                    DpSize(
                        width = style.size!!.width!!.dp, height = style.size.height!!.dp
                    )
                )
            },
        painter = rememberAsyncImagePainter(model = element.src),
        contentDescription = null,
        contentScale = style.fit ?: ContentScale.Crop,
    )
}
