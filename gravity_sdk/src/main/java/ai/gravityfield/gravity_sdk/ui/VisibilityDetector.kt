package ai.gravityfield.gravity_sdk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

@Composable
fun VisibilityDetector(
    onVisible: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }
    var lastVisibilityChangeTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        delay(1000)

        if (isVisible) {
            onVisible()
        }
    }

    Box(modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
        val windowRect = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val widgetRect = layoutCoordinates.boundsInWindow()
        val widgetSize = layoutCoordinates.size

        val intersection = widgetRect.intersect(windowRect)
        val visibleArea = intersection.width * intersection.height
        val fullWidgetArea = widgetSize.width * widgetSize.height

        val nowVisible = visibleArea >= fullWidgetArea * 0.5f
        if (isVisible != nowVisible) {
            isVisible = nowVisible
        }
    }) {
        content()
    }
}