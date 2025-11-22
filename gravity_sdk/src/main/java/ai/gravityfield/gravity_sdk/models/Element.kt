package ai.gravityfield.gravity_sdk.models

data class Element(
    val type: ElementType,
    val text: String?,
    val src: String?,
    val onClick: OnClickModel?,
    val style: Style
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Element {
            return Element(
                type = ElementType.fromString(json["type"] as String),
                text = json["text"] as? String,
                src = json["src"] as? String,
                onClick = if (json["onClick"] != null) OnClickModel.fromJson(json["onClick"] as Map<String, Any?>) else null,
                style = if (json["style"] != null) Style.fromJson(json["style"] as Map<String, Any?>) else Style.empty
            )
        }
    }
}

enum class ElementType {
    IMAGE,
    TEXT,
    WEBVIEW,
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
                "webview" -> WEBVIEW
                "spacer" -> SPACER
                "productsContainer", "products-container", "products_container" -> PRODUCTS_CONTAINER
                else -> UNKNOWN
            }
        }
    }
}

data class OnClickModel(
    val action: Action,
    val copyData: String?,
    val step: Int?,
    val url: String?,
    val deeplink: String?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): OnClickModel {
            return OnClickModel(
                action = Action.fromString(json["action"] as String),
                copyData = json["copyData"] as? String,
                step = json["step"] as? Int,
                url = json["url"] as? String,
                deeplink = json["deeplink"] as? String
            )
        }
    }
}
