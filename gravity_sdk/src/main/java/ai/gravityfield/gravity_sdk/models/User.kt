package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class User(
    val uid: String? = null,
    val custom: String? = null,
    val ses: String? = null,
    val attributes: Map<String, JsonElement>? = null
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): User {
            return User(
                uid = json["uid"] as String?,
                ses = json["ses"] as String?
            )
        }
    }
}
