package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.withoutNulls

data class Variables(
    val frameUI: FrameUI?,
    val elements: List<Element>?,
    val onLoad: ContentActionModel?,
    val onImpression: ContentActionModel?,
    val onVisibleImpression: ContentActionModel?,
    val onClose: ContentActionModel?,
) {
    /**
     * Raw `variables` object exactly as it arrived from the server, including
     * keys that have no typed counterpart and null values. Empty for
     * [Variables] constructed by hand or via [copy] rather than parsed from JSON.
     * Kept out of the primary constructor so the public constructor, [copy],
     * equals and hashCode stay as they were.
     */
    var raw: Map<String, Any?> = emptyMap()
        private set

    internal constructor(
        frameUI: FrameUI?,
        elements: List<Element>?,
        onLoad: ContentActionModel?,
        onImpression: ContentActionModel?,
        onVisibleImpression: ContentActionModel?,
        onClose: ContentActionModel?,
        raw: Map<String, Any?>,
    ) : this(frameUI, elements, onLoad, onImpression, onVisibleImpression, onClose) {
        this.raw = raw
    }

    /** Reads an arbitrary `variables` key, e.g. `variables["my-param"]`. */
    operator fun get(key: String): Any? = raw[key]

    /** Reads an arbitrary `variables` key and returns it only if it is a [T]. */
    inline fun <reified T> valueOf(key: String): T? = raw[key] as? T

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Variables {
            // Typed fields are parsed as before, from a copy without the nulls kept for raw
            val typed = json.withoutNulls()
            return Variables(
                frameUI = if (typed["frameUI"] != null) FrameUI.fromJson(typed["frameUI"] as Map<String, Any?>) else null,
                elements = (typed["elements"] as? List<Map<String, Any?>>)?.map { Element.fromJson(it) },
                onLoad = if (typed["onLoad"] != null) ContentActionModel.fromJson(typed["onLoad"] as Map<String, Any?>) else null,
                onImpression = if (typed["onImpression"] != null) ContentActionModel.fromJson(typed["onImpression"] as Map<String, Any?>) else null,
                onVisibleImpression = if (typed["onVisibleImpression"] != null) ContentActionModel.fromJson(
                    typed["onVisibleImpression"] as Map<String, Any?>
                ) else null,
                onClose = if (typed["onClose"] != null) ContentActionModel.fromJson(typed["onClose"] as Map<String, Any?>) else null,
                raw = json,
            )
        }
    }
}
