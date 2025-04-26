package ai.gravityfield.gravity_sdk.models

data class Variables(
    val frameUI: FrameUI?,
    val elements: List<Element>,
    val onLoad: ContentActionModel,
    val onImpression: ContentActionModel,
    val onVisibleImpression: ContentActionModel
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Variables {
            return Variables(
                frameUI = if (json["frameUI"] != null) FrameUI.fromJson(json["frameUI"] as Map<String, Any?>) else null,
                elements = (json["elements"] as List<Map<String, Any?>>).map { Element.fromJson(it) },
                onLoad = ContentActionModel.fromJson(json["onLoad"] as Map<String, Any?>),
                onImpression = ContentActionModel.fromJson(json["onImpression"] as Map<String, Any?>),
                onVisibleImpression = ContentActionModel.fromJson(json["onVisibleImpression"] as Map<String, Any?>),
            )
        }
    }
}
