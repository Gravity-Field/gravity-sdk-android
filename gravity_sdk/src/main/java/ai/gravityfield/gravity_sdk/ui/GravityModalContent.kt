package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Content
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GravityModalContent(
    content: Content,
    dismiss: () -> Unit,
) {
    val frameUi = content.variables.frameUI
    val container = frameUi.container
    val close = frameUi.close
    val elements = content.variables.elements

    Surface(
        shape = RoundedCornerShape(container.style.cornerRadius?.dp ?: 0.dp),
        color = container.style.backgroundColor ?: Color.White,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .conditional(container.style.padding != null) {
                        padding(
                            start = container.style.padding!!.left.dp,
                            top = container.style.padding.top.dp,
                            end = container.style.padding.right.dp,
                            bottom = container.style.padding.bottom.dp
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                elements.map { GravityElement(element = it) }
            }

            close?.let {
                CloseButton(it, dismiss)
            }
        }
    }
}
