package ai.gravityfield.gravity_sdk.models

data class Event(
    val type: String,
    val urls: List<String>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Event {
            return Event(
                type = json["type"] as String,
                urls = json["urls"] as List<String>
            )
        }
    }
}
