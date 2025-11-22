package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.R
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

@SuppressLint("UseKtx")
class GravityInlineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val selector: String
    private val colorInt: Int
    private val cornerRadius: Float
    private val cornerRadiusTopStart: Float
    private val cornerRadiusTopEnd: Float
    private val cornerRadiusBottomStart: Float
    private val cornerRadiusBottomEnd: Float

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.GravityInlineView,
            defStyleAttr,
            0
        )
        selector = typedArray.getString(R.styleable.GravityInlineView_selector)
            ?: throw Exception("GravitySDK: selector must be provided to GravityInlineView")
        colorInt = typedArray.getColor(
            R.styleable.GravityInlineView_color,
            ContextCompat.getColor(context, android.R.color.transparent)
        )
        cornerRadius =
            typedArray.getDimension(R.styleable.GravityInlineView_cornerRadius, -1f)
        cornerRadiusTopStart =
            typedArray.getDimension(R.styleable.GravityInlineView_cornerRadiusTopStart, 0f)
        cornerRadiusTopEnd =
            typedArray.getDimension(R.styleable.GravityInlineView_cornerRadiusTopEnd, 0f)
        cornerRadiusBottomStart =
            typedArray.getDimension(R.styleable.GravityInlineView_cornerRadiusBottomStart, 0f)
        cornerRadiusBottomEnd =
            typedArray.getDimension(R.styleable.GravityInlineView_cornerRadiusBottomEnd, 0f)
        typedArray.recycle()
    }

    @Composable
    override fun Content() {
        GravityView(
            selector,
            colorInt,
            cornerRadius,
            cornerRadiusTopStart,
            cornerRadiusTopEnd,
            cornerRadiusBottomStart,
            cornerRadiusBottomEnd,
        ) {
            post {
                layoutParams = layoutParams.apply {
                    height = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        it.toFloat(),
                        resources.displayMetrics
                    ).toInt()
                }
            }
        }
    }
}

@Composable
private fun GravityView(
    selector: String,
    colorInt: Int,
    cornerRadius: Float,
    cornerRadiusTopStart: Float,
    cornerRadiusTopEnd: Float,
    cornerRadiusBottomStart: Float,
    cornerRadiusBottomEnd: Float,
    changeHeight: (Double) -> Unit,
) {
    var campaign by remember { mutableStateOf<Campaign?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selector) {
        campaign = null

        scope.launch {
            try {
                // TODO: get pageContext
//                val result = GravitySDK.instance.getContentBySelector(selector, null)
//                campaign = result.data.firstOrNull()
//                val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull()
//                val height = content?.variables?.frameUI?.container?.style?.size?.height
//                withContext(Dispatchers.Main) {
//                    if (content == null) {
//                        changeHeight(0.0)
//                    } else if (height != null) {
//                        changeHeight(height)
//                    }
//                }
            } catch (e: Exception) {
                changeHeight(0.0)
            }
        }
    }

    val shape = if (cornerRadius != -1f) RoundedCornerShape(cornerRadius) else RoundedCornerShape(
        topStart = cornerRadiusTopStart,
        topEnd = cornerRadiusTopEnd,
        bottomStart = cornerRadiusBottomStart,
        bottomEnd = cornerRadiusBottomEnd,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(colorInt),
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull()
        if (campaign != null && content != null) {
            val frameUi = content.variables.frameUI
            val container = frameUi?.container
            val style = container?.style
            val padding = style?.padding
            val horizontalAlignment =
                style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
            val backgroundImage = style?.backgroundImage
            val backgroundFit = style?.backgroundFit ?: ContentScale.Crop
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                ContentEventService.instance.sendContentImpression(content, campaign!!)
            }

            if (backgroundImage != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberAsyncImagePainter(model = backgroundImage),
                    contentDescription = null,
                    contentScale = backgroundFit,
                )
            }

            Column(
                modifier = Modifier
                    .conditional(padding != null)
                    {
                        padding(
                            start = padding!!.left.dp,
                            top = padding.top.dp,
                            end = padding.right.dp,
                            bottom = padding.bottom.dp
                        )
                    },
                horizontalAlignment = horizontalAlignment
            ) {
                GravityElements(
                    content,
                    campaign!!,
                    onClickCallback = { onClickModel ->
                        GravitySDK.instance.onClickHandler(
                            onClickModel,
                            content,
                            campaign!!,
                            context,
                        )
                    }
                )
            }
        }
    }
}
