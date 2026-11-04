package ai.gravityfield.gravity_sdk.ui

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/** Count appearance after a real active draw, then use the existing one-second visibility rule. */
@Composable
internal fun SnackbarVisibilityDetector(
    isCurrent: () -> Boolean,
    onAppear: () -> Unit,
    onVisible: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val owner = view.findViewTreeLifecycleOwner()
    val hasDrawn = remember { booleanArrayOf(false) }
    var drawn by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(snackbarActive(view)) }
    var visibilityInterval by remember { mutableStateOf(0L) }
    val interval = visibilityInterval
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentCheck by rememberUpdatedState(isCurrent)
    val currentAppear by rememberUpdatedState(onAppear)
    val currentVisible by rememberUpdatedState(onVisible)

    fun confirmAppearance() {
        val bounds = coordinates[0]?.takeIf { it.isAttached }?.boundsInWindow()
        val visible = bounds?.intersect(Rect(0f, 0f, view.width.toFloat(), view.height.toFloat()))
        if (hasDrawn[0] && visible != null && visible.width > 0 && visible.height > 0 && snackbarActive(view) && currentCheck()) {
            drawn = true
            currentAppear()
        }
    }

    DisposableEffect(view, owner) {
        // Focus callbacks can precede View.hasWindowFocus changing; recheck after dispatch.
        val confirm = Runnable { active = snackbarActive(view); confirmAppearance() }
        fun postConfirmation() { view.removeCallbacks(confirm); view.post(confirm) }
        fun resetInterval() {
            // Invalidate a pending callback synchronously, even if focus returns before composition.
            visibilityInterval++
            active = false
        }
        val lifecycle = LifecycleEventObserver { _, _ ->
            if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) resetInterval()
            postConfirmation()
        }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) resetInterval()
            postConfirmation()
        }
        owner?.lifecycle?.addObserver(lifecycle)
        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focus)
        onDispose {
            view.removeCallbacks(confirm)
            owner?.lifecycle?.removeObserver(lifecycle)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
        }
    }
    VisibilityDetector(
        enabled = drawn && active,
        once = true,
        intervalKey = interval,
        observeViewportChanges = true,
        canNotify = { interval == visibilityInterval && snackbarActive(view) && currentCheck() },
        onVisible = { currentVisible() },
    ) {
        Box(Modifier.onGloballyPositioned { coordinates[0] = it }.drawWithContent {
            drawContent()
            hasDrawn[0] = true
            confirmAppearance()
        }) { content() }
    }
}

private fun snackbarActive(view: View): Boolean = view.isAttachedToWindow && view.isShown && view.hasWindowFocus() &&
    view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
