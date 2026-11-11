package ai.gravityfield.gravity_sdk.assistant

import ai.gravityfield.gravity_sdk.models.assistant.ShoppingEngagementEvent
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends tracking URLs from Shopping API responses.
 * Failures are isolated and swallowed, so tracking never affects the dialog or the rest of the SDK.
 */
internal class AssistantEventService private constructor() {

    companion object {
        private const val TAG = "AssistantEventService"

        private var _instance = AssistantEventService()

        val instance: AssistantEventService
            get() = _instance
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Sends the URLs of all events matching any of [types], case-insensitively.
     * Multiple types cover alternative spellings of the same event.
     */
    fun track(events: List<ShoppingEngagementEvent>, vararg types: String) {
        val type = types.joinToString("/")
        val urls = events
            .filter { event -> types.any { event.type.equals(it, ignoreCase = true) } }
            .flatMap { it.urls }
            .distinct()

        if (urls.isEmpty()) {
            Logger.d(TAG, "No $type urls in response")
            return
        }

        scope.launch {
            try {
                GravityRepository.instance.trackEngagementEvent(urls)
                Logger.d(TAG, "Sent $type: $urls")
            } catch (e: Throwable) {
                Logger.d(TAG, "Failed to send $type: ${e.message}")
            }
        }
    }
}
