package ai.gravityfield.gravity_sdk.extensions

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

internal fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}

internal fun Modifier.absolutePosition(
    left: Dp? = null,
    top: Dp? = null,
    right: Dp? = null,
    bottom: Dp? = null
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val x = left?.roundToPx()
            ?: (constraints.maxWidth - placeable.width - (right?.roundToPx() ?: 0))
        val y = top?.roundToPx()
            ?: (constraints.maxHeight - placeable.height - (bottom?.roundToPx() ?: 0))
        placeable.place(x, y)
    }
}
