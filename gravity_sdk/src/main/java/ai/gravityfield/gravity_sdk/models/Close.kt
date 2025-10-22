package ai.gravityfield.gravity_sdk.models

data class Close(
    val style: Style?,
    val image: String?,
    val onClick: OnClickModel?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Close {
            return Close(
                style = if (json["style"] != null) Style.fromJson(json["style"] as Map<String, Any?>) else null,
                image = json["image"] as? String,
                onClick = if (json["onClick"] != null) OnClickModel.fromJson(json["onClick"] as Map<String, Any?>) else null,
            )
        }
    }
}
