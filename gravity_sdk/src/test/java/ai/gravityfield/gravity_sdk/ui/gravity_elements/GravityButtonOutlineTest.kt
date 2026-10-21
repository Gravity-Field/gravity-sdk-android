package ai.gravityfield.gravity_sdk.ui.gravity_elements

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GravityButtonOutlineTest {
    @Test
    fun noOutlineColor_noBorder() {
        assertNull(resolveOutlineColor(null, enabled = true))
        assertNull(resolveOutlineColor(null, enabled = false))
    }

    @Test
    fun enabled_keepsOutlineColor() {
        val color = Color(0xFF112233)
        assertEquals(color, resolveOutlineColor(color, enabled = true))
    }

    @Test
    fun disabled_setsOutlineAlphaTo04() {
        val color = Color(0xFF112233)
        assertEquals(color.copy(alpha = 0.4f), resolveOutlineColor(color, enabled = false))
    }
}
