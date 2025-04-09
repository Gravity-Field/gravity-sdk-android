package ai.gravityfield.gravity_sdk.models

data class Content(
    val contentId: String,
    val templateId: String,
    val deliveryMethod: DeliveryMethod,
    val contentType: String,
    val variables: Variables,
    val events: List<Event>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Content {
            return Content(
                contentId = json["contentId"] as String,
                templateId = json["templateId"] as String,
                deliveryMethod = DeliveryMethod.fromString(json["deliveryMethod"] as String?),
                contentType = json["contentType"] as String,
                variables = Variables.fromJson(json["variables"] as Map<String, Any?>),
                events = (json["events"] as List<Map<String, Any?>>).map { Event.fromJson(it) }
            )
        }
    }
}
