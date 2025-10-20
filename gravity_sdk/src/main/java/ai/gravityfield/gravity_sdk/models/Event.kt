package ai.gravityfield.gravity_sdk.models

enum class Action {
    LOAD,
    IMPRESSION,
    VISIBLE_IMPRESSION,
    CLOSE,
    COPY,
    CANCEL,
    FOLLOW_URL,
    FOLLOW_DEEPLINK,
    REQUEST_PUSH,
    REQUEST_TRACKING,
    UNKNOWN;

    companion object {
        fun fromString(type: String): Action {
            return when (type) {
                "load" -> LOAD
                "impression" -> IMPRESSION
                "visible_impression" -> VISIBLE_IMPRESSION
                "close" -> CLOSE
                "copy" -> COPY
                "cancel" -> CANCEL
                "follow_url" -> FOLLOW_URL
                "follow_deeplink" -> FOLLOW_DEEPLINK
                "request_push" -> REQUEST_PUSH
                "request_tracking" -> REQUEST_TRACKING
                else -> UNKNOWN
            }
        }
    }
}

data class Event(
    val type: Action,
    val urls: List<String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Event {
            return Event(
                type = Action.fromString(json["type"] as String),
                urls = json["urls"] as List<String>
            )
        }
    }
}

enum class ProductAction {
    VISIBLE_IMPRESSION,
    CLICK,
    UNKNOWN;

    companion object {
        fun fromString(type: String): ProductAction {
            return when (type) {
                "visible_impression" -> VISIBLE_IMPRESSION
                "click" -> CLICK
                else -> UNKNOWN
            }
        }
    }
}

data class ProductEvent(
    val type: ProductAction,
    val urls: List<String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): ProductEvent {
            return ProductEvent(
                type = ProductAction.fromString(json["type"] as String),
                urls = json["urls"] as List<String>
            )
        }
    }
}
