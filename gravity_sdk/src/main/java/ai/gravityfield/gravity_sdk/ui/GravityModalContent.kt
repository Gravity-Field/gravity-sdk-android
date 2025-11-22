package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun GravityModalContent(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val frameUi = content.variables.frameUI
    val container = frameUi?.container
    val style = container?.style
    val padding = style?.padding
    val cornerRadius = style?.cornerRadius?.dp ?: 0.dp
    val horizontalAlignment =
        style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
    val backgroundColor = style?.backgroundColor ?: MaterialTheme.colorScheme.surface
    val backgroundImage = style?.backgroundImage
    val backgroundFit = style?.backgroundFit ?: ContentScale.Crop
    val close = frameUi?.close

    LaunchedEffect(Unit) {
        ContentEventService.instance.sendContentImpression(content, campaign)
    }

    VisibilityDetector(
        onVisible = { ContentEventService.instance.sendContentVisibleImpression(content, campaign) }
    ) {
        Surface(
            shape = RoundedCornerShape(cornerRadius),
            color = backgroundColor,
        ) {
            Box {
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
                        .fillMaxWidth()
                        .conditional(padding != null) {
                            padding(
                                start = padding!!.left.dp,
                                top = padding.top.dp,
                                end = padding.right.dp,
                                bottom = padding.bottom.dp
                            )
                        },
                    horizontalAlignment = horizontalAlignment,
                ) {
                    GravityElements(content, campaign, onClickCallback)
                }

                close?.let {
                    CloseButton(it, onClickCallback)
                }
            }
        }
    }
}
