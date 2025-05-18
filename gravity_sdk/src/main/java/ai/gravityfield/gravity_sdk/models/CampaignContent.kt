package ai.gravityfield.gravity_sdk.models

data class CampaignContent(
    val contentId: String,
    val templateId: String,
    val deliveryMethod: DeliveryMethod,
    val contentType: String,
    val variables: Variables,
    val products: Products?,
    val events: List<Event>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CampaignContent {
            return CampaignContent(
                contentId = json["contentId"] as String,
                templateId = json["templateId"] as String,
                deliveryMethod = DeliveryMethod.fromString(json["deliveryMethod"] as String?),
                contentType = json["contentType"] as String,
                variables = Variables.fromJson(json["variables"] as Map<String, Any?>),
                products = if (json["products"] != null) Products.fromJson(json["products"] as Map<String, Any?>) else null,
                events = (json["events"] as List<Map<String, Any?>>).map { Event.fromJson(it) }
            )
        }
    }
}

data class ContentActionModel(val action: Action) {
    companion object {
        fun fromJson(json: Map<String, Any?>): ContentActionModel {
            return ContentActionModel(
                action = Action.fromString(json["action"] as String)
            )
        }
    }
}
