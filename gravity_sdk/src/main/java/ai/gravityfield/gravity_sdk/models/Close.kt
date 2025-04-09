package ai.gravityfield.gravity_sdk.models

data class Close(
    val image: String?,
    val style: Style
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Close {
            return Close(
                image = json["image"] as? String,
                style = Style.fromJson(json["style"] as Map<String, Any?>)
            )
        }
    }
}
