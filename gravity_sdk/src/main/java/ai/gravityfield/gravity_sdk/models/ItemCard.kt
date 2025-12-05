package ai.gravityfield.gravity_sdk.models

class ItemCard(
    val template: Template,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): ItemCard {
            return ItemCard(
                template = Template.fromJson(json["template"] as Map<String, Any?>),
            )
        }
    }
}

class Template(
    val frameUI: FrameUI?,
    val elements: List<Element>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Template {
            return Template(
                frameUI = if (json["frameUI"] != null) FrameUI.fromJson(json["frameUI"] as Map<String, Any?>) else null,
                elements = (json["elements"] as List<Map<String, Any?>>).map { Element.fromJson(it) },
            )
        }
    }
}
