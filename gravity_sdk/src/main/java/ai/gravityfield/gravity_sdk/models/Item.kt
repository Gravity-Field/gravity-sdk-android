package ai.gravityfield.gravity_sdk.models

data class Item(
    val id: String,
    val values: Map<String, String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Item {
            return Item(
                id = json["id"] as String,
                values = json["values"] as? Map<String, String> ?: emptyMap()
            )
        }
    }
}
