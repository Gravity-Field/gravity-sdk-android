package ai.gravityfield.gravity_sdk

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit


class GravityText(
    val text: String,
    val color: Color,
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val lineHeight: TextUnit,
)

class GravityImage(
    @DrawableRes val url: Int,
    val size: DpSize,
)

class GravityButton(
    val title: GravityText,
    val color: Color,
    val outlineColor: Color,
    val pressColor: Color?,
    val cornerRadius: Dp,
)
