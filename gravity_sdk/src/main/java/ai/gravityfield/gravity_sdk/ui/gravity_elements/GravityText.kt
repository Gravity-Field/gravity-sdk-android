package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GravityContentAlignment
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
internal fun GravityText(
    element: Element,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val style = element.style

    val textStyle = TextStyle(
        color = style.textColor ?: Color.Unspecified,
        fontSize = style.fontSize ?: TextUnit.Unspecified,
        fontWeight = style.fontWeight ?: FontWeight.Normal
    )

    val textAlign = when (style.contentAlignment) {
        GravityContentAlignment.START -> TextAlign.Start
        GravityContentAlignment.CENTER -> TextAlign.Center
        GravityContentAlignment.END -> TextAlign.End
        else -> null
    }

    val paddingValues = if (style.margin != null)
        PaddingValues(
            start = style.margin.left.dp,
            top = style.margin.top.dp,
            end = style.margin.right.dp,
            bottom = style.margin.bottom.dp
        )
    else PaddingValues()


    Text(
        modifier = Modifier
            .padding(paddingValues)
            .conditional(element.onClick != null) {
                clickable { onClickCallback.invoke(element.onClick!!) }
            },
        text = element.text ?: "",
        textAlign = textAlign,
        style = textStyle,
    )
}
