package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.GravityTextStyle
import ai.gravityfield.gravity_sdk.models.Style
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors iOS GravityButton: only `textStyle.color` colours the label; otherwise the primary label colour. */
class GravityButtonTextColorTest {
    private fun style(json: Map<String, Any?>) = Style.fromJson(json)

    @Test fun textStyleColorIsUsed() {
        val style = style(mapOf("textStyle" to mapOf("color" to "#FF0000")))
        assertEquals(Color(0xFFFF0000), resolveButtonTextColor(style.textStyle, enabled = true, darkTheme = false))
        assertEquals(Color(0xFFFF0000), resolveButtonTextColor(style.textStyle, enabled = true, darkTheme = true))
    }

    @Test fun missingTextStyleUsesPrimaryLabelColourNotMaterialOnPrimary() {
        // A top-level `textColor` is not a button colour on iOS either.
        val style = style(mapOf("textColor" to "#FF0000", "backgroundColor" to "#00FF00"))
        assertEquals(Color.Black, resolveButtonTextColor(style.textStyle, enabled = true, darkTheme = false))
        assertEquals(Color.White, resolveButtonTextColor(style.textStyle, enabled = true, darkTheme = true))
    }

    @Test fun textStyleWithoutColorKeepsTheParsedDefault() {
        val style = style(mapOf("textStyle" to mapOf("fontSize" to 14)))
        assertEquals(Color.Black, resolveButtonTextColor(style.textStyle, enabled = true, darkTheme = false))
    }

    @Test fun disabledLeavesTheDisabledContentColour() {
        val textStyle: GravityTextStyle? = style(mapOf("textStyle" to mapOf("color" to "#FF0000"))).textStyle
        assertEquals(Color.Unspecified, resolveButtonTextColor(textStyle, enabled = false, darkTheme = false))
        assertEquals(Color.Unspecified, resolveButtonTextColor(null, enabled = false, darkTheme = false))
    }
}
