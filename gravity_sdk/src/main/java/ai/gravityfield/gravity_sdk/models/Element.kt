package ai.gravityfield.gravity_sdk.models

enum class ElementType {
    IMAGE,
    TEXT,
    BUTTON,
    SPACER,
    PRODUCTS_CONTAINER,
    UNKNOWN;

    companion object {
        fun fromString(type: String): ElementType {
            return when (type) {
                "image" -> IMAGE
                "text" -> TEXT
                "button" -> BUTTON
                "spacer" -> SPACER
                "productsContainer", "products-container" -> PRODUCTS_CONTAINER
                else -> UNKNOWN
            }
        }
    }
}

data class Element(
    val type: ElementType,
    val text: String?,
    val src: String?,
    val style: Style
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Element {
            return Element(
                type = ElementType.fromString(json["type"] as String),
                text = json["text"] as? String,
                src = json["src"] as? String,
                style = Style.fromJson(json["style"] as Map<String, Any?>)
            )
        }
    }
}
