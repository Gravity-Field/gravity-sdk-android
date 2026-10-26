package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner

internal val LocalModalImpression = compositionLocalOf<ModalImpressionSession?> { null }

/** Watches actual content drawing, including opening translation; a scrim/layout alone cannot fire. */
@Composable
internal fun ModalVisibilityDetector(content: @Composable () -> Unit) {
    val opening = LocalModalImpression.current
    val view = LocalView.current
    val window = LocalWindowInfo.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(Modifier.onGloballyPositioned { coordinates[0] = it }.drawWithContent {
        drawContent()
        val layout = coordinates[0]
        if (opening != null && !opening.hasFired && layout?.isAttached == true) {
            val bounds = layout.boundsInWindow()
            val intersection = bounds.intersect(Rect(0f, 0f, view.width.toFloat(), view.height.toFloat()))
            if (intersection.width > 0f && intersection.height > 0f && view.isShown && window.isWindowFocused && view.hasWindowFocus()) {
                opening.frameDrawn(view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
                // Runs after traversal: the content frame, not merely layout or the scrim, was drawn.
                view.post {
                    val active = view.isAttachedToWindow && view.isShown && view.hasWindowFocus() &&
                        view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
                    opening.contentFrame(active)
                }
            }
        }
    }) { content() }
}
