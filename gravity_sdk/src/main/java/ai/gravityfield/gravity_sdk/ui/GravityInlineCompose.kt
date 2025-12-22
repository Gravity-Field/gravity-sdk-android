package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GravityInlineCompose(
    modifier: Modifier,
    selector: String,
    pageContext: PageContext,
    loader: (@Composable () -> Unit)?,
) {
    var campaign by remember { mutableStateOf<Campaign?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var changedHeight by remember { mutableStateOf<Double?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(selector) {
        campaign = null

        scope.launch {
            try {
                val result = GravitySDK.instance.getContentBySelector(selector, pageContext)
                campaign = result.data.firstOrNull()
                val payload = campaign?.payload?.firstOrNull()
                val content =
                    payload?.contents?.filter { it.step != null }?.sortedBy { it.step }
                        ?.firstOrNull()
                        ?: payload?.contents?.firstOrNull()
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
            } finally {
                isLoading = false
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
        val payload = campaign?.payload?.firstOrNull()
        val content =
            payload?.contents?.filter { it.step != null }?.sortedBy { it.step }?.firstOrNull()
                ?: payload?.contents?.firstOrNull()

        when {
            isLoading && loader != null -> loader()
            campaign != null && content != null -> {
                val frameUi = content.variables.frameUI
                val container = frameUi?.container
                val style = container?.style
                val padding = style?.padding
                val horizontalAlignment =
                    style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
                val backgroundImage = style?.backgroundImage
                val backgroundFit = style?.backgroundFit ?: ContentScale.Crop
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    ContentEventService.instance.sendContentImpression(content, campaign!!)
                }

                if (backgroundImage != null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = rememberAsyncImagePainter(model = backgroundImage),
                        contentDescription = null,
                        contentScale = backgroundFit,
                    )
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
                    horizontalAlignment = horizontalAlignment
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
}
