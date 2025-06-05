package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.User

data class CampaignIdsResponse(
    val user: User,
    val campaigns: List<CampaignId>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CampaignIdsResponse {
            return CampaignIdsResponse(
                user = User.fromJson(json["user"] as Map<String, Any?>),
                campaigns = (json["campaigns"] as List<Map<String, Any?>>).map {
                    CampaignId.fromJson(
                        it
                    )
                }
            )
        }
    }
}

data class CampaignId(
    val campaignId: String,
    val trigger: String
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): CampaignId {
            return CampaignId(
                campaignId = json["campaignId"] as String,
                trigger = if (json["trigger"] != null) json["trigger"] as String else "",
            )
        }
    }
}
