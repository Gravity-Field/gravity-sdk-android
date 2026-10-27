package ai.gravityfield.gravity_sdk.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal object ParseUtils {

    /** Parses `#RRGGBB` (opaque) and `#RRGGBBAA` (alpha last), same as the Flutter SDK. Anything else gives null. */
    fun parseColor(color: Any?): Color? {
        if (color == null) return null

        if (color is String && color.startsWith("#")) {
            val hex = color.substring(1)
            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

            return when (hex.length) {
                6 -> Color(("ff$hex").toLong(16))
                8 -> {
                    val rrggbb = hex.substring(0, 6)
                    val aa = hex.substring(6, 8)
                    Color(("$aa$rrggbb").toLong(16))
                }

                else -> null
            }
        }

        return null
    }

    fun parseDimension(dimension: Any?): Double? {
        if (dimension == null) return null

        return when (dimension) {
            is Double -> dimension
            is Int -> dimension.toDouble()
            is String -> {
                if (dimension.contains("px")) {
                    dimension.replace("px", "").toDoubleOrNull()
                } else {
                    dimension.toDoubleOrNull()
                }
            }

            else -> null
        }
    }

    fun parseFontSize(fontSize: Any?): TextUnit? {
        if (fontSize == null) return null

        return when (fontSize) {
            is Int -> fontSize.sp
            is Double -> fontSize.sp
            else -> null
        }
    }

    fun parseFontWeight(fontWeight: Any?): FontWeight? {
        if (fontWeight == null) return null

        if (fontWeight is String) {
            return when (fontWeight) {
                "100" -> FontWeight.W100
                "200" -> FontWeight.W200
                "300" -> FontWeight.W300
                "400" -> FontWeight.W400
                "500" -> FontWeight.W500
                "600" -> FontWeight.W600
                "700" -> FontWeight.W700
                "800" -> FontWeight.W800
                "900" -> FontWeight.W900
                else -> null
            }
        }

        return null
    }

    fun parseBoxFit(fit: Any?): ContentScale? {
        if (fit == null) {
            return null
        }

        if (fit is String) {
            return when (fit) {
                "fill" -> ContentScale.FillBounds
                "contain" -> ContentScale.Fit
                "cover" -> ContentScale.Crop
                "fitWidth" -> ContentScale.FillWidth
                "fitHeight" -> ContentScale.FillHeight
                "none" -> ContentScale.None
                "scaleDown" -> ContentScale.Inside
                else -> null
            }
        }

        return null
    }
}
