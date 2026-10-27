package ai.gravityfield.gravity_sdk.utils

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Same vectors as ParseColorTests.swift in the iOS SDK; valid ones match Flutter's ParseUtils.parseColor.
class ParseColorTest {
    @Test
    fun rrggbb_isOpaque() {
        assertEquals(Color(0xFFFF0000), ParseUtils.parseColor("#FF0000"))
        assertEquals(Color(0xFF00FF00), ParseUtils.parseColor("#00FF00"))
        assertEquals(Color(0xFF0000FF), ParseUtils.parseColor("#0000FF"))
        assertEquals(Color(0xFF000000), ParseUtils.parseColor("#000000"))
        assertEquals(Color(0xFFFFFFFF), ParseUtils.parseColor("#FFFFFF"))
        assertEquals(Color(0xFF123456), ParseUtils.parseColor("#123456"))
    }

    @Test
    fun rrggbbaa_takesAlphaFromLastPair() {
        assertEquals(ParseUtils.parseColor("#FF0000"), ParseUtils.parseColor("#FF0000FF"))
        assertEquals(Color(0x80123456), ParseUtils.parseColor("#12345680"))
        assertEquals(Color(0x00123456), ParseUtils.parseColor("#12345600"))
    }

    @Test
    fun lowercaseAndUppercaseHex_areEqual() {
        assertEquals(ParseUtils.parseColor("#ABCDEF"), ParseUtils.parseColor("#abcdef"))
        assertEquals(ParseUtils.parseColor("#ABCDEF80"), ParseUtils.parseColor("#abcdef80"))
        assertEquals(Color(0xFFABCDEF), ParseUtils.parseColor("#abcdef"))
    }

    @Test
    fun invalidInput_givesNull() {
        assertNull(ParseUtils.parseColor(null))
        assertNull(ParseUtils.parseColor(0xFF0000))
        listOf(
            "", "FF0000", "#", "#FFF", "#FF00000", "#FF0000000",
            "#GG0000", "#+F0000", "#FF0000-1", "#0xFF00", " #FF0000", "#FF0000 ", "#ＦＦ0000",
            "#12345 ", "#123456+7", "#123456-7",
        ).forEach { assertNull(it, ParseUtils.parseColor(it)) }
    }
}
