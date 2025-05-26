package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.R
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GravitySnackbarType1(
    data: SnackbarData,
    dismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .padding(start = 16.dp, bottom = 26.dp, end = 16.dp)
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, onClick = dismiss, indication = null),
        shape = RoundedCornerShape(data.borderRadius),
        color = data.color,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier.requiredSize(data.imageSize),
                painter = painterResource(data.image),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title.text,
                    color = data.title.color,
                    fontSize = data.title.fontSize,
                    fontWeight = data.title.fontWeight,
                    lineHeight = data.title.lineHeight,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.subtitle.text,
                    color = data.subtitle.color,
                    fontSize = data.subtitle.fontSize,
                    fontWeight = data.subtitle.fontWeight,
                    lineHeight = data.subtitle.lineHeight,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                modifier = Modifier
                    .padding(4.dp)
                    .requiredSize(data.buttonImageSize),
                painter = painterResource(data.buttonImage),
                contentDescription = null,
            )
        }
    }
}

internal data class SnackbarData(
    val color: Color,
    val contentAlignment: Alignment.Horizontal,
    val borderRadius: Dp,
    val title: GravityText,
    val subtitle: GravityText,
    @DrawableRes val image: Int,
    val imageSize: DpSize,
    @DrawableRes val buttonImage: Int,
    val buttonImageSize: DpSize,
)

internal val mockSnackbarData = SnackbarData(
    color = Color(0xFF868585),
    borderRadius = 16.dp,
    contentAlignment = Alignment.Start,
    title = GravityText(
        text = "Скидка 5% \uD83D\uDD25",
        color = Color(0xFF000000),
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 24.sp,
    ),
    subtitle = GravityText(
        text = "Для вас доступен промокод на первую покупку в Gravity",
        color = Color(0xFF535862),
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 18.sp,
    ),
    image = R.drawable.heart,
    imageSize = DpSize(width = 56.dp, height = 56.dp),
    buttonImage = R.drawable.ic_arrow_circle_right,
    buttonImageSize = DpSize(width = 32.dp, height = 32.dp),
)

internal class GravityText(
    val text: String,
    val color: Color,
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val lineHeight: TextUnit,
)

internal class GravityImage(
    @DrawableRes val url: Int,
    val size: DpSize,
)

internal class GravityButton(
    val title: GravityText,
    val color: Color,
    val outlineColor: Color,
    val pressColor: Color?,
    val cornerRadius: Dp,
)
