package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.ElementType
import ai.gravityfield.gravity_sdk.models.OnClickModel
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityButton
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityImage
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityText
import ai.gravityfield.gravity_sdk.utils.ContentEventService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun GravitySnackbarContent2(
    content: CampaignContent,
    campaign: Campaign,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val frameUi = content.variables.frameUI ?: return
    val container = frameUi.container
    val style = container.style
    val padding = style?.padding
    val onClick = container.onClick
    val elements = content.variables.elements
    val texts = elements.filter { it.type == ElementType.TEXT }
    val image = elements.firstOrNull { it.type == ElementType.IMAGE }
    val button = elements.firstOrNull { it.type == ElementType.BUTTON }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        ContentEventService.instance.sendContentImpression(content, campaign)
    }

    VisibilityDetector(
        onVisible = { ContentEventService.instance.sendContentVisibleImpression(content, campaign) }
    ) {
        Surface(
            modifier = Modifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.systemBars.asPaddingValues()
                        .calculateBottomPadding() + 10.dp,
                )
                .fillMaxWidth()
                .conditional(onClick != null) {
                    clickable(interactionSource = interactionSource, onClick = {
                        onClickCallback.invoke(onClick!!)
                    }, indication = null)
                },
            shape = RoundedCornerShape(style?.cornerRadius?.dp ?: 0.dp),
            color = container.style?.backgroundColor ?: Color.White,
        ) {
            Row(
                modifier = Modifier.conditional(padding != null) {
                    padding(
                        start = padding!!.left.dp,
                        top = padding.top.dp,
                        end = padding.right.dp,
                        bottom = padding.bottom.dp
                    )
                },
                verticalAlignment = Alignment.Top,
            ) {
                image?.let {
                    GravityImage(it, onClickCallback)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    texts.getOrNull(0)?.let {
                        GravityText(it, onClickCallback)
                    }
                    texts.getOrNull(1)?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        GravityText(it, onClickCallback)
                    }
                    button?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        GravityButton(it, onClickCallback)
                    }
                }
            }
        }
    }
}
