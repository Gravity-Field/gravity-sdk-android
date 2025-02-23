package ai.gravityfield.gravity_sdk

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GravityFullScreen(
    data: FullScreenData,
    dismiss: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = data.color
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                IconButton(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .align(Alignment.CenterEnd)
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                text = data.title.text,
                color = data.title.color,
                fontSize = data.title.fontSize,
                fontWeight = data.title.fontWeight,
                lineHeight = data.title.lineHeight,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                text = data.subtitle.text,
                color = data.subtitle.color,
                fontSize = data.subtitle.fontSize,
                fontWeight = data.subtitle.fontWeight,
                lineHeight = data.subtitle.lineHeight,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Box(
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    painter = painterResource(data.image),
                    contentDescription = null,
                )
            }
            Button(
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 26.dp, end = 16.dp)
                    .height(48.dp)
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
        }
    }
}

data class FullScreenData(
    val color: Color,
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

val mockFullScreenData = FullScreenData(
    color = Color(0xFFFFFFFF),
    title = GravityText(
        text = "Подарок новчинкам",
        color = Color(0xFF000000),
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        lineHeight = 24.sp,
    ),
    subtitle = GravityText(
        text = "Получите 500 бонусов ща установку приложения Gravity Field",
        color = Color(0xFF535862),
        fontSize = 14.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 21.sp,
    ),
    buttonTitle = GravityText(
        text = "Продолжить",
        color = Color(0xFFFFFFFF),
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 24.sp,
    ),
    buttonColor = Color(0xFF034FFF),
    buttonOutlineColor = Color(0xFF034FFF),
    image = R.drawable.box,
    imageSize = DpSize(width = 306.dp, height = 356.dp),
    closeButtonImage = R.drawable.ic_close,
    closeButtonImageSize = DpSize(width = 24.dp, height = 24.dp),
)