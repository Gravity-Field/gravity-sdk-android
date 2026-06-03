package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ContentEventService private constructor() {

    companion object {
        private var _instance = ContentEventService()

        val instance: ContentEventService
            get() = _instance
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sendContentLoaded(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(ContentLoadEvent(content, campaign))
        }
        val onLoad = content.variables?.onLoad ?: return
        trackEngagement(onLoad, content)
    }

    fun sendContentImpression(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(ContentImpressionEvent(content, campaign))
        }
        val onImpression = content.variables?.onImpression ?: return
        trackEngagement(onImpression, content)
    }

    fun sendContentVisibleImpression(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(
                ContentVisibleImpressionEvent(content, campaign)
            )
        }
        val onVisibleImpression = content.variables?.onVisibleImpression ?: return
        trackEngagement(onVisibleImpression, content)
    }

    fun sendContentClosed(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        if (callbackTrackingEvent) {
            GravitySDK.instance.callbackTrackingEvent(ContentCloseEvent(content, campaign))
        }
        val onClose = content.variables?.onClose ?: return
        trackEngagement(onClose, content)
    }

    private fun trackEngagement(
        contentAction: ContentActionModel,
        content: CampaignContent,
    ) {
        content.events?.find { it.type == contentAction.action }?.let { event ->
            scope.launch {
                try {
                    GravityRepository.instance.trackEngagementEvent(event.urls)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
