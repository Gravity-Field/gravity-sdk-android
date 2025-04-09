package ai.gravityfield.gravity_sdk.models

data class Payload(
    val campaignId: String,
    val experienceId: String,
    val variationId: String,
    val decisionId: String,
    val contents: List<Content>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Payload {
            return Payload(
                campaignId = json["campaignId"] as String,
                experienceId = json["experienceId"] as String,
                variationId = json["variationId"] as String,
                decisionId = json["decisionId"] as String,
                contents = (json["contents"] as List<Map<String, Any?>>).map { Content.fromJson(it) }
            )
        }
    }
}
