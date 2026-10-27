package ai.gravityfield.gravity_sdk.ui.gravity_elements

import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.OnClickModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GravityButtonStateTest {
    private val onClick = OnClickModel(
        action = Action.CLOSE,
        copyData = null,
        step = null,
        itemId = null,
        url = null,
        type = null,
        deeplink = null,
        closeOnClick = true,
    )

    @Test
    fun enabledWithOnClick_isEnabled() {
        assertTrue(isButtonEnabled(enabled = true, onClick = onClick))
    }

    @Test
    fun noOnClick_isDisabled() {
        assertFalse(isButtonEnabled(enabled = true, onClick = null))
    }

    @Test
    fun disabledWithOnClick_isDisabled() {
        assertFalse(isButtonEnabled(enabled = false, onClick = onClick))
    }
}
