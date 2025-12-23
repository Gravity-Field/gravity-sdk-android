package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.CancelEvent
import ai.gravityfield.gravity_sdk.models.ContentCloseEngagement
import ai.gravityfield.gravity_sdk.models.ContentCloseEvent
import ai.gravityfield.gravity_sdk.models.ContentEngagement
import ai.gravityfield.gravity_sdk.models.ContentImpressionEngagement
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEngagement
import ai.gravityfield.gravity_sdk.models.CopyEvent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Event
import ai.gravityfield.gravity_sdk.models.FollowDeeplinkEvent
import ai.gravityfield.gravity_sdk.models.FollowUrlEvent
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
import ai.gravityfield.gravity_sdk.models.internal.ScrollProvider
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.CampaignIdsResponse
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.ui.GravityBottomSheetContent
import ai.gravityfield.gravity_sdk.ui.GravityFullScreenContent
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import ai.gravityfield.gravity_sdk.ui.GravitySnackbarContent1
import ai.gravityfield.gravity_sdk.ui.GravitySnackbarContent2
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import ai.gravityfield.gravity_sdk.utils.DeviceUtils
import ai.gravityfield.gravity_sdk.utils.ProductEventService
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Objects

typealias ProductFilter = (Slot) -> Boolean
typealias GravityEventCallback = (TrackingEvent) -> Unit

class GravitySDK private constructor(
    internal val apiKey: String,
    internal val section: String,
    internal val gravityEventCallback: GravityEventCallback,
    internal val productViewBuilder: ProductViewBuilder?,
    internal val productFilter: ProductFilter?,
    internal val uiSettings: UISettings?,
) {

    companion object {
        private var _instance: GravitySDK? = null

        val instance: GravitySDK
            get() {
                if (_instance == null) {
                    throw Exception("GravitySDK has not been initialized")
                }
                return _instance!!
            }

        fun initialize(
            context: Context,
            apiKey: String,
            section: String,
            gravityEventCallback: GravityEventCallback,
            productViewBuilder: ProductViewBuilder? = null,
            productFilter: ProductFilter? = null,
            uiSettings: UISettings? = null,
        ) {
            DeviceUtils.initialize(context)
            _instance = GravitySDK(
                apiKey,
                section,
                gravityEventCallback,
                productViewBuilder,
                productFilter,
                uiSettings,
            )
        }
    }

    private var user: User? = null
    private var options = Options()
    private var contentSettings = ContentSettings()
    internal var proxyUrl: String? = null
    internal var notificationPermissionStatus = NotificationPermissionStatus.UNKNOWN

    private val repository = GravityRepository.instance
    private val contentEventService = ContentEventService.instance
    private val productEventService = ProductEventService.instance

    private val inlineViewCache = mutableMapOf<Int, InlineViewCache>()

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
        user = User(custom = userId, ses = sessionId)
    }

    fun setNotificationPermissionStatus(status: NotificationPermissionStatus) {
        notificationPermissionStatus = status
    }

    suspend fun trackView(
        pageContext: PageContext,
        activityContext: Context,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val campaignIdsResponse = repository.visit(pageContext, options, user)
                handleCampaignIdsResponse(campaignIdsResponse, pageContext, activityContext)
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun triggerEvent(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        activityContext: Context,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val campaignIdsResponse = repository.event(events, pageContext, options, user)
                handleCampaignIdsResponse(campaignIdsResponse, pageContext, activityContext)
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun handleCampaignIdsResponse(
        response: CampaignIdsResponse,
        pageContext: PageContext,
        activityContext: Context,
    ) {
        val activity = findOwner<Activity>(activityContext) ?: return

        val sortedByPriority = response.campaigns.sortedByDescending { it.priority }
        for (campaignId in sortedByPriority) {
            val result = getContentByCampaignId(campaignId.campaignId, pageContext)

            val campaign = result.data.firstOrNull() ?: continue
            val payload = campaign.payload.firstOrNull() ?: continue
            val content = payload.contents.filter { it.step != null }
                .sortedBy { it.step }.firstOrNull() ?: payload.contents.firstOrNull() ?: continue

            val delayTime = campaignId.delayTime
            if (delayTime != null) {
                delay(delayTime.toLong())
            }

            if (activity.isFinishing || activity.isDestroyed) {
                return
            }

            withContext(Dispatchers.Main) {
                showBackendContent(activity, content, campaign)
            }
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

    internal suspend fun getContentByCampaignId(
        campaignId: String,
        pageContext: PageContext,
    ): ContentResponse {
        val response = withContext(Dispatchers.IO) {
            repository.chooseByCampaignId(
                campaignId = campaignId,
                options = options,
                contentSettings = contentSettings,
                pageContext = pageContext
            )
        }
        for (campaign in response.data) {
            for (payload in campaign.payload) {
                for (content in payload.contents) {
                    contentEventService.sendContentLoaded(content, campaign)
                }
            }
        }
        return response
    }

    internal suspend fun getContentBySelector(
        selector: String,
        pageContext: PageContext,
    ): ContentResponse {
        val response = withContext(Dispatchers.IO) {
            repository.chooseBySelector(
                selector = selector,
                options = options,
                contentSettings = contentSettings,
                pageContext = pageContext
            )
        }
        for (campaign in response.data) {
            for (payload in campaign.payload) {
                for (content in payload.contents) {
                    contentEventService.sendContentLoaded(content, campaign)
                }
            }
        }
        return response
    }

    internal fun getInlineViewCache(selector: String, pageContext: PageContext): InlineViewCache? {
        val key = getInlineViewCacheKey(selector, pageContext)
        return inlineViewCache[key]
    }

    internal fun putInlineViewCache(
        selector: String,
        pageContext: PageContext,
        cache: InlineViewCache,
    ) {
        val key = getInlineViewCacheKey(selector, pageContext)
        inlineViewCache[key] = cache
    }

    fun resetInlineViewCache(selector: String, pageContext: PageContext) {
        val key = getInlineViewCacheKey(selector, pageContext)
        inlineViewCache.remove(key)
    }

    private fun getInlineViewCacheKey(selector: String, pageContext: PageContext): Int {
        return Objects.hash(selector, pageContext)
    }

    private fun trackEngagementEvent(action: Action, events: List<Event>?) {
        events?.find { it.type == action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
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
    ) {
        when (content.deliveryMethod) {
            DeliveryMethod.MODAL -> showModal(activity, content, campaign, item)
            DeliveryMethod.BOTTOM_SHEET -> showBottomSheet(activity, content, campaign, item)
            DeliveryMethod.FULL_SCREEN -> showFullScreen(activity, content, campaign, item)
            DeliveryMethod.SNACK_BAR -> showSnackbar(activity, content, campaign)
            DeliveryMethod.INLINE -> {}
            DeliveryMethod.UNKNOWN -> {}
        }
    }


    private fun showModal(
        activity: Activity,
        content: CampaignContent,
        campaign: Campaign,
        item: Item?,
    ) {
        val dismissController = DismissController()
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss
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
                            ::dismiss
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
    ) {
        val frameUI = content.variables.frameUI
        val container = frameUI?.container
        val cornerRadius = container?.style?.cornerRadius ?: 0.0

        val dismissController = DismissController()
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss
        ) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
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
                            dismissCallback = {
                                scope.launch { state.hide() }
                                    .invokeOnCompletion { dismiss() }
                            }
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
    ) {
        val dismissController = DismissController()
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
        }

        showOverlay(
            activity = activity,
            dismissController = dismissController,
            onBack = ::dismiss
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
                        ::dismiss
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
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
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
    ) {
        val action = onClickModel.action

        trackEngagementEvent(action, content.events)

        if (action != Action.CLOSE && onClickModel.closeOnClick) {
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
                val nextStep = onClickModel.step ?: return
                val itemId = onClickModel.itemId ?: return
                val item = content.items?.find { it.id == itemId } ?: return
                val targetContent =
                    campaign.payload.firstOrNull()?.contents?.find { it.step == nextStep } ?: return

                val activity = findOwner<Activity>(context) ?: return

                showBackendContent(activity, targetContent, campaign, item)
            }

            else -> {}
        }
    }

    private fun callbackTrackingEvent(event: TrackingEvent) {
        gravityEventCallback.invoke(event)
    }

    private fun showOverlay(
        activity: Activity,
        dismissController: DismissController = DismissController(),
        onBack: () -> Unit,
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
                CompositionLocalProvider(LocalAppFont provides font) {
                    content()
                }
            }
            z = decorView.children.maxOf { it.z } + 1
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