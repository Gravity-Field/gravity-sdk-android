package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProduct
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductAction
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlock
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlockBuilder
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductLayout
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private const val TAG = "AssistantBlock"

private val CardWidth = 164.dp
private val ImageHeight = 158.dp
private val ImageShape = RoundedCornerShape(16.dp)

/** Press highlight shape; smaller radius than the image so it does not clip the text. */
private val RippleShape = RoundedCornerShape(8.dp)
private val PlaceholderColor = Color(0xFFF2F4F7)
private val TitleColor = Color(0xFF101828)
private val BrandColor = Color(0xFF667085)
private val PriceColor = Color(0xFF101828)

private const val VisibleFraction = 0.5f
private const val VisibleDwellMillis = 1_000L

/** Demo product block: a title and a horizontal carousel of product cards. */
class AssistantProductBlockBuilderImpl : AssistantProductBlockBuilder {

    @Composable
    override fun Build(block: AssistantProductBlock, layout: AssistantProductLayout) {
        Column(modifier = Modifier.width(layout.availableWidth)) {
            block.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    text = title,
                    color = TitleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (block.products.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = "Подборка пуста",
                    color = BrandColor,
                    fontSize = 14.sp,
                )
                return@Column
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = block.products,
                    key = { it.itemId },
                ) { product ->
                    TrackVisible(product) {
                        ProductCard(product)
                    }
                }
            }
        }
    }
}

/**
 * Reports a product impression once at least half of the card has been visible for a second.
 * Every appearance is reported; the SDK filters duplicates.
 */
@Composable
private fun TrackVisible(product: AssistantProduct, content: @Composable () -> Unit) {
    val view = LocalView.current
    var isVisible by remember(product.itemId) { mutableStateOf(false) }

    LaunchedEffect(product.itemId, isVisible) {
        if (!isVisible) return@LaunchedEffect
        Log.d(TAG, "Half of ${product.itemId} is on screen")
        delay(VisibleDwellMillis)
        perform(product, AssistantProductAction.VisibleImpression)
    }

    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val window = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
            val bounds = coordinates.boundsInWindow().intersect(window)

            // Disjoint rects intersect with negative sides whose product would be positive.
            val visibleArea = bounds.width.coerceAtLeast(0f) * bounds.height.coerceAtLeast(0f)
            val cardArea = coordinates.size.width.toFloat() * coordinates.size.height.toFloat()

            isVisible = cardArea > 0f && visibleArea >= cardArea * VisibleFraction
        }
    ) {
        content()
    }
}

@Composable
private fun ProductCard(product: AssistantProduct) {
    Column(
        modifier = Modifier
            .width(CardWidth)
            // Clip before clickable, otherwise the ripple is drawn as a rectangle.
            .clip(RippleShape)
            .clickable(enabled = product.canOpen) {
                perform(product, AssistantProductAction.Tap)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ImageHeight)
                .clip(ImageShape)
                .background(PlaceholderColor),
            contentAlignment = Alignment.Center,
        ) {
            if (product.imageUrl != null) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = product.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(text = "Нет фото", color = BrandColor, fontSize = 12.sp)
            }
        }

        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = product.name,
            color = TitleColor,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        product.brand?.takeIf { it.isNotBlank() }?.let { brand ->
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = brand,
                color = BrandColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        product.price?.let { price ->
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = formatPrice(price),
                color = PriceColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Reports a product action to the SDK; any navigation comes back via AssistantActionHandler. */
private fun perform(product: AssistantProduct, action: AssistantProductAction) {
    val result = GravitySDK.instance.performAssistantProductAction(
        product = product.ref,
        action = action,
    )
    Log.d(TAG, "$action on ${product.itemId} = $result")
}

private fun formatPrice(price: Double): String {
    val format = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
    return "${format.format(price)} ₽"
}
