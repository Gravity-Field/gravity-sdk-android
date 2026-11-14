package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.Style
import org.junit.Assert.*
import org.junit.Test

class DimensionParsingTest {
    @Test fun nonFiniteDimensionsAreAbsentAndInsetsDefaultToZero() {
        for (value in listOf("Infinity", "-Infinity", "NaN", "1e400", "Infinitypx",
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            assertNull("dimension=$value", ParseUtils.parseDimension(value))
            val style = Style.fromJson(mapOf(
                "size" to mapOf("width" to value, "height" to value),
                "padding" to mapOf("top" to value, "bottom" to value, "left" to value, "right" to value),
                "margin" to mapOf("top" to value, "bottom" to value, "left" to value, "right" to value),
                "cornerRadius" to value,
            ))
            assertNull(style.size!!.width)
            assertNull(style.size!!.height)
            assertNull(style.cornerRadius)
            assertEquals(listOf(0.0, 0.0, 0.0, 0.0), style.padding!!.run { listOf(top, bottom, left, right) })
            assertEquals(listOf(0.0, 0.0, 0.0, 0.0), style.margin!!.run { listOf(top, bottom, left, right) })
        }
    }

    @Test fun finiteDimensionsKeepTheirValues() {
        for (value in listOf(12, 12.0, "12", "12px")) assertEquals(12.0, ParseUtils.parseDimension(value))
        assertEquals(-2.5, ParseUtils.parseDimension("-2.5px"))
        assertEquals(0.0, ParseUtils.parseDimension(0))
    }
}
