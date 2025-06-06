package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun GravityModalContent(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val frameUi = content.variables.frameUI ?: return
    val container = frameUi.container
    val close = frameUi.close

    LaunchedEffect(Unit) {
        ContentEventService.instance.sendContentImpression(content, campaign)
    }

    VisibilityDetector(
        onVisible = { ContentEventService.instance.sendContentVisibleImpression(content, campaign) }
    ) {
        Surface(
            shape = RoundedCornerShape(container.style.cornerRadius?.dp ?: 0.dp),
            color = container.style.backgroundColor ?: Color.White,
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .conditional(container.style.padding != null) {
                            padding(
                                start = container.style.padding!!.left.dp,
                                top = container.style.padding.top.dp,
                                end = container.style.padding.right.dp,
                                bottom = container.style.padding.bottom.dp
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
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
