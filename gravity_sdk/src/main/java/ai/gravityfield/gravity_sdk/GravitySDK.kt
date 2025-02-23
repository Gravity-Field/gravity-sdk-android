package ai.gravityfield.gravity_sdk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.window.Dialog
import androidx.core.view.children
import kotlinx.coroutines.launch


class GravitySDK private constructor() {

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
        fun init() {
            _instance = GravitySDK()
        }
    }

    fun showModal(context: Context, data: ModalData) {
        val dismissController = DismissController()
        showOverlay(context, dismissController) {
            Dialog(
                onDismissRequest = {
                    dismissController.dismiss()
                },
            ) {
                GravityModalType1(data, dismissController::dismiss)
            }
        }
    }

    fun showSnackbar(context: Context, data: SnackbarData) {
        val dismissController = DismissController()
        showOverlay(context, dismissController) {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(data) {
                scope.launch {
                    snackbarHostState.showSnackbar("")
                    dismissController.dismiss()
                }
            }
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                SnackbarHost(
                    hostState = snackbarHostState,
                ) { _ ->
                    GravitySnackbarType1(data, dismissController::dismiss)
                }
            }

        }
    }

    private fun showOverlay(
        context: Context,
        dismissController: DismissController = DismissController(),
        content: @Composable () -> Unit,
    ) {
        val decorView = findOwner<Activity>(context)?.window?.decorView as ViewGroup
        val view = ComposeView(context).apply {
            setContent {
                BackHandler { dismissController.dismiss() }
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
