package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.ElementType
import androidx.compose.runtime.Composable

@Composable
fun GravityElement(
    element: Element,
) {
    when (element.type) {
        ElementType.IMAGE -> GravityImage(element = element)
        ElementType.TEXT -> GravityText(element = element)
        ElementType.BUTTON -> GravityButton(element = element)
        ElementType.UNKNOWN -> {}
    }
}
