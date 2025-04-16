package ai.gravityfield.gravity_sdk.models

data class Event(
    val name: String,
    val urls: List<String>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Event {
            return Event(
                name = json["name"] as String,
                urls = json["urls"] as List<String>
            )
        }
    }
}
