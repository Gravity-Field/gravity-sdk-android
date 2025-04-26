package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.absolutePosition
import ai.gravityfield.gravity_sdk.models.Close
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
fun CloseButton(
    close: Close,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    Box(
        modifier = Modifier
            .absolutePosition(
                left = close.style.positioned?.left?.dp,
                top = close.style.positioned?.top?.dp,
                right = close.style.positioned?.right?.dp,
                bottom = close.style.positioned?.bottom?.dp,
            )
    ) {
        IconButton(
            onClick = {
                if (close.onClick != null) onClickCallback.invoke(close.onClick)
            }
        ) {
            if (close.image != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = close.image),
                    contentDescription = null,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                )
            }
        }
    }
}
