package ai.gravityfield.gravity_sdk.models

data class User(
    val uid: String,
    val ses: String
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): User {
            return User(
                uid = json["uid"] as String,
                ses = json["ses"] as String
            )
        }
    }

    fun toJson(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "ses" to ses
        )
    }
}
