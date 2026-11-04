package ai.gravityfield.gravity_sdk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import android.view.ViewTreeObserver

@Composable
internal fun VisibilityDetector(
    onVisible: () -> Unit,
    once: Boolean = false,
    visibilityKey: Any? = null,
    observeViewportChanges: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var isVisible by remember(visibilityKey) { mutableStateOf(false) }
    var hasBeenVisible by remember(visibilityKey) { mutableStateOf(false) }
    val currentOnVisible by rememberUpdatedState(onVisible)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    fun updateVisibility(layoutCoordinates: LayoutCoordinates) {
        if (!layoutCoordinates.isAttached || !view.isShown) {
            isVisible = false
            return
        }
        val visibleRect = android.graphics.Rect()
        if (!view.getLocalVisibleRect(visibleRect)) {
            isVisible = false
            return
        }
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val windowRect = Rect(
            (visibleRect.left + location[0]).toFloat(),
            (visibleRect.top + location[1]).toFloat(),
            (visibleRect.right + location[0]).toFloat(),
            (visibleRect.bottom + location[1]).toFloat(),
        )
        val widgetRect = layoutCoordinates.boundsInWindow()
        val widgetSize = layoutCoordinates.size
        val intersection = widgetRect.intersect(windowRect)
        val visibleArea = intersection.width.coerceAtLeast(0f) * intersection.height.coerceAtLeast(0f)
        val fullWidgetArea = widgetSize.width.toFloat() * widgetSize.height
        isVisible = fullWidgetArea > 0 && visibleArea >= fullWidgetArea * 0.5f
    }

    // A parent can change its clipping viewport without repositioning the card.
    // Grid cells must recheck that viewport when the host draws or scrolls.
    DisposableEffect(view, visibilityKey, observeViewportChanges) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            coordinates[0]?.let(::updateVisibility)
            true
        }
        if (observeViewportChanges) observer.addOnPreDrawListener(listener)
        onDispose {
            if (observeViewportChanges && observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }

    LaunchedEffect(isVisible, visibilityKey, once) {
        if (!isVisible || (once && hasBeenVisible)) return@LaunchedEffect
        delay(1000)

        if (isVisible && (!once || !hasBeenVisible)) {
            hasBeenVisible = true
            currentOnVisible()
        }
    }

    Box(modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
        coordinates[0] = layoutCoordinates
        updateVisibility(layoutCoordinates)
    }) {
        content()
    }
}
