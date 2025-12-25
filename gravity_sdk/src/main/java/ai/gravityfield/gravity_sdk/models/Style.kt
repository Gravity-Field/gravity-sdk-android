package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.utils.ParseUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class Style(
    val backgroundColor: Color? = null,
    val backgroundImage: String? = null,
    val backgroundFit: ContentScale? = null,
    val pressColor: Color? = null,
    val outlineColor: Color? = null,
    val cornerRadius: Double? = null,
    val size: GravitySize? = null,
    val margin: GravityMargin? = null,
    val padding: GravityPadding? = null,
    val fontSize: TextUnit? = null,
    val fontWeight: FontWeight? = null,
    val textColor: Color? = null,
    val textStyle: GravityTextStyle? = null,
    val fit: ContentScale? = null,
    val contentAlignment: GravityContentAlignment? = null,
    val verticalAlignment: GravityContentAlignment? = null,
    val layoutWidth: GravityLayoutWidth? = null,
    val positioned: GravityPositioned? = null,
    val weight: Float? = null,
    val productContainerType: ProductContainerType? = null,
    val gridColumns: Int? = null,
    val rowSpacing: Int? = null,
) {
    companion object {
        val empty = Style()

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Style {
            return Style(
                backgroundColor = ParseUtils.parseColor(json["backgroundColor"]),
                backgroundImage = json["backgroundImage"] as? String,
                backgroundFit = ParseUtils.parseBoxFit(json["backgroundFit"]),
                pressColor = ParseUtils.parseColor(json["pressColor"]),
                outlineColor = ParseUtils.parseColor(json["outlineColor"]),
                cornerRadius = ParseUtils.parseDimension(json["cornerRadius"]),
                size = json["size"]?.let { GravitySize.fromJson(it as Map<String, Any?>) },
                margin = json["margin"]?.let { GravityMargin.fromJson(it as Map<String, Any?>) },
                padding = json["padding"]?.let { GravityPadding.fromJson(it as Map<String, Any?>) },
                fontSize = ParseUtils.parseFontSize(json["fontSize"]),
                fontWeight = ParseUtils.parseFontWeight(json["fontWeight"]),
                textColor = ParseUtils.parseColor(json["textColor"]),
                textStyle = json["textStyle"]?.let { GravityTextStyle.fromJson(it as Map<String, Any?>) },
                fit = ParseUtils.parseBoxFit(json["fit"]),
                contentAlignment = json["contentAlignment"]?.let {
                    GravityContentAlignment.fromString(
                        it as String
                    )
                },
                verticalAlignment = json["verticalAlignment"]?.let {
                    GravityContentAlignment.fromString(
                        it as String
                    )
                },
                layoutWidth = json["layoutWidth"]?.let { GravityLayoutWidth.fromString(it as String) },
                positioned = json["positioned"]?.let { GravityPositioned.fromJson(it as Map<String, Any?>) },
                weight = (json["rows"] as? Number)?.toFloat() ?: 1f,
                productContainerType = ProductContainerType.fromString(json["productContainerType"] as? String),
                gridColumns = (json["gridColumns"] as? Number)?.toInt(),
                rowSpacing = (json["rowSpacing"] as? Number)?.toInt()
            )
        }
    }
}

data class GravitySize(
    val width: Double?,
    val height: Double?
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): GravitySize {
            return GravitySize(
                width = ParseUtils.parseDimension(json["width"]),
                height = ParseUtils.parseDimension(json["height"])
            )
        }
    }
}

data class GravityMargin(
    val left: Double,
    val right: Double,
    val top: Double,
    val bottom: Double
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): GravityMargin {
            return GravityMargin(
                left = ParseUtils.parseDimension(json["left"]) ?: 0.0,
                right = ParseUtils.parseDimension(json["right"]) ?: 0.0,
                top = ParseUtils.parseDimension(json["top"]) ?: 0.0,
                bottom = ParseUtils.parseDimension(json["bottom"]) ?: 0.0
            )
        }
    }
}

data class GravityPadding(
    val left: Double,
    val right: Double,
    val top: Double,
    val bottom: Double
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): GravityPadding {
            return GravityPadding(
                left = ParseUtils.parseDimension(json["left"]) ?: 0.0,
                right = ParseUtils.parseDimension(json["right"]) ?: 0.0,
                top = ParseUtils.parseDimension(json["top"]) ?: 0.0,
                bottom = ParseUtils.parseDimension(json["bottom"]) ?: 0.0
            )
        }
    }
}

data class GravityPositioned(
    val left: Double?,
    val right: Double?,
    val top: Double?,
    val bottom: Double?
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): GravityPositioned {
            return GravityPositioned(
                left = ParseUtils.parseDimension(json["left"]),
                right = ParseUtils.parseDimension(json["right"]),
                top = ParseUtils.parseDimension(json["top"]),
                bottom = ParseUtils.parseDimension(json["bottom"])
            )
        }
    }
}

data class GravityTextStyle(
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val color: Color
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): GravityTextStyle {
            return GravityTextStyle(
                fontSize = ParseUtils.parseFontSize(json["fontSize"]) ?: 0.sp,
                fontWeight = ParseUtils.parseFontWeight(json["fontWeight"]) ?: FontWeight.Normal,
                color = ParseUtils.parseColor(json["color"]) ?: Color(0xFF000000)
            )
        }
    }
}

enum class GravityContentAlignment {
    START,
    CENTER,
    END;

    companion object {
        fun fromString(value: String): GravityContentAlignment {
            return when (value) {
                "start" -> START
                "center" -> CENTER
                "end" -> END
                else -> START
            }
        }
    }

    fun toHorizontalAlignment(): Alignment.Horizontal {
        return when (this) {
            START -> Alignment.Start
            CENTER -> Alignment.CenterHorizontally
            END -> Alignment.End
        }
    }

    fun toVerticalArrangement(): Arrangement.Vertical {
        return when (this) {
            START -> Arrangement.Top
            CENTER -> Arrangement.Center
            END -> Arrangement.Bottom
        }
    }
}

enum class GravityLayoutWidth {
    MATCH_PARENT,
    WRAP_CONTENT;

    companion object {
        fun fromString(value: String): GravityLayoutWidth {
            return when (value) {
                "match_parent" -> MATCH_PARENT
                "wrap_content" -> WRAP_CONTENT
                else -> MATCH_PARENT
            }
        }
    }
}

enum class ProductContainerType {
    ROW,
    GRID;

    companion object {
        fun fromString(type: String?): ProductContainerType? {
            return when (type) {
                "row" -> ROW
                "grid" -> GRID
                else -> null
            }
        }
    }
}