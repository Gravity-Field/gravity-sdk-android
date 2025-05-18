package ai.gravityfield.gravity_sdk.models.external

import kotlinx.serialization.Serializable

@Serializable
sealed class TriggerEvent {
    abstract val type: String
    abstract val name: String
}

@Serializable
class AddToCartEvent(
    val value: Double,
    val productId: String,
    val quantity: Int,
    val currency: String?,
    val cart: List<CartItem>?,
) : TriggerEvent() {
    override val type: String = "add-to-cart-v1"
    override val name: String = "Add to Cart"
}

@Serializable
class CustomEvent(
    override val type: String,
    override val name: String
) : TriggerEvent()

@Serializable
data class CartItem(val productId: String, val quantity: Int, val itemPrice: Double)
