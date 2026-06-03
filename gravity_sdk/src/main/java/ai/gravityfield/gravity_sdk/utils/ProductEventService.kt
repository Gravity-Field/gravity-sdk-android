package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ProductAction
import ai.gravityfield.gravity_sdk.models.ProductClickEvent
import ai.gravityfield.gravity_sdk.models.ProductImpressionEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.GravityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ProductEventService private constructor() {

    companion object {
        private var _instance = ProductEventService()

        val instance: ProductEventService
            get() = _instance
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sendProductClick(
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(
                ProductClickEvent(slot, content, campaign)
            )
        }
        trackEngagement(ProductAction.CLICK, slot)
    }

    fun sendProductVisibleImpression(
        slot: Slot,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(
                ProductImpressionEvent(slot, content, campaign)
            )
        }
        trackEngagement(ProductAction.VISIBLE_IMPRESSION, slot)
    }

    private fun trackEngagement(
        action: ProductAction,
        slot: Slot,
    ) {
        slot.events?.find { it.type == action }?.let { event ->
            scope.launch {
                try {
                    GravityRepository.instance.trackEngagementEvent(event.urls)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
