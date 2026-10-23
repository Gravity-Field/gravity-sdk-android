// TODO: migrate to material3 LocalRippleConfiguration / RippleConfiguration
@file:Suppress("DEPRECATION_ERROR")

package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.LocalAppFont
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GravityLayoutWidth
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
    item: Item? = null,
    enabled: Boolean = true,
) {
    val text = if (item != null) item.values[element.text] else element.text

    val style = element.style
    val textStyle = style.textStyle
    val paddingValues = if (style.padding != null)
        PaddingValues(
            start = style.padding.left.dp,
            top = style.padding.top.dp,
            end = style.padding.right.dp,
            bottom = style.padding.bottom.dp
        )
    else PaddingValues()
    val width = style.size?.width
    val height = style.size?.height
    val layoutWidth = style.layoutWidth
    val margin = style.margin
    val horizontalAlignment = style.contentAlignment?.toAlignment() ?: Alignment.Center
    val isEnabled = isButtonEnabled(enabled, element.onClick)

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = style.backgroundColor ?: Color.Unspecified,
        contentColor = textStyle?.color ?: Color.Unspecified,
    )
    val cornerRadius = style.cornerRadius?.dp ?: DEFAULT_CORNER_RADIUS.dp
    val border = resolveOutlineColor(style.outlineColor, isEnabled)?.let { BorderStroke(1.dp, it) }
    val rippleColor = style.pressColor
    // TODO: no-op on material3 1.3.0+ — MaterialTheme wires LocalIndication to its own ripple(),
    //  which reads LocalRippleConfiguration instead of LocalRippleTheme. The version that applies
    //  here is the one the host app resolves, so pressColor is already ignored on modern Compose.
    //  Replacement: CompositionLocalProvider(LocalRippleConfiguration provides
    //  RippleConfiguration(color = it, rippleAlpha = RippleAlpha(...))).
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
            enabled = isEnabled,
            colors = buttonColors,
            shape = RoundedCornerShape(cornerRadius),
            border = border,
            contentPadding = paddingValues,
            modifier = Modifier
                .conditional(margin != null) {
                    padding(
                        start = margin!!.left.dp,
                        top = margin.top.dp,
                        end = margin.right.dp,
                        bottom = margin.bottom.dp
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .conditional(width != null) {
                        width(width!!.dp)
                    }
                    .conditional(height != null) {
                        height(height!!.dp)
                    }
                    .conditional(layoutWidth == GravityLayoutWidth.MATCH_PARENT) {
                        fillMaxWidth()
                    },
                contentAlignment = horizontalAlignment,
            ) {
                Text(
                    text = text ?: "",
                    style = TextStyle(
                        // Unspecified when disabled so the Button's disabledContentColor applies
                        color = if (isEnabled) textStyle?.color ?: Color.Unspecified else Color.Unspecified,
                        fontSize = textStyle?.fontSize ?: TextUnit.Unspecified,
                        fontWeight = textStyle?.fontWeight ?: FontWeight.Normal,
                        fontFamily = LocalAppFont.current
                    )
                )
            }
        }
    }
}

internal fun isButtonEnabled(enabled: Boolean, onClick: OnClickModel?): Boolean {
    return enabled && onClick != null
}

internal fun resolveOutlineColor(outlineColor: Color?, enabled: Boolean): Color? {
    if (outlineColor == null) return null
    return if (enabled) outlineColor else outlineColor.copy(alpha = DISABLED_OUTLINE_ALPHA)
}

private const val DISABLED_OUTLINE_ALPHA = 0.4f
private const val DEFAULT_CORNER_RADIUS = 8.0
