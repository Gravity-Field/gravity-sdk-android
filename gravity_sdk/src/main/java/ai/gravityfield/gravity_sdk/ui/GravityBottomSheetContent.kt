package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun GravityBottomSheetContent(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
    item: Item?,
) {
    val scrollState = rememberScrollState()
    val frameUi = content.variables?.frameUI
    val container = frameUi?.container
    val style = container?.style
    val padding = style?.padding
    val hasGrid = content.hasProductsGrid()
    val hasWeightedSpacer = hasGrid && content.variables?.elements.orEmpty().any { it.type == ElementType.SPACER }
    val horizontalAlignment =
        style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
    val backgroundColor = style?.backgroundColor ?: MaterialTheme.colorScheme.background
    val backgroundImage = style?.backgroundImage
    val backgroundFit = style?.backgroundFit ?: ContentScale.Crop
    val close = frameUi?.close

    val presentationAnalytics = LocalPresentationAnalytics.current
    LaunchedEffect(Unit) {
        if (presentationAnalytics?.claimImpression() != false) {
            ContentEventService.instance.sendContentImpression(content, campaign)
        }
    }

    ModalVisibilityDetector {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
        ) {
            if (backgroundImage != null) {
                Image(
                    modifier = Modifier.matchParentSize(),
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
                    }
                    .fillMaxWidth()
                    .conditional(hasWeightedSpacer) { fillMaxHeight() }
                    .conditional(hasGrid) { verticalScroll(scrollState) },
                horizontalAlignment = horizontalAlignment,
            ) {
                GravityElements(content, campaign, onClickCallback, item)
            }

            close?.let {
                CloseButton(it, onClickCallback)
            }
        }
    }
}
