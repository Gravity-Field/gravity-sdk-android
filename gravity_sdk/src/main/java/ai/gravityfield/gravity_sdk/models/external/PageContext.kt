package ai.gravityfield.gravity_sdk.models.external

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContextType {
    @SerialName("homepage")
    HOMEPAGE,

    @SerialName("product")
    PRODUCT,

    @SerialName("cart")
    CART,

    @SerialName("category")
    CATEGORY,

    @SerialName("search")
    SEARCH,

    @SerialName("other")
    OTHER;
}

@Serializable
data class PageContext(val type: ContextType)
