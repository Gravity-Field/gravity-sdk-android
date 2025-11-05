package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.LocalAppFont
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GravityLayoutWidth
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
internal fun GravityButton(
    element: Element,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val style = element.style
    val textStyle = style.textStyle
    val layoutWidth = style.layoutWidth

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = style.backgroundColor ?: Color.Unspecified,
        contentColor = textStyle?.color ?: Color.Unspecified,
    )
    val cornerRadius = style.cornerRadius?.dp ?: 0.0.dp
    val rippleColor = style.pressColor
    val ripple = rippleColor?.let {
        object : RippleTheme {
            @Composable
            override fun defaultColor(): Color {
                return it
            }

            @Composable
            override fun rippleAlpha(): RippleAlpha {
                return RippleAlpha(
                    pressedAlpha = it.alpha,
                    focusedAlpha = it.alpha,
                    draggedAlpha = it.alpha,
                    hoveredAlpha = it.alpha
                )
            }
        }
    }
    CompositionLocalProvider(
        values = if (ripple == null) emptyArray() else arrayOf(LocalRippleTheme provides ripple)
    ) {
        Button(
            onClick = {
                if (element.onClick != null) {
                    onClickCallback.invoke(element.onClick)
                }
            },
            colors = buttonColors,
            shape = RoundedCornerShape(
                topStart = cornerRadius,
                topEnd = cornerRadius,
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
            ),
            modifier = Modifier
                .conditional(style.margin != null) {
                    padding(
                        start = style.margin!!.left.dp,
                        top = style.margin.top.dp,
                        end = style.margin.right.dp,
                        bottom = style.margin.bottom.dp
                    )
                }
                .conditional(layoutWidth == GravityLayoutWidth.MATCH_PARENT) {
                    fillMaxWidth()
                }
                .conditional(style.size?.height != null) {
                    height(style.size!!.height!!.dp)
                }
                .conditional(style.size == null) {
                    height(48.dp)
                },
        ) {
            Text(
                text = element.text ?: "",
                style = TextStyle(
                    color = textStyle?.color ?: Color.Unspecified,
                    fontSize = textStyle?.fontSize ?: TextUnit.Unspecified,
                    fontWeight = textStyle?.fontWeight ?: FontWeight.Normal,
                    fontFamily = LocalAppFont.current
                )
            )
        }
    }
}
