package ai.gravityfield.gravity_sdk.models.internal

import ai.gravityfield.gravity_sdk.network.ContentResponse

internal data class InlineViewCache(
    val content: ContentResponse? = null,
    val scrollPosition: ScrollPosition? = null,
)

internal data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)
