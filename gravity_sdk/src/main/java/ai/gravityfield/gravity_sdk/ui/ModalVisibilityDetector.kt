package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import android.view.Choreographer
import android.view.ViewTreeObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner

internal val LocalModalImpression = compositionLocalOf<ModalImpressionSession?> { null }

/** Watches actual content drawing, including opening translation; a scrim/layout alone cannot fire. */
@Composable
internal fun ModalVisibilityDetector(content: @Composable () -> Unit) {
    val opening = LocalModalImpression.current
    val view = LocalView.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val drawn = remember(opening) { booleanArrayOf(false) }

    fun activeContent(): Boolean {
        val layout = coordinates[0] ?: return false
        if (!drawn[0] || !layout.isAttached || !view.isAttachedToWindow || !view.isShown || !view.hasWindowFocus()) return false
        if (view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
        val bounds = layout.boundsInWindow()
        val intersection = bounds.intersect(Rect(0f, 0f, view.width.toFloat(), view.height.toFloat()))
        return intersection.width > 0f && intersection.height > 0f
    }

    // Material sheet animations can move a cached graphics layer without invoking drawWithContent.
    // Bound animation probing; later real draws/focus changes still confirm delayed content.
    DisposableEffect(view, opening) {
        val choreographer = Choreographer.getInstance()
        var disposed = false
        var framesRemaining = 120
        fun pending() = !disposed && opening != null && !opening.hasFired && !opening.isAbandoned
        val confirm = Runnable {
            if (pending() && activeContent()) opening?.contentFrame(active = true)
        }
        fun postConfirmation(requirePreviousDraw: Boolean = true) {
            if (!pending() || (requirePreviousDraw && !drawn[0])) return
            view.removeCallbacks(confirm)
            view.post(confirm)
        }
        val observer = view.viewTreeObserver
        // OnDraw precedes drawing: the posted runnable checks drawn after this traversal,
        // including a first draw that happens after the animation probe budget is exhausted.
        val onDraw = ViewTreeObserver.OnDrawListener { postConfirmation(requirePreviousDraw = false) }
        val onFocus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) postConfirmation() }
        observer.addOnDrawListener(onDraw)
        observer.addOnWindowFocusChangeListener(onFocus)
        val frame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!pending() || framesRemaining-- <= 0) return
                postConfirmation() // after this frame's layout/draw traversal
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frame)
        onDispose {
            disposed = true
            choreographer.removeFrameCallback(frame)
            view.removeCallbacks(confirm)
            if (observer.isAlive) {
                observer.removeOnDrawListener(onDraw)
                observer.removeOnWindowFocusChangeListener(onFocus)
            }
        }
    }
    Box(Modifier.onGloballyPositioned { coordinates[0] = it }.drawWithContent {
        drawContent()
        drawn[0] = true
        // Explicit close can race the posted confirmation after this actual content draw.
        if (activeContent()) opening?.frameDrawn(active = true)
    }) { content() }
}
