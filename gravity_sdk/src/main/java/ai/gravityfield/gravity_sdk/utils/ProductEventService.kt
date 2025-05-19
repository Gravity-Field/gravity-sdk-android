package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.ProductAction
import ai.gravityfield.gravity_sdk.models.ProductEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.GravityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ProductEventService private constructor() {

    companion object {
        private var _instance = ProductEventService()

        val instance: ProductEventService
            get() = _instance
    }

    fun sendProductClick(slot: Slot) {
        trackEvent(ProductAction.PCLICK, slot.events)
    }

    fun sendProductVisibleImpression(slot: Slot) {
        trackEvent(ProductAction.PIMP, slot.events)
    }

    private fun trackEvent(action: ProductAction, events: List<ProductEvent>) {
        events.find { it.name == action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                GravityRepository.instance.trackEngagementEvent(event.urls)
            }
        }
    }
}
