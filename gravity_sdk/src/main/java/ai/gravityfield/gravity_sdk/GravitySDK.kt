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
import ai.gravityfield.gravity_sdk.models.Device
import ai.gravityfield.gravity_sdk.models.Event
import ai.gravityfield.gravity_sdk.models.FollowDeeplinkEvent
import ai.gravityfield.gravity_sdk.models.FollowUrlEvent
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.ProductClickEngagement
import ai.gravityfield.gravity_sdk.models.ProductEngagement
import ai.gravityfield.gravity_sdk.models.ProductVisibleImpressionEngagement
import ai.gravityfield.gravity_sdk.models.RequestPushEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.TrackingEvent
import ai.gravityfield.gravity_sdk.models.TriggerEvent
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.ui.GravityBottomSheetContent
import ai.gravityfield.gravity_sdk.ui.GravityFullScreenContent
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import ai.gravityfield.gravity_sdk.ui.GravitySnackbarType1
import ai.gravityfield.gravity_sdk.ui.mockSnackbarData
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.children
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias ProductViewBuilder = (Context, Slot) -> View
typealias ProductFilter = (Slot) -> Boolean
typealias GravityEventCallback = (TrackingEvent) -> Unit

class GravitySDK private constructor(
    internal val apiKey: String,
    internal val section: String,
    internal val device: Device,
    internal val gravityEventCallback: GravityEventCallback,
    internal val productViewBuilder: ProductViewBuilder?,
    internal val productFilter: ProductFilter?,
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
        ) {
            val device = Device(
                id = DeviceUtils.getDeviceId(),
                userAgent = DeviceUtils.getUserAgent(context)
            )
            _instance = GravitySDK(
                apiKey,
                section,
                device,
                gravityEventCallback,
                productViewBuilder,
                productFilter,
            )
        }
    }

    private var user: User? = null
    private var options = Options()
    private var contentSettings = ContentSettings()
    internal var proxyUrl: String? = null

    private val repository = GravityRepository.instance
    private val contentEventService = ContentEventService.instance
    private val productEventService = ProductEventService.instance

    fun setOptions(
        options: Options?,
        contentSettings: ContentSettings?,
        proxyUrl: String?
    ) {
        this.options = options ?: Options()
        this.contentSettings = contentSettings ?: ContentSettings()
        this.proxyUrl = proxyUrl
    }

    fun setUser(userId: String, sessionId: String) {
        user = User(custom = userId, ses = sessionId)
    }

    suspend fun trackView(
        pageContext: PageContext,
        context: Context
    ) {
        withContext(Dispatchers.IO) {
            val campaignIdsResponse = repository.visit(pageContext, options, user)
            val campaignId = campaignIdsResponse.campaigns.firstOrNull()
            if (campaignId != null) {
                val result = getContentByCampaignId(campaignId.campaignId, pageContext)

                val campaign = result.data.firstOrNull() ?: return@withContext
                val content =
                    campaign.payload.firstOrNull()?.contents?.firstOrNull() ?: return@withContext

                withContext(Dispatchers.Main) {
                    showBackendContent(context, content, campaign)
                }
            }
        }
    }

    suspend fun triggerEvent(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        context: Context
    ) {
        withContext(Dispatchers.IO) {
            val campaignIdsResponse = repository.event(events, pageContext, options, user)
            val campaignId = campaignIdsResponse.campaigns.firstOrNull()
            if (campaignId != null) {
                val result = getContentByCampaignId(campaignId.campaignId, pageContext)

                val campaign = result.data.firstOrNull() ?: return@withContext
                val content =
                    campaign.payload.firstOrNull()?.contents?.firstOrNull() ?: return@withContext

                withContext(Dispatchers.Main) {
                    showBackendContent(context, content, campaign)
                }
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

    fun showSnackbar1(context: Context) {
        showBackendContent(context, "snackbar-1")
    }

    fun showModal1(context: Context) {
        showBackendContent(context, "modal-template-1")
    }

    fun showModal2(context: Context) {
        showBackendContent(context, "modal-template-2")
    }

    fun showBottomSheet(context: Context) {
        showBackendContent(context, "bottom-sheet-template-1")
    }

    fun showBottomSheetBanner(context: Context) {
        showBackendContent(context, "bottom-sheet-banner")
    }

    fun showBottomSheetProductsGrid(context: Context) {
        showBackendContent(context, "bottom-sheet-products-grid-2")
    }

    fun showBottomSheetProductsRow(context: Context) {
        showBackendContent(context, "bottom-sheet-products-row")
    }

    fun showFullScreen(context: Context) {
        showBackendContent(context, "fullscreen-banner")
    }

    suspend fun getContentByCampaignId(
        campaignId: String,
        pageContext: PageContext? = null
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

    suspend fun getContentBySelector(
        selector: String,
        pageContext: PageContext? = null
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

    private fun trackEngagementEvent(action: Action, events: List<Event>?) {
        events?.find { it.name == action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.trackEngagementEvent(event.urls)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun showBackendContent(context: Context, templateId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = getContentByCampaignId(templateId)
            val campaign = result.data.first()
            val content = campaign.payload.first().contents.first()

            withContext(Dispatchers.Main) {
                when (content.deliveryMethod) {
                    DeliveryMethod.MODAL -> showModal(context, content, campaign)
                    DeliveryMethod.BOTTOM_SHEET -> showBottomSheet(context, content, campaign)
                    DeliveryMethod.FULL_SCREEN -> showFullScreen(context, content, campaign)
                    DeliveryMethod.SNACK_BAR -> showSnackbar(context, content, campaign)
                    DeliveryMethod.INLINE -> {}
                    DeliveryMethod.UNKNOWN -> {}
                }
            }
        }
    }

    private fun showBackendContent(context: Context, content: CampaignContent, campaign: Campaign) {
        when (content.deliveryMethod) {
            DeliveryMethod.MODAL -> showModal(context, content, campaign)
            DeliveryMethod.BOTTOM_SHEET -> showBottomSheet(context, content, campaign)
            DeliveryMethod.FULL_SCREEN -> showFullScreen(context, content, campaign)
            DeliveryMethod.SNACK_BAR -> showSnackbar(context, content, campaign)
            DeliveryMethod.INLINE -> {}
            DeliveryMethod.UNKNOWN -> {}
        }
    }


    private fun showModal(context: Context, content: CampaignContent, campaign: Campaign) {
        val dismissController = DismissController()
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
        }

        showOverlay(
            context = context,
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
                            context,
                            ::dismiss
                        )
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showBottomSheet(context: Context, content: CampaignContent, campaign: Campaign) {
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
            context = context,
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
                            context,
                            dismissCallback = {
                                scope.launch { state.hide() }
                                    .invokeOnCompletion { dismiss() }
                            }
                        )
                    }
                )
            }
        }
    }

    private fun showFullScreen(context: Context, content: CampaignContent, campaign: Campaign) {
        val dismissController = DismissController()
        fun dismiss() {
            contentEventService.sendContentClosed(content, campaign)
            gravityEventCallback.invoke(ContentCloseEvent(content, campaign))
            dismissController.dismiss()
        }

        showOverlay(
            context = context,
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
                        context,
                        ::dismiss
                    )
                }
            )
        }
    }

    private fun showSnackbar(context: Context, content: CampaignContent, campaign: Campaign) {
        val dismissController = DismissController()
        showOverlay(context, dismissController, dismissController::dismiss) {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            // todo: change to backend content
            LaunchedEffect(mockSnackbarData) {
                scope.launch {
                    snackbarHostState.showSnackbar("")
                    dismissController.dismiss()
                }
            }
            Column(
                modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom,
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                ) { _ ->
                    GravitySnackbarType1(mockSnackbarData, dismissController::dismiss)
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

                callbackTrackingEvent(
                    FollowUrlEvent(url, content, campaign)
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

            else -> {}
        }
    }

    private fun callbackTrackingEvent(event: TrackingEvent) {
        gravityEventCallback.invoke(event)
    }


    private fun showOverlay(
        context: Context,
        dismissController: DismissController = DismissController(),
        onBack: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        val decorView = findOwner<Activity>(context)?.window?.decorView as ViewGroup
        val view = ComposeView(context).apply {
            setContent {
                BackHandler { onBack.invoke() }
                content()
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
