package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GravityFullScreenContent(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val frameUi = content.variables.frameUI
    val container = frameUi?.container
    val padding = container?.style?.padding
    val close = frameUi?.close

    LaunchedEffect(Unit) {
        ContentEventService.instance.sendContentImpression(content, campaign)
    }

    VisibilityDetector(
        onVisible = { ContentEventService.instance.sendContentVisibleImpression(content, campaign) }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = container?.style?.backgroundColor
                ?: MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
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
                    GravityElements(content, campaign, onClickCallback)
                }

                close?.let {
                    CloseButton(it, onClickCallback)
                }
            }
        }
    }
}
