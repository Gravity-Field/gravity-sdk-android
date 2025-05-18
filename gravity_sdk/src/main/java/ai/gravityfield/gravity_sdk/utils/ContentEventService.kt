package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ContentActionModel
import ai.gravityfield.gravity_sdk.models.Event
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

    fun sendContentLoaded(content: CampaignContent) {
        val onLoad = content.variables.onLoad ?: return
        trackEvent(onLoad, content.events)
    }

    fun sendContentImpression(content: CampaignContent) {
        val onImpression = content.variables.onImpression ?: return
        trackEvent(onImpression, content.events)
    }

    fun sendContentVisibleImpression(content: CampaignContent) {
        val onVisibleImpression = content.variables.onVisibleImpression ?: return
        trackEvent(onVisibleImpression, content.events)
    }

    fun sendContentClosed(content: CampaignContent) {
        val onClose = content.variables.onClose ?: return
        trackEvent(onClose, content.events)
    }

    private fun trackEvent(contentAction: ContentActionModel, events: List<Event>) {
        events.find { it.name == contentAction.action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                GravityRepository.instance.trackEngagementEvent(event.urls)
            }
        }
    }
}
