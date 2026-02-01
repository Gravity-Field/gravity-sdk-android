package ai.gravityfield.gravity_sdk.models

data class CustomModel(
    val json: String?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CustomModel {
            return CustomModel(
                json = json["json"] as? String,
            )
        }
    }
}
