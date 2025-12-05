package ai.gravityfield.gravity_sdk.models

data class Element(
    val type: ElementType,
    val text: String?,
    val src: String?,
    val onClick: OnClickModel?,
    val itemCard: ItemCard?,
    val style: Style,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Element {
            return Element(
                type = ElementType.fromString(json["type"] as String),
                text = json["text"] as? String,
                src = json["src"] as? String,
                onClick = if (json["onClick"] != null) OnClickModel.fromJson(json["onClick"] as Map<String, Any?>) else null,
                itemCard = if (json["itemCard"] != null) ItemCard.fromJson(json["itemCard"] as Map<String, Any?>) else null,
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
    ITEMS_CONTAINER,
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
                "itemsContainer", "items-container", "items_container" -> ITEMS_CONTAINER
                else -> UNKNOWN
            }
        }
    }
}

data class OnClickModel(
    val action: Action,
    val copyData: String?,
    val step: Int?,
    val itemId: String?,
    val url: String?,
    val type: FollowUrlType?,
    val deeplink: String?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): OnClickModel {
            return OnClickModel(
                action = Action.fromString(json["action"] as String),
                copyData = json["copyData"] as? String,
                step = (json["step"] as Number?)?.toInt(),
                itemId = json["itemId"] as? String,
                url = json["url"] as? String,
                type = FollowUrlType.fromString(json["type"] as String?),
                deeplink = json["deeplink"] as? String
            )
        }
    }
}

enum class FollowUrlType {
    BROWSER,
    WEBVIEW;

    companion object {
        fun fromString(value: String?): FollowUrlType? {
            return when (value) {
                "browser" -> BROWSER
                "webview" -> WEBVIEW
                else -> null
            }
        }
    }
}
