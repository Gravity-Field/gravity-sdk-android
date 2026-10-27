package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.CancelEvent
import ai.gravityfield.gravity_sdk.models.ContentCloseEngagement
import ai.gravityfield.gravity_sdk.models.ContentEngagement
import ai.gravityfield.gravity_sdk.models.ContentImpressionEngagement
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEngagement
import ai.gravityfield.gravity_sdk.models.CopyEvent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Event
import ai.gravityfield.gravity_sdk.models.FollowDeeplinkEvent
import ai.gravityfield.gravity_sdk.models.FollowUrlEvent
import ai.gravityfield.gravity_sdk.models.GravityDataResponse
import ai.gravityfield.gravity_sdk.models.InAppPreparation
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.NotificationPermissionStatus
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.ProductClickEngagement
import ai.gravityfield.gravity_sdk.models.ProductEngagement
import ai.gravityfield.gravity_sdk.models.ProductVisibleImpressionEngagement
import ai.gravityfield.gravity_sdk.models.RequestPushEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.TemplateSystemName
import ai.gravityfield.gravity_sdk.models.TrackingEvent
import ai.gravityfield.gravity_sdk.models.TriggerEvent
import ai.gravityfield.gravity_sdk.models.UISettings
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.models.internal.InlineViewCache
import ai.gravityfield.gravity_sdk.models.internal.InlineViewCacheStore
import ai.gravityfield.gravity_sdk.models.internal.ScrollPosition
import ai.gravityfield.gravity_sdk.models.internal.ScrollProvider
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.CampaignIdsResponse
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.EngagementTransport
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.ui.GravityBottomSheetContent
import ai.gravityfield.gravity_sdk.ui.GravityFullScreenContent
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import ai.gravityfield.gravity_sdk.ui.GravitySnackbarContent1
import ai.gravityfield.gravity_sdk.ui.GravitySnackbarContent2
import ai.gravityfield.gravity_sdk.ui.LocalModalImpression
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import ai.gravityfield.gravity_sdk.utils.DeviceUtils
import ai.gravityfield.gravity_sdk.utils.InAppPresentation
import ai.gravityfield.gravity_sdk.utils.InAppPresentationGate
import ai.gravityfield.gravity_sdk.utils.InAppPresentationRegistry
import ai.gravityfield.gravity_sdk.utils.LogLevel
import ai.gravityfield.gravity_sdk.utils.Logger
import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import ai.gravityfield.gravity_sdk.utils.PresentationRevision
import ai.gravityfield.gravity_sdk.utils.ProductEventService
import ai.gravityfield.gravity_sdk.utils.QueuedVisibleImpression
import ai.gravityfield.gravity_sdk.utils.VisibleImpressionDelivery
import ai.gravityfield.gravity_sdk.utils.VisibleImpressionOwner
import ai.gravityfield.gravity_sdk.utils.VisibleImpressionStore
import ai.gravityfield.gravity_sdk.utils.finishVisibleImpression
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

typealias ProductFilter = (Slot) -> Boolean
typealias GravityEventCallback = (TrackingEvent) -> Unit

class GravitySDK private constructor(
    internal val apiKey: String,
    internal val section: String,
    internal val gravityEventCallback: GravityEventCallback,
    internal val productViewBuilder: ProductViewBuilder?,
    internal val productFilter: ProductFilter?,
    internal val uiSettings: UISettings?,
    private val inAppPreparation: InAppPreparation?,
    private val applicationContext: Context,
) {

    companion object {
        @Volatile private var _instance: GravitySDK? = null
        private val deliveryDispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "gravity-wrimp-io").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        // Persistence submitted by an existing window survives SDK replacement and dispose().
        private val persistenceScope = CoroutineScope(deliveryDispatcher + SupervisorJob())

        val instance: GravitySDK
            get() {
                if (_instance == null) {
                    throw Exception("GravitySDK has not been initialized")
                }
                return _instance!!
            }

        private const val TAG = "SDK"

        fun initialize(
            context: Context,
            apiKey: String,
            section: String,
            gravityEventCallback: GravityEventCallback,
            productViewBuilder: ProductViewBuilder? = null,
            productFilter: ProductFilter? = null,
            uiSettings: UISettings? = null,
            logLevel: LogLevel = LogLevel.NONE,
        ) {
            initialize(context, apiKey, section, gravityEventCallback, productViewBuilder,
                productFilter, uiSettings, logLevel, inAppPreparation = null)
        }

        fun initialize(
            context: Context,
            apiKey: String,
            section: String,
            gravityEventCallback: GravityEventCallback,
            productViewBuilder: ProductViewBuilder? = null,
            productFilter: ProductFilter? = null,
            uiSettings: UISettings? = null,
            logLevel: LogLevel = LogLevel.NONE,
            inAppPreparation: InAppPreparation?,
        ) {
            Logger.configure(logLevel)
            DeviceUtils.initialize(context)
            _instance?.presentationGate?.reset()
            _instance?.stopDeliveryRuntime()
            _instance = GravitySDK(
                apiKey,
                section,
                gravityEventCallback,
                productViewBuilder,
                productFilter,
                uiSettings,
                inAppPreparation,
                context.applicationContext,
            )
            _instance?.startDeliveryRuntime()
        }

        /** No preparation configured passes the content through unchanged; otherwise delegates to it. */
        internal suspend fun resolvedContent(
            content: CampaignContent,
            campaign: Campaign,
            preparation: InAppPreparation?,
        ): CampaignContent? {
            if (preparation == null) return content
            return preparation.prepare(content, campaign)
        }
    }

    private var options = Options()
    private var contentSettings = ContentSettings()
    private val queueFileName = MessageDigest.getInstance("SHA-256")
        .digest((apiKey + "\u0000" + section).toByteArray()).joinToString("") { "%02x".format(it) }
    private val visibleImpressionStore = VisibleImpressionStore.open(
        key = applicationContext.packageName + ":" + queueFileName,
        file = { File(applicationContext.noBackupFilesDir, "gravity-wrimp/$queueFileName.json") },
        onCorrupt = { Logger.e(TAG, "Discarding unreadable WRIMP queue: ${it.message}", it, sendToBack = false) },
    )
    private fun currentIdentity(): String? = identity(GravityRepository.instance.userForRequest(requestUser))
    private fun identity(user: User): String? = user.custom?.let { "custom:$it" } ?: user.uid?.let { "uid:$it" }
    internal val presentationGate = InAppPresentationGate(
        deliveryPending = { visibleImpressionStore.blocks(it, currentIdentity()) },
    )
    internal val visibleImpressionDelivery = VisibleImpressionDelivery(
        send = { GravityRepository.instance.trackEngagementEvent(listOf(it), apiKey) },
        retryable = EngagementTransport::isRetryable,
        store = visibleImpressionStore,
        complete = { entry, remove -> finishVisibleImpression(entry, true, impressionOwner, _instance?.impressionOwner, remove) },
        discard = { entry, remove -> finishVisibleImpression(entry, false, impressionOwner, _instance?.impressionOwner, remove) },
    )
    private val impressionOwner get() = VisibleImpressionOwner(queueFileName, presentationGate, ::currentIdentity)
    // Deliberately independent of UI scopes and dispose(): a visible show must finish delivery.
    private val deliveryScope = CoroutineScope(deliveryDispatcher + SupervisorJob())
    private val deliveryWake = Channel<Unit>(Channel.CONFLATED)
    private val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { resumeVisibleImpressions() }
    }
    private fun resumeVisibleImpressions() {
        deliveryWake.trySend(Unit)
    }
    private fun startDeliveryRuntime() {
        try { connectivity.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback) }
        catch (e: RuntimeException) { Logger.e(TAG, "Connectivity observer unavailable: ${e.message}") }
        resumeVisibleImpressions()
        deliveryScope.launch {
            while (true) {
                // No periodic wakeups with an empty queue, including after dispose().
                if (!visibleImpressionStore.hasWork()) deliveryWake.receive()
                else withTimeoutOrNull(30_000) { deliveryWake.receive() }
                try { visibleImpressionDelivery.resume() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Logger.e(TAG, "Visible impression queue: ${e.message}", e) }
            }
        }
    }
    private fun stopDeliveryRuntime() {
        try { connectivity.unregisterNetworkCallback(networkCallback) } catch (_: RuntimeException) { }
        deliveryScope.cancel()
    }

    private fun modalImpression(content: CampaignContent, campaign: Campaign): ModalImpressionSession {
        val originalUser = GravityRepository.instance.userForRequest(user)
        val campaignId = presentationGate.campaignId(content.contentId)
            ?: campaign.payload.firstOrNull()?.campaignId ?: content.contentId
        return ModalImpressionSession {
            contentEventService.sendModalVisibleImpression(this, content, campaign, originalUser, campaignId)
        }
    }

    internal fun enqueueModalVisibleImpression(
        content: CampaignContent, originalUser: User, campaignId: String, onPersisted: () -> Unit = {},
    ): Boolean = presentationGate.scoped {
        val event = content.events?.find { it.type == Action.VISIBLE_IMPRESSION }
        if (event == null || event.urls.isEmpty()) {
            Logger.e(TAG, "Visible impression has no WRIMP URL")
            return@scoped false
        }
        val originalIdentity = identity(originalUser)
        val queued = QueuedVisibleImpression(
            campaignId = campaignId,
            contentId = content.contentId,
            identity = originalIdentity ?: "unidentified",
            urls = event.urls,
        )
        visibleImpressionStore.stage(queued, onPersisted)
        if (originalIdentity != null) presentationGate.visibleDurable(content.contentId, queued.id)
        persistenceScope.launch {
            try { visibleImpressionDelivery.persistStaged() }
            catch (e: Exception) { Logger.e(TAG, "Cannot persist visible impression: ${e.message}", e) }
            // A still-open window can belong to a replaced SDK; wake the current store owner.
            val owner = _instance?.takeIf { it.queueFileName == queueFileName } ?: this@GravitySDK
            owner.resumeVisibleImpressions()
        }
        true
    }

    private val presentationRevision = PresentationRevision()
    private val inAppPresentations = InAppPresentationRegistry()
    private var requestUser: User? = null
    internal val user: User? get() = presentationGate.scoped { requestUser }

    private fun automaticRequest(pageContext: PageContext, newScreen: Boolean) = presentationGate.scoped {
        if (newScreen) presentationRevision.invalidate()
        Triple(presentationGate.beginRequest(pageContext.location, newScreen), requestUser, presentationRevision.current)
    }
    internal var proxyUrl: String? = null
    internal var notificationPermissionStatus = NotificationPermissionStatus.UNKNOWN

    @Volatile
    private var isPresentationLocked = false
    private var presentationLockListener: ((Boolean) -> Unit)? = null

    private val repository = GravityRepository.instance
    private val contentEventService = ContentEventService.instance
    private val productEventService = ProductEventService.instance

    private val inlineViewCache = InlineViewCacheStore()
    private val inlineListViewCache = InlineViewCacheStore()

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setOptions(
        options: Options?,
        contentSettings: ContentSettings?,
        proxyUrl: String?,
    ) {
        this.options = options ?: Options()
        this.contentSettings = contentSettings ?: ContentSettings()
        this.proxyUrl = proxyUrl
    }

    fun setUser(userId: String, sessionId: String) {
        presentationGate.scoped {
            if (requestUser?.custom != userId || requestUser?.ses != sessionId) {
                presentationGate.reset()
                presentationRevision.invalidate()
            }
            requestUser = User(custom = userId, ses = sessionId)
        }
    }

    fun setNotificationPermissionStatus(status: NotificationPermissionStatus) {
        notificationPermissionStatus = status
    }

    fun setPresentationLockListener(listener: ((Boolean) -> Unit)?) {
        presentationLockListener = listener
    }

    fun lockPresentation() {
        isPresentationLocked = true
        presentationRevision.invalidate()
        Logger.i(TAG, "Presentation lock called, presentation is now locked")
        presentationLockListener?.invoke(true)
    }

    fun unlockPresentation() {
        isPresentationLocked = false
        Logger.i(TAG, "Presentation unlock called, presentation is now unlocked")
        presentationLockListener?.invoke(false)
    }

    fun trackView(
        pageContext: PageContext,
        activityContext: Context,
    ) {
        val (request, currentUser, revision) = automaticRequest(pageContext, true)
        ioScope.launch {
            try {
                val campaignIdsResponse =
                    repository.visit(pageContext, options, currentUser) ?: return@launch
                handleCampaignIdsResponse(campaignIdsResponse, pageContext, activityContext, request, currentUser, revision)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "trackView error: ${e.message}")
            }
        }
    }

    fun triggerEvent(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        activityContext: Context,
    ) {
        // The event happened now; events without their own eventTime are sent with it
        val raisedAt = Date()
        val (request, currentUser, revision) = automaticRequest(pageContext, false)
        ioScope.launch {
            try {
                val campaignIdsResponse =
                    repository.event(events, raisedAt, pageContext, options, currentUser) ?: return@launch
                handleCampaignIdsResponse(campaignIdsResponse, pageContext, activityContext, request, currentUser, revision)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "triggerEvent error: ${e.message}")
            }
        }
    }

    suspend fun getContentBySelector(
        selector: String,
        pageContext: PageContext,
    ): ContentResponse? {
        return getContentBySelectorWithDetails(selector, pageContext)?.data
    }

    /**
     * Same as [getContentBySelector], plus the raw JSON of the same response,
     * for rendering the content yourself. Does not show anything.
     */
    suspend fun getContentBySelectorWithDetails(
        selector: String,
        pageContext: PageContext,
    ): GravityDataResponse<ContentResponse>? {
        val response = repository.chooseBySelectorWithDetails(
            selector = selector,
            options = options,
            contentSettings = contentSettings,
            customerUser = user,
            pageContext = pageContext
        ) ?: return null
        sendContentLoaded(response.data)
        return response
    }

    fun dispose() {
        presentationGate.reset()
        ioScope.cancel()
        mainScope.cancel()
        presentationLockListener = null
    }

    private suspend fun handleCampaignIdsResponse(
        response: CampaignIdsResponse,
        pageContext: PageContext,
        activityContext: Context,
        request: InAppPresentationGate.Request,
        currentUser: User?,
        revision: Long,
    ) {
        if (!presentationGate.isCurrent(request)) return
        // Await restore off main rather than skip the first campaign response during startup.
        withContext(deliveryDispatcher) { visibleImpressionStore.load() }
        val activity = findOwner<Activity>(activityContext) ?: return
        val sortedByPriority = response.campaigns.sortedByDescending { it.priority }
        for (campaignId in sortedByPriority) {
            if (!presentationGate.isCurrent(request)) return
            val reservation = presentationGate.reserve(campaignId.campaignId, request) ?: continue
            var shown = false
            try {
                val result = getContentByCampaignId(campaignId.campaignId, pageContext, currentUser) ?: continue
                val campaign = result.data.firstOrNull() ?: continue
                val payload = campaign.payload.firstOrNull() ?: continue
                val content = payload.contents.sortedBy { it.step }.firstOrNull() ?: continue
                if (content.variables?.elements.isNullOrEmpty()) return
                campaignId.delayTime?.let { delay(it.toLong()) }
                shown = prepareAndShowBackendContent(
                    activity, content, campaign, revision,
                    reservation = reservation, contentIds = payload.contents.map { it.contentId },
                )
                if (shown) return
            } finally {
                if (!shown) presentationGate.cancel(reservation)
            }
        }
    }

    private suspend fun prepareAndShowBackendContent(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        revision: Long,
        item: Item? = null,
        reservation: InAppPresentationGate.Reservation? = null,
        contentIds: List<String> = emptyList(),
        opening: ModalImpressionSession? = null,
    ): Boolean {
        fun canPresent() = GravitySDK.instance === this@GravitySDK &&
            presentationRevision.canPresent(revision, isActivityActive(activity), isPresentationLocked)
        if (!withContext(Dispatchers.Main) { canPresent() }) return false

        val prepared = resolvedContent(content, campaign, inAppPreparation) ?: run {
            Logger.i(TAG, "In-app preparation skipped content ${content.contentId}")
            return false
        }

        return withContext(Dispatchers.Main) {
            if (!canPresent()) return@withContext false
            if (reservation != null) {
                if (activity.window?.decorView?.isShown != true) return@withContext false
                presentationGate.present(reservation, contentIds) {
                    showBackendContent(activity, prepared, campaign, item, opening)
                }
            } else {
                showBackendContent(activity, prepared, campaign, item, opening)
            }
        }
    }

    private fun isActivityActive(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (inAppPreparation == null) return true
        return if (activity is LifecycleOwner) {
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } else {
            activity.hasWindowFocus()
        }
    }

    fun sendContentEngagement(engagement: ContentEngagement) {
        when (engagement) {
            is ContentImpressionEngagement ->
                contentEventService.sendContentImpression(
                    engagement.content,
                    engagement.campaign,
                    false
                )

            is ContentVisibleImpressionEngagement ->
                contentEventService.sendContentVisibleImpression(
                    engagement.content,
                    engagement.campaign,
                    false
                )

            is ContentCloseEngagement ->
                contentEventService.sendContentClosed(
                    engagement.content,
                    engagement.campaign,
                    false
                )
        }
    }

    fun sendProductEngagement(engagement: ProductEngagement) {
        when (engagement) {
            is ProductClickEngagement -> productEventService.sendProductClick(
                engagement.slot,
                engagement.content,
                engagement.campaign,
                false
            )

            is ProductVisibleImpressionEngagement ->
                productEventService.sendProductVisibleImpression(
                    engagement.slot,
                    engagement.content,
                    engagement.campaign,
                    false
                )
        }
    }

    /** Closes the visible SDK in-app with this contentId. Completion runs on the UI thread
     * after dismissal; false means no matching presentation. Inline widgets are unaffected. */
    fun dismissInApp(contentId: String, completion: (Boolean) -> Unit = {}) {
        mainScope.launch { inAppPresentations.dismiss(contentId, completion) }
    }

    private fun registerInApp(
        content: CampaignContent,
        campaign: Campaign,
        reportClose: () -> Boolean = { true },
        performDismiss: (complete: (Boolean) -> Unit) -> Unit,
    ): InAppPresentation {
        var registered: WeakReference<InAppPresentation>? = null
        val presentation = InAppPresentation(performDismiss) { success ->
            inAppPresentations.remove(registered?.get(), content.contentId)
            if (success && reportClose()) contentEventService.sendContentClosed(content, campaign)
        }
        registered = WeakReference(presentation)
        inAppPresentations.register(presentation, content.contentId)
        return presentation
    }

    suspend fun getContentByCampaignId(
        campaignId: String,
        pageContext: PageContext,
    ): ContentResponse? = getContentByCampaignId(campaignId, pageContext, user)

    private suspend fun getContentByCampaignId(
        campaignId: String,
        pageContext: PageContext,
        currentUser: User?,
    ): ContentResponse? {
        return getContentByCampaignIdWithDetails(campaignId, pageContext, currentUser)?.data
    }

    /**
     * Same as [getContentByCampaignId], plus the raw JSON of the same response,
     * for rendering the content yourself. Does not show anything.
     */
    suspend fun getContentByCampaignIdWithDetails(
        campaignId: String,
        pageContext: PageContext,
    ): GravityDataResponse<ContentResponse>? =
        getContentByCampaignIdWithDetails(campaignId, pageContext, user)

    private suspend fun getContentByCampaignIdWithDetails(
        campaignId: String,
        pageContext: PageContext,
        currentUser: User?,
    ): GravityDataResponse<ContentResponse>? {
        val response = repository.chooseByCampaignIdWithDetails(
            campaignId = campaignId,
            options = options,
            contentSettings = contentSettings,
            customerUser = currentUser,
            pageContext = pageContext
        ) ?: return null
        sendContentLoaded(response.data)
        return response
    }

    suspend fun getContentByGroupSelector(
        groupSelector: String,
        pageContext: PageContext,
    ): ContentResponse? {
        val response = repository.chooseByGroup(
            group = groupSelector,
            options = options,
            contentSettings = contentSettings,
            customerUser = user,
            pageContext = pageContext
        ) ?: return null
        sendContentLoaded(response)
        return response
    }

    private fun sendContentLoaded(response: ContentResponse) {
        for (campaign in response.data) {
            for (payload in campaign.payload) {
                for (content in payload.contents) {
                    contentEventService.sendContentLoaded(content, campaign)
                }
            }
        }
    }

    internal fun getInlineViewCache(selector: String, pageContext: PageContext): InlineViewCache? {
        return inlineViewCache.get(selector, pageContext)
    }

    internal fun putInlineViewCache(
        selector: String,
        pageContext: PageContext,
        cache: InlineViewCache,
    ) {
        inlineViewCache.put(selector, pageContext, cache)
    }

    internal fun updateInlineViewScrollPosition(
        selector: String,
        pageContext: PageContext,
        scrollPosition: ScrollPosition,
    ) {
        inlineViewCache.updateScrollPosition(selector, pageContext, scrollPosition)
    }

    fun resetInlineViewCache(selector: String, pageContext: PageContext) {
        inlineViewCache.remove(selector, pageContext)
    }

    internal fun getInlineListViewCache(
        groupSelector: String,
        pageContext: PageContext,
    ): InlineViewCache? {
        return inlineListViewCache.get(groupSelector, pageContext)
    }

    internal fun putInlineListViewCache(
        groupSelector: String,
        pageContext: PageContext,
        cache: InlineViewCache,
    ) {
        inlineListViewCache.put(groupSelector, pageContext, cache)
    }

    internal fun updateInlineListViewScrollPosition(
        groupSelector: String,
        pageContext: PageContext,
        scrollPosition: ScrollPosition,
    ) {
        inlineListViewCache.updateScrollPosition(groupSelector, pageContext, scrollPosition)
    }

    fun resetInlineListViewCache(groupSelector: String, pageContext: PageContext) {
        inlineListViewCache.remove(groupSelector, pageContext)
    }

    private fun trackEngagementEvent(action: Action, events: List<Event>?) {
        events?.find { it.type == action }?.let { event ->
            ioScope.launch {
                try {
                    repository.trackEngagementEvent(event.urls)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun showBackendContent(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        item: Item? = null,
        opening: ModalImpressionSession? = null,
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed || activity.window?.decorView !is ViewGroup) return false
        val impression = opening ?: modalImpression(content, campaign)
        when (content.deliveryMethod) {
            DeliveryMethod.MODAL -> showModal(activity, content, campaign, item, impression)
            DeliveryMethod.BOTTOM_SHEET -> showBottomSheet(activity, content, campaign, item, impression)
            DeliveryMethod.FULL_SCREEN -> showFullScreen(activity, content, campaign, item, impression)
            DeliveryMethod.SNACK_BAR -> {
                if (content.templateSystemName == null || content.templateSystemName == TemplateSystemName.UNKNOWN) return false
                showSnackbar(activity, content, campaign)
            }
            DeliveryMethod.INLINE, DeliveryMethod.UNKNOWN -> return false
        }
        return true
    }

    private fun showModal(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        item: Item?,
        opening: ModalImpressionSession,
    ) {
        val dismissController = DismissController()
        val presentation = registerInApp(content, campaign, reportClose = { !opening.isAbandoned }) { complete ->
            opening.explicitClose()
            val attached = dismissController.listener != null
            dismissController.dismiss()
            complete(attached)
        }
        fun dismiss() {
            presentation.close()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss,
            opening = opening,
            onAbandoned = { presentationGate.closed(content.contentId) },
            onBackground = { opening.background { presentation.close() } },
        ) {
            Dialog(onDismissRequest = ::dismiss) {
                GravityModalContent(
                    content,
                    campaign,
                    onClickCallback = { onClickModel ->
                        onClickHandler(
                            onClickModel,
                            content,
                            campaign,
                            activity,
                            ::dismiss,
                            opening
                        )
                    },
                    item,
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showBottomSheet(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        item: Item? = null,
        opening: ModalImpressionSession,
    ) {
        val frameUI = content.variables?.frameUI
        val container = frameUI?.container
        val cornerRadius = container?.style?.cornerRadius ?: 0.0

        val dismissController = DismissController()
        var requestClose: (((Boolean) -> Unit) -> Unit)? = null
        val presentation = registerInApp(content, campaign, reportClose = { !opening.isAbandoned }) { complete ->
            opening.explicitClose()
            if (dismissController.listener == null) complete(false)
            else if (opening.isAbandoned) { dismissController.dismiss(); complete(true) }
            else {
                val close = requestClose
                if (close != null) close(complete)
                else { dismissController.dismiss(); complete(true) }
            }
        }
        fun dismiss() {
            presentation.close()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss,
            opening = opening,
            onAbandoned = { presentationGate.closed(content.contentId) },
            onBackground = { opening.background { presentation.close() } },
        ) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            requestClose = { complete ->
                scope.launch { state.hide() }.invokeOnCompletion {
                    val attached = dismissController.listener != null
                    dismissController.dismiss()
                    complete(attached)
                }
            }
            ModalBottomSheet(
                onDismissRequest = ::dismiss,
                shape = RoundedCornerShape(
                    topStart = cornerRadius.dp, topEnd = cornerRadius.dp
                ),
                containerColor = container?.style?.backgroundColor
                    ?: BottomSheetDefaults.ContainerColor,
                dragHandle = null,
                sheetState = state,
            ) {
                GravityBottomSheetContent(
                    content,
                    campaign,
                    onClickCallback = { onClickModel ->
                        onClickHandler(
                            onClickModel,
                            content,
                            campaign,
                            activity,
                            dismissCallback = ::dismiss,
                            opening = opening
                        )
                    },
                    item,
                )
            }
        }
    }

    private fun showFullScreen(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        item: Item?,
        opening: ModalImpressionSession,
    ) {
        val dismissController = DismissController()
        val presentation = registerInApp(content, campaign, reportClose = { !opening.isAbandoned }) { complete ->
            opening.explicitClose()
            val attached = dismissController.listener != null
            dismissController.dismiss()
            complete(attached)
        }
        fun dismiss() {
            presentation.close()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss,
            opening = opening,
            onAbandoned = { presentationGate.closed(content.contentId) },
            onBackground = { opening.background { presentation.close() } },
        ) {
            GravityFullScreenContent(
                content,
                campaign,
                onClickCallback = { onClickModel ->
                    onClickHandler(
                        onClickModel,
                        content,
                        campaign,
                        activity,
                        ::dismiss,
                        opening
                    )
                },
                item,
            )
        }
    }

    private fun showSnackbar(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
    ) {
        val template = content.templateSystemName
        if (template == null || template == TemplateSystemName.UNKNOWN) return

        val dismissController = DismissController()
        val presentation = registerInApp(content, campaign) { complete ->
            val attached = dismissController.listener != null
            dismissController.dismiss()
            complete(attached)
        }
        fun dismiss() {
            presentation.close()
        }

        val onClickCallback = { onClickModel: OnClickModel ->
            onClickHandler(
                onClickModel,
                content,
                campaign,
                activity,
                ::dismiss
            )
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss
        ) {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(content) {
                scope.launch {
                    snackbarHostState.showSnackbar("")
                    dismiss()
                }
            }
            Column(
                modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom,
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                ) { _ ->
                    when (template) {
                        TemplateSystemName.SNACKBAR_1 -> GravitySnackbarContent1(
                            content,
                            campaign,
                            onClickCallback
                        )

                        TemplateSystemName.SNACKBAR_2 -> GravitySnackbarContent2(
                            content,
                            campaign,
                            onClickCallback
                        )

                        else -> {}
                    }
                }
            }

        }
    }

    internal fun onClickHandler(
        onClickModel: OnClickModel,
        content: CampaignContent,
        campaign: Campaign,
        context: Context,
        dismissCallback: (() -> Unit)? = null,
        opening: ModalImpressionSession? = null,
    ) {
        val action = onClickModel.action

        trackEngagementEvent(action, content.events)

        if (action != Action.CLOSE && (action != Action.OPEN_STEP || opening == null) && onClickModel.closeOnClick) {
            dismissCallback?.invoke()
        }

        when (action) {
            Action.COPY -> {
                val textToCopy = onClickModel.copyData ?: return
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("", textToCopy)
                clipboard.setPrimaryClip(clip)

                callbackTrackingEvent(
                    CopyEvent(textToCopy, content, campaign)
                )
            }

            Action.CLOSE -> dismissCallback?.invoke()

            Action.CANCEL -> {
                dismissCallback?.invoke()

                callbackTrackingEvent(
                    CancelEvent(content, campaign)
                )
            }

            Action.FOLLOW_URL -> {
                val url = onClickModel.url ?: return
                val type = onClickModel.type ?: return

                callbackTrackingEvent(
                    FollowUrlEvent(url, type, content, campaign)
                )
            }

            Action.FOLLOW_DEEPLINK -> {
                val deeplink = onClickModel.deeplink ?: return

                callbackTrackingEvent(
                    FollowDeeplinkEvent(deeplink, content, campaign)
                )
            }

            Action.REQUEST_PUSH -> {
                val intent = Intent().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        this.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                callbackTrackingEvent(
                    RequestPushEvent(content, campaign)
                )
            }

            Action.OPEN_STEP -> {
                fun closeOldStep() {
                    if (opening != null && onClickModel.closeOnClick) dismissCallback?.invoke()
                }
                val nextStep = onClickModel.step ?: run { closeOldStep(); return }
                val itemId = onClickModel.itemId
                val item = if (itemId != null) content.items?.find { it.id == itemId } else null
                val targetContent =
                    campaign.payload.firstOrNull()?.contents?.find { it.step == nextStep }
                        ?: run { closeOldStep(); return }

                val activity = findOwner<Activity>(context) ?: run { closeOldStep(); return }

                val revision = presentationRevision.current
                ioScope.launch {
                    try {
                        prepareAndShowBackendContent(activity, targetContent, campaign, revision, item, opening = opening)
                    } finally {
                        if (opening != null && onClickModel.closeOnClick) {
                            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { closeOldStep() }
                        }
                    }
                }
            }

            else -> {}
        }
    }

    internal fun callbackTrackingEvent(event: TrackingEvent) {
        mainScope.launch {
            gravityEventCallback.invoke(event)
        }
    }

    private fun showOverlay(
        activity: Activity,
        dismissController: DismissController = DismissController(),
        onBack: () -> Unit,
        opening: ModalImpressionSession? = null,
        onAbandoned: () -> Unit = {},
        onBackground: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        val decorView = activity.window?.decorView as ViewGroup
        val fontResId = uiSettings?.fontResId

        val view = ComposeView(activity).apply {
            setContent {
                val font = remember(fontResId) {
                    if (fontResId != null) FontFamily(Font(fontResId)) else FontFamily.Default
                }
                BackHandler { onBack.invoke() }
                CompositionLocalProvider(LocalAppFont provides font, LocalModalImpression provides opening) {
                    content()
                }
            }
            z = (decorView.children.maxOfOrNull { it.z } ?: 0f) + 1
        }
        if (opening != null) {
            opening.attach()
            val owner = activity as? LifecycleOwner
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) onBackground()
            }
            owner?.lifecycle?.addObserver(observer)
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    owner?.lifecycle?.removeObserver(observer)
                    opening.detach(onAbandoned)
                }
            })
        }
        dismissController.listener = {
            decorView.removeView(view)
            dismissController.dispose()
        }
        decorView.addView(
            view, 0, MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private inline fun <reified T> findOwner(context: Context): T? {
        var innerContext = context
        while (innerContext is ContextWrapper) {
            if (innerContext is T) {
                return innerContext
            }
            innerContext = innerContext.baseContext
        }
        return null
    }
}

internal class DismissController {
    var listener: (() -> Unit)? = null

    fun dismiss() {
        listener?.invoke()
    }

    fun dispose() {
        listener = null
    }
}

internal val LocalAppFont = compositionLocalOf<FontFamily> {
    FontFamily.Default
}

internal val LocalScrollProvider = compositionLocalOf<ScrollProvider?> {
    null
}
