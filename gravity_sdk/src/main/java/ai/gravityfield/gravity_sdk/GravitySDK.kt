package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.Action
import ai.gravityfield.gravity_sdk.models.Content
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Event
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.ui.GravityBottomSheetContent
import ai.gravityfield.gravity_sdk.ui.GravityFullScreenContent
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

class GravitySDK private constructor(
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

        // todo: add initialization params
        fun init(
            productViewBuilder: ProductViewBuilder? = null,
            productFilter: ProductFilter? = null,
        ) {
            _instance = GravitySDK(
                productViewBuilder,
                productFilter,
            )
        }
    }

    private val repository = GravityRepository()

    fun showSnackbar(context: Context, data: SnackbarData) {
        val dismissController = DismissController()
        showOverlay(context, dismissController, dismissController::dismiss) {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(data) {
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
                    GravitySnackbarType1(data, dismissController::dismiss)
                }
            }

        }
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

    internal suspend fun getCompany(companyId: String): ContentResponse {
        return repository.getContent(companyId)
    }

    private fun trackEngagementEvent(action: Action, events: List<Event>) {
        events.find { it.name == action }?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                repository.trackEngagementEvent(event.urls)
            }
        }

    }

    private fun showBackendContent(context: Context, templateId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.getContent(templateId)
            val content = result.data.first().payload.first().contents.first()

            withContext(Dispatchers.Main) {
                when (content.deliveryMethod) {
                    DeliveryMethod.MODAL -> showModal(context, content)
                    DeliveryMethod.BOTTOM_SHEET -> showBottomSheet(context, content)
                    DeliveryMethod.FULL_SCREEN -> showFullScreen(context, content)

                    DeliveryMethod.SNACK_BAR -> TODO()
                    DeliveryMethod.INLINE -> {}
                    DeliveryMethod.UNKNOWN -> {}
                }
            }
        }
    }

    private fun showModal(context: Context, content: Content) {
        val dismissController = DismissController()
        fun dismiss() {
            trackEngagementEvent(Action.CLOSE, content.events)
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
                    onClickCallback = { onClickModel ->
                        onClickHandler(onClickModel, content, dismissController::dismiss)
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showBottomSheet(context: Context, content: Content) {
        val frameUI = content.variables.frameUI
        val container = frameUI?.container
        val cornerRadius = container?.style?.cornerRadius ?: 0.0

        val dismissController = DismissController()
        fun dismiss() {
            trackEngagementEvent(Action.CLOSE, content.events)
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
                    onClickCallback = { onClickModel ->
                        onClickHandler(
                            onClickModel,
                            content,
                            dismissCallback = {
                                scope.launch { state.hide() }
                                    .invokeOnCompletion { dismissController.dismiss() }
                            }
                        )
                    }
                )
            }
        }
    }

    private fun showFullScreen(context: Context, content: Content) {
        val dismissController = DismissController()
        fun dismiss() {
            trackEngagementEvent(Action.CLOSE, content.events)
            dismissController.dismiss()
        }

        showOverlay(
            context = context,
            dismissController = dismissController,
            onBack = ::dismiss
        ) {
            GravityFullScreenContent(
                content,
                onClickCallback = { onClickModel ->
                    onClickHandler(onClickModel, content, dismissController::dismiss)
                }
            )
        }
    }

    internal fun onClickHandler(
        onClickModel: OnClickModel,
        content: Content,
        dismissCallback: (() -> Unit)? = null,
    ) {
        val action = onClickModel.action

        trackEngagementEvent(action, content.events)

        when (action) {
            Action.LOAD -> {}
            Action.IMPRESSION -> {}
            Action.VISIBLE_IMPRESSION -> {}
            Action.COPY -> {}
            Action.CLOSE -> dismissCallback?.invoke()
            Action.CANCEL -> dismissCallback?.invoke()
            Action.FOLLOW_URL -> {}
            Action.FOLLOW_DEEPLINK -> {}
            Action.REQUEST_PUSH -> {}
            Action.REQUEST_TRACKING -> {}
            Action.UNKNOWN -> {}
        }
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

class DismissController {
    var listener: (() -> Unit)? = null

    fun dismiss() {
        listener?.invoke()
    }

    fun dispose() {
        listener = null
    }
}
