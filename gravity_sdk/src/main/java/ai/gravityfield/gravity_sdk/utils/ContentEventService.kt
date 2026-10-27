package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ContentActionModel
import ai.gravityfield.gravity_sdk.models.ContentCloseEvent
import ai.gravityfield.gravity_sdk.models.ContentImpressionEvent
import ai.gravityfield.gravity_sdk.models.ContentLoadEvent
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEvent
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.GravityRepository
import kotlinx.coroutines.CancellationException
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

    /** Modal openings call this once after a content frame, before any close destroys the UI. */
    fun sendModalVisibleImpression(
        sdk: GravitySDK,
        content: CampaignContent,
        campaign: Campaign,
        originalUser: User,
        campaignId: String,
    ): Boolean {
        return sdk.enqueueModalVisibleImpression(content, originalUser, campaignId) {
            sdk.callbackTrackingEvent(ContentVisibleImpressionEvent(content, campaign))
        }
    }

    fun sendContentVisibleImpression(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        val sdk = GravitySDK.instance
        sdk.presentationGate.visible(content.contentId)
        if (callbackTrackingEvent) sdk.callbackTrackingEvent(ContentVisibleImpressionEvent(content, campaign))
        val action = content.variables?.onVisibleImpression?.action ?: Action.VISIBLE_IMPRESSION
        val event = content.events?.find { it.type == action }
        if (event == null || event.urls.isEmpty()) {
            Logger.e("ContentEvent", "Visible impression has no tracking URL")
            return
        }
        scope.launch {
            if (sdk.visibleImpressionDelivery.deliver(event.urls)) {
                if (action != Action.VISIBLE_IMPRESSION) return@launch
                sdk.presentationGate.acknowledged(content.contentId)
            } else {
                Logger.e("ContentEvent", "Visible impression delivery failed after retries")
            }
        }
    }

    fun sendContentClosed(
        content: CampaignContent,
        campaign: Campaign,
        callbackTrackingEvent: Boolean = true,
    ) {
        GravitySDK.instance.presentationGate.closed(content.contentId)
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
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Logger.e("ContentEvent", "Engagement delivery failed: ${error.message}", error)
                }
            }
        }
    }
}
