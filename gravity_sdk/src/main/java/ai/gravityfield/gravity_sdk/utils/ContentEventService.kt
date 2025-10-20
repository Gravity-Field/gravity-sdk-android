package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ContentActionModel
import ai.gravityfield.gravity_sdk.models.ContentCloseEvent
import ai.gravityfield.gravity_sdk.models.ContentImpressionEvent
import ai.gravityfield.gravity_sdk.models.ContentLoadEvent
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEvent
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.GravityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ContentEventService private constructor() {

    companion object {
        private var _instance = ContentEventService()

        val instance: ContentEventService
            get() = _instance
    }

    fun sendContentLoaded(
        content: CampaignContent, campaign: Campaign, callbackTrackingEvent: Boolean = true
    ) {
        val onLoad = content.variables.onLoad ?: return
        trackEvent(onLoad, content, campaign, callbackTrackingEvent)
    }

    fun sendContentImpression(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true
    ) {
        val onImpression = content.variables.onImpression ?: return
        trackEvent(onImpression, content, campaign, callbackTrackingEvent)
    }

    fun sendContentVisibleImpression(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true
    ) {
        val onVisibleImpression = content.variables.onVisibleImpression ?: return
        trackEvent(onVisibleImpression, content, campaign, callbackTrackingEvent)
    }

    fun sendContentClosed(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true
    ) {
        val onClose = content.variables.onClose ?: return
        trackEvent(onClose, content, campaign, callbackTrackingEvent)
    }

    private fun trackEvent(
        contentAction: ContentActionModel,
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean
    ) {
        content.events?.find { it.type == contentAction.action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    GravityRepository.instance.trackEngagementEvent(event.urls)

                    if (callbackTrackingEvent) {
                        val trackingEvent = when (contentAction.action) {
                            Action.LOAD -> ContentLoadEvent(content, campaign)
                            Action.IMPRESSION -> ContentImpressionEvent(content, campaign)
                            Action.VISIBLE_IMPRESSION -> ContentVisibleImpressionEvent(
                                content,
                                campaign
                            )

                            Action.CLOSE -> ContentCloseEvent(content, campaign)
                            else -> null
                        } ?: return@launch
                        GravitySDK.instance.gravityEventCallback.invoke(trackingEvent)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }
}
