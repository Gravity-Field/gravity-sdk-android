package ai.gravityfield.gravity_sdk.models.assistant

/** Response of `POST /shopping/generate`. Product fields arrive in snake_case. */
internal data class ShoppingGenerateResponse(
    val threadId: String?,
    val resourceId: String?,
    val assistantMessageId: String?,
    val contents: List<ShoppingContent>,
    val suggests: List<String>,
    val events: List<ShoppingEngagementEvent>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): ShoppingGenerateResponse {
            val response = json["response"] as? Map<String, Any?>
            val ui = response?.get("ui") as? Map<String, Any?>

            return ShoppingGenerateResponse(
                threadId = json["threadId"] as? String,
                resourceId = json["resourceId"] as? String,
                assistantMessageId = json["assistantMessageId"] as? String,
                contents = (response?.get("contents") as? List<Map<String, Any?>>)
                    ?.mapNotNull { ShoppingContent.fromJson(it) }
                    .orEmpty(),
                suggests = (ui?.get("suggests") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty(),
                events = parseEvents(json["events"]),
            )
        }
    }
}

internal sealed interface ShoppingContent {

    data class Message(val value: String) : ShoppingContent

    data class Products(
        val title: String?,
        val shelfId: String?,
        val products: List<ShoppingProduct>,
    ) : ShoppingContent

    data class Button(
        val value: String,
        val action: String,
        val url: String?,
    ) : ShoppingContent

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): ShoppingContent? {
            return when (json["type"] as? String) {
                "message" -> Message(json["value"] as? String ?: return null)

                "products" -> Products(
                    title = json["title"] as? String,
                    shelfId = json["shelf_id"] as? String,
                    products = (json["value"] as? List<Map<String, Any?>>)
                        ?.mapNotNull { ShoppingProduct.fromJson(it) }
                        .orEmpty(),
                )

                "button" -> Button(
                    value = json["value"] as? String ?: return null,
                    action = json["action"] as? String ?: return null,
                    url = json["url"] as? String,
                )

                else -> null
            }
        }
    }
}

internal data class ShoppingProduct(
    val name: String,
    val sku: String?,
    val categoryId: String?,
    val price: Double?,
    val features: String?,
    val isStm: Boolean?,
    val brand: String?,
    val url: String?,
    val imageUrl: String?,
    val events: List<ShoppingEngagementEvent>,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): ShoppingProduct? {
            val name = json["name"] as? String ?: return null

            return ShoppingProduct(
                name = name,
                sku = json["sku"] as? String,
                categoryId = json["category_id"] as? String,
                price = (json["price"] as? Number)?.toDouble(),
                features = json["features"] as? String,
                isStm = json["is_stm"] as? Boolean,
                brand = json["brand"] as? String,
                url = json["url"] as? String,
                imageUrl = json["image_url"] as? String,
                events = parseEvents(json["events"]),
            )
        }
    }
}

internal data class ShoppingEngagementEvent(
    val type: String,
    val urls: List<String>,
)

/** Each event carries either a `urls` array or a single `url`. */
@Suppress("UNCHECKED_CAST")
private fun parseEvents(raw: Any?): List<ShoppingEngagementEvent> {
    val events = raw as? List<Map<String, Any?>> ?: return emptyList()

    return events.mapNotNull { event ->
        val type = event["type"] as? String ?: return@mapNotNull null
        val urls = (event["urls"] as? List<*>)?.mapNotNull { it as? String }
            ?: listOfNotNull(event["url"] as? String)

        if (urls.isEmpty()) null else ShoppingEngagementEvent(type, urls)
    }
}
