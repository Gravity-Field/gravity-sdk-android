package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.Campaign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Return the content to present, or null to skip this in-app and try the next campaign. */
typealias InAppPreparationHandler = suspend (CampaignContent, Campaign) -> CampaignContent?

/** Runs after productFilter and content-loaded tracking, before any impression events. */
class InAppPreparation(
    val timeoutMillis: Long = 5_000,
    private val handler: InAppPreparationHandler,
) {
    /** The handler must use cancellable suspending APIs and never block a thread. */
    internal suspend fun prepare(content: CampaignContent, campaign: Campaign): CampaignContent? {
        if (timeoutMillis <= 0 || timeoutMillis > 3_600_000) return null
        return try {
            withTimeoutOrNull(timeoutMillis) { handler(content, campaign) }
        } catch (cancelled: CancellationException) {
            // A client's own timeout/cancellation skips this content; cancellation of our
            // parent scope (e.g. dispose) must still prevent further presentation work.
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
    }
}

/** Keeps original slots, order, events and content metadata. An empty result skips the in-app. */
fun CampaignContent.filteringProducts(predicate: (Slot) -> Boolean): CampaignContent? {
    val original = products ?: return null
    val slots = original.slots.orEmpty().filter(predicate)
    if (slots.isEmpty()) return null
    return copy(products = original.copy(slots = slots))
}
