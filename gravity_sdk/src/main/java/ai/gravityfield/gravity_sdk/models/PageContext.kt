package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.SerialName
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
    val data: List<String>? = null,
    val location: String? = null,
    val lng: String? = null,
    val utm: Map<String, String>? = null,
    val attributes: Map<String, String>? = null,
)
