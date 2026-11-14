package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * iOS/Flutter row geometry: `size.height` is the outer box including padding, the background
 * fills that box, margins stay outside it, and products never draw outside it.
 */
@RunWith(AndroidJUnit4::class)
class ProductsRowGeometryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val campaign = Campaign(null, emptyList())

    private fun exercise(cardHeight: Int) {
        rule.runOnUiThread {
            rule.activity.actionBar?.hide()
            GravitySDK.initialize(rule.activity, "row-geometry", "row-geometry", {}, productViewBuilder = object : ProductViewBuilder {
                @Composable override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
                    // A host card that does not shrink to the row, like the example ProductView.
                    Box(Modifier.width(60.dp).requiredHeight(cardHeight.dp).background(Color.Blue).testTag("card-${slot.slotId}"))
                }
            })
        }
        val content = CampaignContent("row-geometry", null, DeliveryMethod.MODAL, "test", 1,
            Variables.fromJson(mapOf("elements" to listOf(
                mapOf("type" to "text", "text" to "Above", "style" to mapOf("size" to mapOf("height" to 20))),
                mapOf("type" to "products-container", "style" to mapOf("productContainerType" to "row",
                    "size" to mapOf("height" to 120), "backgroundColor" to "#FFFF00",
                    "margin" to mapOf("top" to 12, "bottom" to 12, "left" to 0, "right" to 0),
                    "padding" to mapOf("top" to 12, "bottom" to 12, "left" to 12, "right" to 12))),
                mapOf("type" to "text", "text" to "Below", "style" to mapOf("size" to mapOf("height" to 20))),
            ))),
            Products("test", false, null, (1..2).map { Slot(emptyMap(), false, it, "s$it", null) }, null, null),
            null, null, null)
        rule.setContent {
            Column(Modifier.width(240.dp).background(Color.Magenta).testTag("host")) {
                GravityElements(content, campaign, {})
            }
        }
        val above = rule.onNodeWithText("Above").getUnclippedBoundsInRoot()
        val below = rule.onNodeWithText("Below").getUnclippedBoundsInRoot()
        val host = rule.onNodeWithTag("host").getUnclippedBoundsInRoot()
        assertEquals("margin + height + margin between neighbours", 144f, (below.top - above.bottom).value, 0.5f)
        val card = rule.onNodeWithTag("card-s1", useUnmergedTree = true).getBoundsInRoot()
        val rowTop = above.bottom.value + 12f
        val rowBottom = rowTop + 120f
        assertTrue("card clipped to the row box: $card", card.top.value >= rowTop - 0.5f && card.bottom.value <= rowBottom + 0.5f)
        if (cardHeight <= 96) assertEquals("card starts at the top padding", rowTop + 12f, card.top.value, 0.5f)

        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        val pixels = rule.onNodeWithTag("host").captureToImage().toPixelMap()
        val scale = pixels.width / 240f
        fun at(xDp: Float, yDp: Float) = pixels[(xDp * scale).toInt(), ((yDp - host.top.value) * scale).toInt()]
        // Background covers the whole box, including the padding below short cards.
        assertEquals("background at the bottom padding", Color.Yellow, at(200f, rowBottom - 3f))
        assertEquals("background beside the cards", Color.Yellow, at(230f, rowTop + 60f))
        // Nothing of the row draws into the margins.
        assertEquals("top margin stays clear", Color.Magenta, at(30f, rowTop - 3f))
        assertEquals("bottom margin stays clear", Color.Magenta, at(30f, rowBottom + 3f))
    }

    @Test fun shortCardsSitInsidePaddingAndBackgroundFillsTheBox() = exercise(cardHeight = 40)
    @Test fun oversizedCardsAreClippedToTheRowBox() = exercise(cardHeight = 250)
}
