package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.GravitySDK

data class Products(
    val strategyId: String,
    val name: String,
    val fallback: Boolean,
    val slots: List<Slot>,
    val pageNumber: Int?,
    val countPages: Int?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Products {
            var slots = (json["slots"] as List<Map<String, Any?>>).map { Slot.fromJson(it) }
            if (GravitySDK.instance.productFilter != null) {
                slots = slots.filter(GravitySDK.instance.productFilter!!)
            }
            return Products(
                strategyId = json["strategyId"] as String,
                name = json["name"] as String,
                pageNumber = (json["pageNumber"] as Number?)?.toInt(),
                countPages = (json["countPages"] as Number?)?.toInt(),
                fallback = json["fallback"] as Boolean,
                slots = slots
            )
        }
    }
}

data class Slot(
    val item: Item,
    val fallback: Boolean,
    val strId: Int,
    val slotId: String,
    val events: List<ProductEvent>?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Slot {
            return Slot(
                item = Item.fromJson(json["item"] as Map<String, Any?>),
                fallback = json["fallback"] as Boolean,
                strId = (json["strId"] as Number).toInt(),
                slotId = json["slotId"] as String,
                events = (json["events"] as List<Map<String, Any?>>?)?.map {
                    ProductEvent.fromJson(
                        it
                    )
                }
            )
        }
    }
}

data class Item(
    val sku: String,
    val groupId: String,
    val name: String,
    val price: String,
    val url: String,
    val imageUrl: String,
    val isNew: String,
    val oldPrice: String,
    val bitrixId: String,
    val categories: List<String>,
    val keywords: List<String>,
    val brand: String,
    val inStock: Boolean
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Item {
            return Item(
                sku = json["sku"] as String,
                groupId = json["group_id"] as String,
                name = json["name"] as String,
                price = json["price"] as String,
                url = json["url"] as String,
                imageUrl = json["image_url"] as String,
                isNew = json["is_new"] as String,
                oldPrice = json["old_price"] as String,
                bitrixId = json["bitrix_id"] as String,
                categories = json["categories"] as List<String>,
                keywords = json["keywords"] as List<String>,
                brand = json["brand"] as String,
                inStock = json["in_stock"] as Boolean
            )
        }
    }
}
