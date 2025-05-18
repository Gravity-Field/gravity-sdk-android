package ai.gravityfield.gravity_sdk.models

data class CampaignVariation(
    val campaignId: String,
    val experienceId: String,
    val variationId: String,
    val decisionId: String,
    val contents: List<CampaignContent>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CampaignVariation {
            return CampaignVariation(
                campaignId = json["campaignId"] as String,
                experienceId = json["experienceId"] as String,
                variationId = json["variationId"] as String,
                decisionId = json["decisionId"] as String,
                contents = (json["contents"] as List<Map<String, Any?>>).map {
                    CampaignContent.fromJson(
                        it
                    )
                }
            )
        }
    }
}
