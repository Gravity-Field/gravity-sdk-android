package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.CampaignVariation
import ai.gravityfield.gravity_sdk.models.User

data class ContentResponse(
    val user: User,
    val data: List<Campaign>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): ContentResponse {
            return ContentResponse(
                user = User.fromJson(json["user"] as Map<String, Any?>),
                data = (json["data"] as List<Map<String, Any?>>?)?.map { Campaign.fromJson(it) }
                    ?: emptyList()
            )
        }
    }
}

data class Campaign(
    val selector: String,
    val payload: List<CampaignVariation>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Campaign {
            return Campaign(
                selector = json["selector"] as String,
                payload = (json["payload"] as List<Map<String, Any?>>).map {
                    CampaignVariation.fromJson(it)
                }
            )
        }
    }
}
