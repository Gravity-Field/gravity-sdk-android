package ai.gravityfield.gravity_sdk.models

data class Variables(
    val frameUI: FrameUI,
    val elements: List<Element>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Variables {
            return Variables(
                frameUI = FrameUI.fromJson(json["frameUI"] as Map<String, Any?>),
                elements = (json["elements"] as List<Map<String, Any?>>).map { Element.fromJson(it) }
            )
        }
    }
}
