package ai.gravityfield.gravity_sdk.models

data class CampaignContent(
    val contentId: String,
    val templateSystemName: TemplateSystemName?,
    val deliveryMethod: DeliveryMethod,
    val contentType: String,
    val step: Int?,
    val variables: Variables,
    val products: Products?,
    val items: List<Item>?,
    val events: List<Event>?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): CampaignContent {
            return CampaignContent(
                contentId = json["contentId"] as String,
                templateSystemName = if (json["templateSystemName"] != null) TemplateSystemName.fromString(
                    json["templateSystemName"] as String
                ) else null,
                deliveryMethod = DeliveryMethod.fromString(json["deliveryMethod"] as String?),
                contentType = json["contentType"] as String,
                step = (json["step"] as Number?)?.toInt(),
                variables = Variables.fromJson(json["variables"] as Map<String, Any?>),
                products = if (json["products"] != null) Products.fromJson(json["products"] as Map<String, Any?>) else null,
                items = if (json["items"] != null) (json["items"] as List<Map<String, Any?>>).map {
                    Item.fromJson(
                        it
                    )
                } else null,
                events = if (json["events"] != null) (json["events"] as List<Map<String, Any?>>).map {
                    Event.fromJson(
                        it
                    )
                } else null
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
