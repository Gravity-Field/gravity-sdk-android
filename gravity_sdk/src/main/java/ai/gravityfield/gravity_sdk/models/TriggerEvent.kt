@file:OptIn(ExperimentalSerializationApi::class)

package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
sealed class TriggerEvent {
    abstract val type: String
    abstract val name: String

    /** Extra properties sent alongside the typed fields. String values only. */
    abstract val customProps: Map<String, String>?

    /** When the event happened; sent in UTC. Omitted, the time triggerEvent was called is sent. */
    abstract val eventTime: Date?
}

@Serializable
class AddToCartEvent @JvmOverloads constructor(
    val value: Double,
    val productId: String,
    val quantity: Int,
    val currency: String? = null,
    val cart: List<CartItem>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "add-to-cart-v1"
    override val name: String = "Add to Cart"
}

@Serializable
class PurchaseEvent @JvmOverloads constructor(
    val uniqueTransactionId: String,
    val value: Double,
    val cart: List<CartItem>,
    val currency: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "purchase-v1"
    override val name: String = "Purchase"
}

@Serializable
class RemoveFromCartEvent @JvmOverloads constructor(
    val value: Double,
    val productId: String,
    val quantity: Int,
    val currency: String? = null,
    val cart: List<CartItem>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "remove-from-cart-v1"
    override val name: String = "Remove from Cart"
}

@Serializable
class SyncCartEvent @JvmOverloads constructor(
    val value: Double,
    val currency: String? = null,
    val cart: List<CartItem>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "sync-cart-v1"
    override val name: String = "Sync cart"
}

@Serializable
class AddToWishlistEvent @JvmOverloads constructor(
    val value: Double,
    val productId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "add-to-wishlist-v1"
    override val name: String = "Add to Wishlist"
}

@Serializable
class SignUpEvent @JvmOverloads constructor(
    val hashedEmail: String? = null,
    val cuid: String? = null,
    val cuidType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "signup-v1"
    override val name: String = "Signup"
}

@Serializable
class LoginEvent @JvmOverloads constructor(
    val hashedEmail: String? = null,
    val cuid: String? = null,
    val cuidType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent() {
    override val type: String = "login-v1"
    override val name: String = "Login"
}

@Serializable
class CustomEvent @JvmOverloads constructor(
    override val type: String,
    override val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val customProps: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cuid: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cuidType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cart: List<CartItem>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = EventTimeSerializer::class)
    override val eventTime: Date? = null,
) : TriggerEvent()

@Serializable
data class CartItem(
    val productId: String,
    val quantity: Int,
    val itemPrice: Double,
)

/**
 * Encodes [events] for the /event body. Events without their own eventTime get
 * [raisedAt], the moment the app raised them; an explicit eventTime is kept.
 */
internal fun encodeEvents(json: Json, events: List<TriggerEvent>, raisedAt: Date): JsonArray {
    val fallback = json.encodeToJsonElement(EventTimeSerializer, raisedAt)
    return JsonArray(events.map { event ->
        val obj = json.encodeToJsonElement(TriggerEvent.serializer(), event).jsonObject
        if (obj.containsKey("eventTime")) obj else JsonObject(obj + ("eventTime" to fallback))
    })
}

/** RFC 3339 timestamp in UTC with millisecond precision, e.g. `2026-08-28T11:00:00.123Z`. */
internal object EventTimeSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EventTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeString(dateFormat().format(value))
    }

    override fun deserialize(decoder: Decoder): Date {
        return dateFormat().parse(decoder.decodeString())!!
    }

    // SimpleDateFormat is not thread-safe, so a new one per call
    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
