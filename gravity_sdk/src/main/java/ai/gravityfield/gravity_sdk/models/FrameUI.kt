package ai.gravityfield.gravity_sdk.models

data class FrameUI(
    val container: Container,
    val close: Close?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): FrameUI {
            return FrameUI(
                container = Container.fromJson(json["container"] as Map<String, Any?>),
                close = json["close"]?.let { Close.fromJson(it as Map<String, Any?>) }
            )
        }
    }
}
