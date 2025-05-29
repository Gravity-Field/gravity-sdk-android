package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ProductAction
import ai.gravityfield.gravity_sdk.models.ProductImpressionEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
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

    fun sendProductClick(
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true
    ) {
        trackEvent(ProductAction.PCLICK, slot, content, campaign, callbackTrackingEvent)
    }

    fun sendProductVisibleImpression(
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true
    ) {
        trackEvent(ProductAction.PIMP, slot, content, campaign, callbackTrackingEvent)
    }

    private fun trackEvent(
        action: ProductAction,
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean
    ) {
        slot.events.find { it.name == action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                GravityRepository.instance.trackEngagementEvent(event.urls)
            }
        }

        if (callbackTrackingEvent) {
            val event = when (action) {
                ProductAction.PIMP -> ProductImpressionEvent(slot, content, campaign)
                else -> null
            } ?: return
            GravitySDK.instance.gravityEventCallback.invoke(event)
        }
    }
}
