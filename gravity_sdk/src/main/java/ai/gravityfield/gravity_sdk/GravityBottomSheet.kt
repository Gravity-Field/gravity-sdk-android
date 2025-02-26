package ai.gravityfield.gravity_sdk

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GravityBottomSheetType1(
    data: BottomSheetData,
    dismiss: () -> Unit,
) {
    Box {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Image(
                modifier = Modifier.requiredSize(data.imageSize),
                painter = painterResource(data.image),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                modifier = Modifier.padding(horizontal = 35.5.dp),
                text = data.title.text,
                color = data.title.color,
                fontSize = data.title.fontSize,
                fontWeight = data.title.fontWeight,
                lineHeight = data.title.lineHeight,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                modifier = Modifier.padding(horizontal = 35.5.dp),
                text = data.subtitle.text,
                color = data.subtitle.color,
                fontSize = data.subtitle.fontSize,
                fontWeight = data.subtitle.fontWeight,
                lineHeight = data.subtitle.lineHeight,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier
                    .padding(horizontal = 43.5.dp)
                    .height(48.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(90.dp),
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
            Spacer(modifier = Modifier.height(24.dp))
        }
        IconButton(
            modifier = Modifier
                .padding(end = 2.dp)
                .align(Alignment.TopEnd)
                .size(width = 44.dp, height = 44.dp),
            onClick = dismiss,
        ) {
            Image(
                modifier = Modifier
                    .padding(all = 10.dp)
                    .requiredSize(DpSize(width = 24.dp, height = 24.dp))
                    .align(Alignment.Center),
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
            )
        }
    }
}

data class BottomSheetData(
    val color: Color,
    val borderRadius: Dp,
    val title: GravityText,
    val subtitle: GravityText,
    val buttonTitle: GravityText,
    val buttonColor: Color,
    val buttonOutlineColor: Color,
    @DrawableRes val image: Int,
    val imageSize: DpSize,
    @DrawableRes val closeButtonImage: Int,
    val closeButtonImageSize: DpSize,
)

val mockBottomSheetData = BottomSheetData(
    color = Color(0xFFFFFFFF),
    borderRadius = 16.dp,
    title = GravityText(
        text = "Осталось 3 дня!",
        color = Color(0xFF000000),
        fontSize = 18.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 28.sp,
    ),
    subtitle = GravityText(
        text = "Ваш промокод вот-вот сгорит — воспользуетесь?",
        color = Color(0xFF535862),
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 20.sp,
    ),
    buttonTitle = GravityText(
        text = "За покупками!",
        color = Color(0xFFFFFFFF),
        fontSize = 16.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 24.sp,
    ),
    buttonColor = Color(0xFF3595FE),
    buttonOutlineColor = Color(0xFF3595FE),

    image = R.drawable.bell,
    imageSize = DpSize(width = 96.dp, height = 96.dp),
    closeButtonImage = R.drawable.ic_close,
    closeButtonImageSize = DpSize(width = 24.dp, height = 24.dp),
)