package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class TriggerEventJsonTest {
    // Same settings as GravityRepository
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val props = mapOf("list" to "search", "payment_type" to "card")

    // 2026-09-03 09:00:15.250 UTC
    private val time: Date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.SEPTEMBER, 3, 9, 0, 15)
        set(Calendar.MILLISECOND, 250)
    }.time

    private fun wire(events: List<TriggerEvent>): List<JsonObject> {
        return (json.encodeToJsonElement(events) as JsonArray).map { it.jsonObject }
    }

    private fun typedEvents(customProps: Map<String, String>? = null, eventTime: Date? = null) = listOf(
        AddToCartEvent(value = 1.0, productId = "p", quantity = 1, customProps = customProps, eventTime = eventTime),
        RemoveFromCartEvent(value = 1.0, productId = "p", quantity = 1, customProps = customProps, eventTime = eventTime),
        SyncCartEvent(value = 1.0, customProps = customProps, eventTime = eventTime),
        PurchaseEvent(uniqueTransactionId = "t", value = 1.0, cart = emptyList(), customProps = customProps, eventTime = eventTime),
        AddToWishlistEvent(value = 1.0, productId = "p", customProps = customProps, eventTime = eventTime),
        SignUpEvent(cuid = "c", customProps = customProps, eventTime = eventTime),
        LoginEvent(cuid = "c", customProps = customProps, eventTime = eventTime),
        CustomEvent(type = "x", name = "X", customProps = customProps, eventTime = eventTime),
    )

    @Test
    fun everyEventSendsCustomPropsAndEventTimeWhenSet() {
        val events = typedEvents(customProps = props, eventTime = time)
        wire(events).zip(events).forEach { (obj, event) ->
            assertEquals(event.type, json.encodeToJsonElement(props), obj["customProps"])
            assertEquals(event.type, JsonPrimitive("2026-09-03T09:00:15.250Z"), obj["eventTime"])
            assertEquals(JsonPrimitive(event.type), obj["type"])
        }
    }

    @Test
    fun unsetCustomPropsAndEventTimeAreOmitted() {
        val events = typedEvents()
        wire(events).zip(events).forEach { (obj, event) ->
            assertFalse(event.type, obj.containsKey("customProps"))
            assertFalse(event.type, obj.containsKey("eventTime"))
        }
    }

    @Test
    fun typedFieldsStillSerializeNextToCustomProps() {
        val obj = wire(
            listOf(AddToCartEvent(value = 99.5, productId = "sku-1", quantity = 2, currency = "RUB", customProps = props))
        ).single()
        val expected = buildJsonObject {
            put("value", 99.5)
            put("productId", "sku-1")
            put("quantity", 2)
            put("currency", "RUB")
            put("customProps", json.encodeToJsonElement(props))
            put("type", "add-to-cart-v1")
            put("name", "Add to Cart")
        }
        assertEquals(expected, obj)
    }

    @Test
    fun customEventCarriesCuidCuidTypeCartAndEventTime() {
        val obj = wire(
            listOf(
                CustomEvent(
                    type = "loyalty-v1",
                    name = "Loyalty",
                    cuid = "user@example.com",
                    cuidType = "email",
                    cart = listOf(CartItem(productId = "sku-1", quantity = 2, itemPrice = 10.5)),
                    customProps = props,
                    eventTime = time,
                )
            )
        ).single()
        val expected = buildJsonObject {
            put("type", "loyalty-v1")
            put("name", "Loyalty")
            put("customProps", json.encodeToJsonElement(props))
            put("cuid", "user@example.com")
            put("cuidType", "email")
            put("cart", json.encodeToJsonElement(listOf(CartItem("sku-1", 2, 10.5))))
            put("eventTime", "2026-09-03T09:00:15.250Z")
        }
        assertEquals(expected, obj)
    }

    @Test
    fun unsetOptionalFieldsAreOmittedNotSentAsNull() {
        val events = listOf(
            AddToCartEvent(value = 1.0, productId = "p", quantity = 1),
            RemoveFromCartEvent(value = 1.0, productId = "p", quantity = 1),
            SyncCartEvent(value = 1.0),
            PurchaseEvent(uniqueTransactionId = "t", value = 1.0, cart = emptyList()),
            AddToWishlistEvent(value = 1.0, productId = "p"),
            SignUpEvent(),
            LoginEvent(),
            CustomEvent(type = "x", name = "X"),
        )
        wire(events).zip(events).forEach { (obj, event) ->
            assertFalse(event.type, obj.values.any { it is JsonNull })
        }
    }

    @Test
    fun emptyCustomPropsIsSentAsEmptyObject() {
        val obj = wire(listOf(CustomEvent(type = "x", name = "X", customProps = emptyMap()))).single()
        assertEquals(JsonObject(emptyMap()), obj["customProps"])
    }

    @Test
    fun eventTimeIsSentInUtcWhateverTheDeviceTimeZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Novosibirsk"))
            // 16:00:15.250 in Novosibirsk (UTC+7) is 09:00:15.250 UTC
            val local = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.SEPTEMBER, 3, 16, 0, 15)
                set(Calendar.MILLISECOND, 250)
            }.time
            val obj = wire(listOf(CustomEvent(type = "x", name = "X", eventTime = local))).single()
            assertEquals(JsonPrimitive("2026-09-03T09:00:15.250Z"), obj["eventTime"])
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun eventsWithoutEventTimeGetTheRaisedAtTime() {
        val explicit = CustomEvent(type = "a", name = "A", eventTime = time)
        val implicit = CustomEvent(type = "b", name = "B")
        val raisedAt = Date(time.time + 60_000)

        val data = encodeEvents(json, listOf(explicit, implicit), raisedAt).map { it.jsonObject }

        assertEquals(JsonPrimitive("2026-09-03T09:00:15.250Z"), data[0]["eventTime"])
        assertEquals(JsonPrimitive("2026-09-03T09:01:15.250Z"), data[1]["eventTime"])
    }

    @Test
    fun customEventWithoutNewFieldsIsUnchangedOnTheWire() {
        val obj = wire(listOf(CustomEvent(type = "x", name = "X"))).single()
        assertEquals(buildJsonObject { put("type", "x"); put("name", "X") }, obj)
    }
}
