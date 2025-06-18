package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GravityInlineCompose(
    modifier: Modifier,
    selector: String,
) {
    var campaign by remember { mutableStateOf<Campaign?>(null) }
    var changedHeight by remember { mutableStateOf<Double?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(selector) {
        campaign = null

        scope.launch {
            try {
                val result = GravitySDK.instance.getContentBySelector(selector)
                campaign = result.data.firstOrNull()
                val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull()
                val height = content?.variables?.frameUI?.container?.style?.size?.height
                withContext(Dispatchers.Main) {
                    if (content == null) {
                        changedHeight = 0.0
                    } else if (height != null) {
                        changedHeight = height
                    }
                }
            } catch (e: Exception) {
                changedHeight = 0.0
            }
        }
    }

    Box(
        modifier = Modifier
            .conditional(changedHeight != null) {
                height(changedHeight!!.dp)
            }
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull()
        if (campaign != null && content != null) {
            val frameUi = content.variables.frameUI
            val container = frameUi?.container
            val padding = container?.style?.padding
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                ContentEventService.instance.sendContentImpression(content, campaign!!)
            }

            Column(
                modifier = Modifier
                    .conditional(padding != null)
                    {
                        padding(
                            start = padding!!.left.dp,
                            top = padding.top.dp,
                            end = padding.right.dp,
                            bottom = padding.bottom.dp
                        )
                    },
                horizontalAlignment = container?.style?.contentAlignment?.toHorizontalAlignment()
                    ?: Alignment.CenterHorizontally
            ) {
                GravityElements(
                    content,
                    campaign!!,
                    onClickCallback = { onClickModel ->
                        GravitySDK.instance.onClickHandler(
                            onClickModel,
                            content,
                            campaign!!,
                            context,
                        )
                    }
                )
            }
        }
    }
}
