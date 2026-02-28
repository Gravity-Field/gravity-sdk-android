package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.absolutePosition
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Close
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
internal fun CloseButton(
    close: Close,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val image = close.image
    val style = close.style
    val positioned = style.positioned
    val width = style.size?.width
    val height = style.size?.height
    val contentScale = style.fit ?: ContentScale.Crop

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .absolutePosition(
                left = positioned?.left?.dp,
                top = positioned?.top?.dp,
                right = positioned?.right?.dp,
                bottom = positioned?.bottom?.dp,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (close.onClick != null) onClickCallback.invoke(close.onClick)
                }
            )
            .then(
                if (isPressed) {
                    Modifier.alpha(0.5f)
                } else {
                    Modifier
                }
            )
    ) {
        if (image != null) {
            Image(
                modifier = Modifier
                    .conditional(width != null) {
                        width(width!!.dp)
                    }
                    .conditional(height != null) {
                        height(height!!.dp)
                    },
                painter = rememberAsyncImagePainter(model = image),
                contentDescription = null,
                contentScale = contentScale
            )
        } else {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
            )
        }
    }
}
