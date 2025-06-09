package ai.gravityfield.gravity_sdk.models

data class Container(
    val style: Style,
    val onClick: OnClickModel?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Container {
            return Container(
                style = Style.fromJson(json["style"] as Map<String, Any?>),
                onClick = if (json["onClick"] != null) OnClickModel.fromJson(json["onClick"] as Map<String, Any?>) else null,
            )
        }
    }
}
