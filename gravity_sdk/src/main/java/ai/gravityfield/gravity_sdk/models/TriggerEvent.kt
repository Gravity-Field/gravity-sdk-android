package ai.gravityfield.gravity_sdk.models

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
class PurchaseEvent(
    val uniqueTransactionId: String,
    val value: Double,
    val currency: String?,
    val cart: List<CartItem>?,
) : TriggerEvent() {
    override val type: String = "purchase-v1"
    override val name: String = "Purchase"
}

@Serializable
class RemoveFromCartEvent(
    val value: Double,
    val productId: String,
    val quantity: Int,
    val currency: String?,
    val cart: List<CartItem>?,
) : TriggerEvent() {
    override val type: String = "remove-from-cart-v1"
    override val name: String = "Remove from Cart"
}

@Serializable
class SyncCartEvent(
    val currency: String?,
    val cart: List<CartItem>?,
) : TriggerEvent() {
    override val type: String = "sync-cart-v1"
    override val name: String = "Sync cart"
}

@Serializable
class AddToWishlistEvent(
    val productId: String,
) : TriggerEvent() {
    override val type: String = "add-to-wishlist-v1"
    override val name: String = "Add to Wishlist"
}

@Serializable
class SingUpEvent(
    val hashedEmail: String,
    val cuid: String,
    val cuidType: String
) : TriggerEvent() {
    override val type: String = "signup-v1"
    override val name: String = "Signup"
}

@Serializable
class LoginEvent(
    val hashedEmail: String? = null,
    val cuid: String? = null,
    val cuidType: String? = null
) : TriggerEvent() {
    override val type: String = "login-v1"
    override val name: String = "Login"
}

@Serializable
class CustomEvent(
    override val type: String,
    override val name: String,
    val properties: Map<String, String>? = null
) : TriggerEvent()

@Serializable
data class CartItem(val productId: String, val quantity: Int, val itemPrice: Double)
