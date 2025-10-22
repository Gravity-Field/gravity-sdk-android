package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.GravitySDK

data class Products(
    val strategyId: String,
    val name: String,
    val fallback: Boolean,
    val slots: List<Slot>?,
    val pageNumber: Int?,
    val countPages: Int?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Products {
            var slots = (json["slots"] as List<Map<String, Any?>>?)?.map { Slot.fromJson(it) }
            if (GravitySDK.instance.productFilter != null) {
                slots = slots?.filter(GravitySDK.instance.productFilter!!)
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
    val item: Map<String, Any?>,
    val fallback: Boolean,
    val strId: Int,
    val slotId: String,
    val events: List<ProductEvent>?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): Slot {
            return Slot(
                item = json["item"] as Map<String, Any?>,
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
