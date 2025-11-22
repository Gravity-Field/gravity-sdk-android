package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun GravityWebview(
    element: Element,
) {
    val style = element.style
    val padding = style.padding
    val cornerRadius = style.cornerRadius?.dp ?: 0.0.dp
    val url = element.src ?: ""

    Box(
        modifier = Modifier
            .conditional(padding != null) {
                padding(
                    start = padding!!.left.dp,
                    top = padding.top.dp,
                    end = padding.right.dp,
                    bottom = padding.bottom.dp
                )
            }
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = {
                it.loadUrl(url)
            },
        )
    }
}
