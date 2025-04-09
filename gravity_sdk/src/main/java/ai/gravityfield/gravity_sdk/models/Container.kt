package ai.gravityfield.gravity_sdk.models

data class Container(
    val style: Style
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Container {
            return Container(
                style = Style.fromJson(json["style"] as Map<String, Any?>)
            )
        }
    }
}
