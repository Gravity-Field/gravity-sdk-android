package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable

@Serializable
enum class ContextType {
    HOMEPAGE,
    PRODUCT,
    CART,
    CATEGORY,
    SEARCH,
    OTHER;
}

@Serializable
data class PageContext(
    val type: ContextType,
    val data: List<String>,
    val location: String,
    val lng: String? = null,
    val utm: Map<String, String>? = null,
    val attributes: Map<String, String> = emptyMap(),
)
