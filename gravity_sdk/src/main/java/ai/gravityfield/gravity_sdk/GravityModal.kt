package ai.gravityfield.gravity_sdk

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GravityModalType1(
    data: ModalData,
    dismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(data.borderRadius),
        color = data.color,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = data.contentAlignment,
                ) {
                    val textAlign =
                        if (data.contentAlignment == Alignment.Start) TextAlign.Left else TextAlign.Center
                    Image(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .requiredSize(data.imageSize),
                        painter = painterResource(data.image),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = data.title.text,
                        color = data.title.color,
                        fontSize = data.title.fontSize,
                        fontWeight = data.title.fontWeight,
                        lineHeight = data.title.lineHeight,
                        textAlign = textAlign,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = data.subtitle.text,
                        color = data.subtitle.color,
                        fontSize = data.subtitle.fontSize,
                        fontWeight = data.subtitle.fontWeight,
                        lineHeight = data.subtitle.lineHeight,
                        textAlign = textAlign,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                HorizontalDivider(
                    color = Color(0xFFE9EAEB),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    onClick = dismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = data.buttonColor),
                ) {
                    Text(
                        text = data.buttonTitle.text,
                        color = data.buttonTitle.color,
                        fontSize = data.buttonTitle.fontSize,
                        fontWeight = data.buttonTitle.fontWeight,
                        lineHeight = data.buttonTitle.lineHeight,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = data.secondaryButtonOutlineColor,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    shape = RoundedCornerShape(8.dp),
                    onClick = dismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = data.secondaryButtonColor,
                        contentColor = data.secondaryButtonOutlineColor
                    ),
                ) {
                    Text(
                        text = data.secondaryButtonTitle.text,
                        color = data.secondaryButtonTitle.color,
                        fontSize = data.secondaryButtonTitle.fontSize,
                        fontWeight = data.secondaryButtonTitle.fontWeight,
                        lineHeight = data.secondaryButtonTitle.lineHeight,
                    )
                }
            }
            IconButton(
                modifier = Modifier
                    .padding(all = 12.dp)
                    .align(Alignment.TopEnd)
                    .size(width = 44.dp, height = 44.dp),
                onClick = dismiss,
            ) {
                Image(
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .requiredSize(data.closeButtonImageSize)
                        .align(Alignment.Center),
                    painter = painterResource(data.closeButtonImage),
                    contentDescription = null,
                )

            }
        }
    }
}

data class ModalData(
    val color: Color,
    val contentAlignment: Alignment.Horizontal,
    val borderRadius: Dp,
    val title: GravityText,
    val subtitle: GravityText,
    val buttonTitle: GravityText,
    val buttonColor: Color,
    val buttonOutlineColor: Color,
    val secondaryButtonTitle: GravityText,
    val secondaryButtonColor: Color,
    val secondaryButtonOutlineColor: Color,
    @DrawableRes val image: Int,
    val imageSize: DpSize,
    @DrawableRes val closeButtonImage: Int,
    val closeButtonImageSize: DpSize,
)

val mockModalData = ModalData(
    color = Color(0xFFFFFFFF),
    borderRadius = 16.dp,
    contentAlignment = Alignment.Start,
    title = GravityText(
        text = "Скидка 5% \uD83D\uDD25",
        color = Color(0xFF000000),
        fontSize = 18.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 28.sp,
    ),
    subtitle = GravityText(
        text = "Поздравляем! Для вас доступен промокод на первую покупку в Gravity Ads",
        color = Color(0xFF535862),
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 20.sp,
    ),
    buttonTitle = GravityText(
        text = "Скопировать",
        color = Color(0xFFFFFFFF),
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 24.sp,
    ),
    buttonColor = Color(0xFF7F56D9),
    buttonOutlineColor = Color(0xFF7F56D9),
    secondaryButtonTitle = GravityText(
        text = "Отмена",
        color = Color(0xFF414651),
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 24.sp,
    ),
    secondaryButtonColor = Color(0xFFFFFFFF),
    secondaryButtonOutlineColor = Color(0xFF000000),
    image = R.drawable.ic_check,
    imageSize = DpSize(width = 56.dp, height = 56.dp),
    closeButtonImage = R.drawable.ic_close,
    closeButtonImageSize = DpSize(width = 24.dp, height = 24.dp),
)

class GravityText(
    val text: String,
    val color: Color,
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val lineHeight: TextUnit,
)
