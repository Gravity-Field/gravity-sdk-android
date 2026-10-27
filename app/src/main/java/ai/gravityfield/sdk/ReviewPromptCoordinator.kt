package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.ProductClickEngagement
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * App-wide [ReviewPromptIntegration] used for the *real* campaign flow (as opposed to the
 * synthetic fixtures in [ReviewPromptTestConsoleActivity]). Created once and wired into
 * [GravitySDK.initialize] from [MainActivity], so the one `GravitySDK.instance` singleton uses
 * it for every automatic in-app triggered by `trackView`/`triggerEvent`.
 *
 * Points at the same test campaign [CAMPAIGN_ID] (selector [SELECTOR]) as the iOS demo's
 * `ReviewPromptCoordinator`, so the campaign behaves identically across platforms.
 * [DemoReviewEligibilityAPI] is a real, working client eligibility check: one batch lookup
 * against [ReviewStatusStore] (the durable "already reviewed" record), not a stub that approves
 * everything. Tapping "Оставить отзыв" opens a real review form; submitting persists the status
 * so the SKU is excluded next time.
 */
object ReviewPromptCoordinator {
    const val CAMPAIGN_ID = "6acdfec314d8e893950c5402"
    const val SELECTOR = "ios-review"

    private var statusStore: ReviewStatusStore? = null

    lateinit var integration: ReviewPromptIntegration
        private set

    val log = mutableStateListOf<String>()
    val preview = mutableStateListOf<PreviewSlot>()

    /** Drives [ReviewFormDialog]'s presentation; non-null while the form is up. */
    var reviewFormSku: String? by mutableStateOf(null)

    data class PreviewSlot(
        val id: String,
        val slot: Slot,
        val content: CampaignContent,
        val campaign: Campaign,
    )

    /** Must be called once, before [GravitySDK.initialize], with the app's fallback builder for
     *  products outside [SELECTOR]'s campaign. */
    fun initialize(context: Context, fallbackProductViewBuilder: ProductViewBuilder) {
        if (statusStore != null) return
        val store = ReviewStatusStore(context)
        statusStore = store
        integration = ReviewPromptIntegration(
            api = DemoReviewEligibilityAPI(store),
            selector = SELECTOR,
            fallbackProductViewBuilder = fallbackProductViewBuilder,
            onOpenReview = { sku ->
                appendLog("→ onOpenReview: открыта форма отзыва для SKU $sku")
                reviewFormSku = sku
            },
        )
    }

    /** Called after a successful submission from [ReviewFormDialog]. Persists the status so
     *  [DemoReviewEligibilityAPI] excludes this SKU on the next batch check, and updates the
     *  in-session optimistic status too, in case the client's own API has caching lag. */
    fun didSubmitReview(sku: String, rating: Int, comment: String) {
        statusStore?.markReviewed(sku)
        integration.didSubmitReview(sku)
        appendLog("Отзыв отправлен: SKU $sku, $rating★, \"$comment\" — статус сохранён")
    }

    /** Demo-only affordance so the real flow can be re-tested without reinstalling the app. */
    fun resetReviewStatus() {
        statusStore?.reset()
        integration.clearSubmittedReviews()
        appendLog("Статус отзывов сброшен — все SKU снова доступны для отзыва")
    }

    /** [fetchAndPreviewBySelector] never has a live SDK-presented in-app to close —
     *  `getContentBySelector` doesn't show anything, so there's nothing registered for
     *  `dismissInApp` to find. This sends the same real click tracking and opens the review
     *  form directly, since there is genuinely nothing to dismiss here. */
    fun reviewFromPreview(slot: Slot, content: CampaignContent, campaign: Campaign) {
        val sku = slot.item["sku"] as? String ?: ""
        GravitySDK.instance.sendProductEngagement(ProductClickEngagement(slot, content, campaign))
        appendLog("Клик по превью SKU $sku — dismissInApp не вызывается (нет live in-app в этом режиме)")
        reviewFormSku = sku
    }

    /** Matches the campaign's configured trigger: runs the full automatic pipeline. The SDK shows
     *  the first matching campaign on this trigger sorted by priority, so this can show a
     *  different campaign than [SELECTOR] if another one also fires here — use
     *  [fetchAndPreviewBySelector] to target this campaign specifically. */
    fun triggerRealCampaign(context: Context) {
        appendLog("trackView(SEARCH) отправлен — конкурирует с другими кампаниями на этом триггере")
        GravitySDK.instance.trackView(
            pageContext = PageContext(type = ContextType.SEARCH, data = emptyList(), location = "search"),
            activityContext = context,
        )
    }

    /** Fetches the campaign directly by selector — no competition with other campaigns on the
     *  same trigger, deterministic. Runs the content through the same [ReviewPromptIntegration]
     *  filtering the automatic pipeline would apply, then renders whatever survives. */
    suspend fun fetchAndPreviewBySelector() {
        appendLog("getContentBySelector(\"$SELECTOR\") …")
        val response = GravitySDK.instance.getContentBySelector(
            selector = SELECTOR,
            pageContext = PageContext(type = ContextType.SEARCH, data = emptyList(), location = "search"),
        )
        if (response == null) {
            appendLog("getContentBySelector → null (нет кампании с этим selector, либо запрос не удался)")
            preview.clear()
            return
        }
        val campaign = response.data.firstOrNull()
        val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull()
        if (campaign == null || content == null) {
            appendLog("getContentBySelector → кампания найдена, но без контента")
            preview.clear()
            return
        }

        val rawSkus = content.products?.slots.orEmpty().mapNotNull { it.item["sku"] as? String }
        appendLog("contentId=${content.contentId}, товаров до фильтрации: ${rawSkus.size} $rawSkus")

        val filtered = integration.recommendedContent(content, campaign)
        val slots = filtered?.products?.slots.orEmpty()
        appendLog("после recommendedContent: ${slots.size} товар(ов)")
        preview.clear()
        preview.addAll(
            slots.mapIndexed { index, slot ->
                PreviewSlot(slot.slotId ?: "slot-$index", slot, filtered ?: content, campaign)
            }
        )
    }

    private fun appendLog(line: String) {
        log.add(line)
    }
}

/** The client's own eligibility check. Recently-purchased filtering already happened on the
 *  Gravity side (the campaign's products strategy), so the only thing left for the client to
 *  decide is "not already reviewed" — one batch lookup against the durable [ReviewStatusStore],
 *  with a short delay standing in for a real network round-trip. */
private class DemoReviewEligibilityAPI(private val store: ReviewStatusStore) : ReviewEligibilityAPI {
    override suspend fun eligibleSkus(skus: List<String>): Set<String> {
        delay(400)
        val reviewed = store.reviewedSkus()
        return skus.toSet() - reviewed
    }
}
