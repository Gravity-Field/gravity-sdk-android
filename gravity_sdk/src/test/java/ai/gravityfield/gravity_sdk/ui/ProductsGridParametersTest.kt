package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.GridSettings
import ai.gravityfield.gravity_sdk.models.Style
import org.junit.Assert.*
import org.junit.Test

class ProductsGridParametersTest {
    private fun parameters(vararg pairs: Pair<String, Any?>) =
        ProductsGridParameters.fromStyle(Style.fromJson(mapOf(*pairs)))

    @Test fun adminFixturesUseNaturalHeightAndGridSettings() {
        for (fixture in 1..4) {
            val style = mutableMapOf<String, Any?>(
                "backgroundColor" to "#fffff000", "productContainerType" to "grid",
                "size" to mapOf("height" to if (fixture == 4) 400 else 250),
                "rowSpacing" to 8,
                "margin" to mapOf("left" to 16, "right" to 16, "top" to 16, "bottom" to 16))
            if (fixture >= 2) style["gridSettings"] = mapOf("columns" to 4, "horizontalSpacing" to 6, "verticalSpacing" to 6)
            if (fixture >= 3) style["padding"] = mapOf("left" to 5, "right" to 5, "top" to 5, "bottom" to 5)
            val element = Element.fromJson(mapOf("type" to "products-container", "style" to style))
            val params = ProductsGridParameters.fromStyle(element.style)
            assertEquals(if (fixture == 1) 2 else 4, params.columns)
            assertEquals(if (fixture == 1) 8.0 else 6.0, params.horizontalSpacing, 0.0)
            assertEquals(params.horizontalSpacing, params.verticalSpacing, 0.0)
            assertEquals(1f, params.aspectRatio, 0f)
            assertEquals(16.0, element.style.margin!!.left, 0.0)
            assertEquals(if (fixture >= 3) 5.0 else 0.0, element.style.padding?.left ?: 0.0, 0.0)
        }
    }

    @Test fun columnPrecedenceAndInvalidValuesFallBack() {
        assertEquals(2, parameters().columns)
        assertEquals(3, parameters("gridColumns" to 3).columns)
        assertEquals(4, parameters("gridColumns" to 3, "gridSettings" to mapOf("columns" to 4)).columns)
        assertEquals(3, parameters("gridColumns" to 3, "gridSettings" to emptyMap<String, Any>()).columns)
        for (invalid in listOf(null, 0, -1, 13, 12.1, "13px", "invalid", true, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(3, parameters("gridColumns" to 3, "gridSettings" to mapOf("columns" to invalid)).columns)
            assertEquals(2, parameters("gridSettings" to mapOf("columns" to invalid)).columns)
        }
        assertEquals(3, ProductsGridParameters.fromStyle(Style(gridColumns = 3,
            gridSettings = GridSettings(columns = 0))).columns)
        assertEquals(2, parameters("gridColumns" to -3).columns)
        assertEquals(2, parameters("gridSettings" to mapOf("columns" to Long.MAX_VALUE)).columns)
        assertEquals(2, parameters("gridColumns" to 100).columns)
        assertEquals(2, parameters("gridColumns" to Long.MAX_VALUE).columns)
        assertEquals(2, parameters("gridSettings" to "invalid").columns)
    }

    @Test fun publicSettingsAndLegacyColumnsUseTheSameBounds() {
        for (columns in listOf(1, 2, 12)) {
            assertEquals(columns, parameters("gridSettings" to mapOf("columns" to columns)).columns)
            assertEquals(columns, ProductsGridParameters.fromStyle(Style(gridSettings = GridSettings(columns = columns))).columns)
        }
        for (columns in listOf(-1, 0, 13, Int.MAX_VALUE)) {
            assertEquals(3, ProductsGridParameters.fromStyle(Style(gridColumns = 3,
                gridSettings = GridSettings(columns = columns))).columns)
            assertEquals(2, ProductsGridParameters.fromStyle(Style(gridColumns = columns)).columns)
        }
        assertEquals(2, parameters("gridColumns" to 12.9).columns)
    }

    @Test fun spacingAcceptsZeroAndFractionsButRejectsNegativeAndNonNumbers() {
        val valid = parameters("gridSettings" to mapOf("horizontalSpacing" to 0, "verticalSpacing" to 2.5))
        assertEquals(0.0, valid.horizontalSpacing, 0.0)
        assertEquals(2.5, valid.verticalSpacing, 0.0)
        for (invalid in listOf(-1, "-1px", "invalid", true, Double.NaN, Double.POSITIVE_INFINITY)) {
            val params = parameters("gridSettings" to mapOf("horizontalSpacing" to invalid, "verticalSpacing" to invalid))
            assertEquals(8.0, params.horizontalSpacing, 0.0)
            assertEquals(8.0, params.verticalSpacing, 0.0)
        }
    }

    @Test fun gridSettingsAcceptNumericStringsAndPixelDimensions() {
        for (columns in listOf("4", "4px", "4.9")) {
            val parsed = parameters("gridSettings" to mapOf("columns" to columns,
                "horizontalSpacing" to "6", "verticalSpacing" to "2.5px"))
            assertEquals(4, parsed.columns)
            assertEquals(6.0, parsed.horizontalSpacing, 0.0)
            assertEquals(2.5, parsed.verticalSpacing, 0.0)
        }
        assertEquals(2, parameters("gridSettings" to mapOf("columns" to "100px")).columns)
    }

    @Test fun gridCardsRemainSquareRegardlessOfConfiguredDimensions() {
        assertEquals(1f, parameters("size" to mapOf("width" to 400, "height" to 200)).aspectRatio, 0f)
        for (size in listOf(mapOf("height" to 400), mapOf("width" to 400),
            mapOf("width" to 0, "height" to 200), mapOf("width" to 200, "height" to -1),
            mapOf("width" to Double.POSITIVE_INFINITY, "height" to 200))) {
            assertEquals(1f, parameters("size" to size).aspectRatio, 0f)
        }
    }
}
