package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.Payload
import ai.gravityfield.gravity_sdk.models.User

class ContentResponse(
    val user: User,
    val data: List<DataItem>
) {
    companion object {
        fun fromJson(json: Map<String, Any>): ContentResponse {
            return ContentResponse(
                user = User.fromJson(json["user"] as Map<String, Any>),
                data = (json["data"] as List<Map<String, Any>>).map { DataItem.fromJson(it) }
            )
        }
    }
}

class DataItem(
    val selector: String,
    val payload: List<Payload>
) {
    companion object {
        fun fromJson(json: Map<String, Any>): DataItem {
            return DataItem(
                selector = json["selector"] as String,
                payload = (json["payload"] as List<Map<String, Any>>).map { Payload.fromJson(it) }
            )
        }
    }
}
