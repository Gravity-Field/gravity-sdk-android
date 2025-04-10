package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Content
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GravityBottomSheetContent(
    content: Content,
    dismiss: () -> Unit,
) {
    val frameUi = content.variables.frameUI
    val container = frameUi?.container
    val padding = container?.style?.padding
    val close = frameUi?.close
    val elements = content.variables.elements

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .conditional(padding != null)
                {
                    padding(
                        start = padding!!.left.dp,
                        top = padding.top.dp,
                        end = padding.right.dp,
                        bottom = padding.bottom.dp
                    )
                },
            horizontalAlignment = container?.style?.contentAlignment?.toHorizontalAlignment()
                ?: Alignment.CenterHorizontally
        ) {
            elements.map { GravityElement(it, content.products) }
        }

        close?.let {
            CloseButton(it, dismiss)
        }
    }
}
