package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.Variables
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.GravityBottomSheetContent
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductsGridSpacerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun exercise(sheet: Boolean) {
        rule.runOnUiThread { GravitySDK.initialize(rule.activity, "spacer-test", "spacer-test", {}) }
        val content = CampaignContent("grid-spacer", null, DeliveryMethod.MODAL, "test", 1,
            Variables.fromJson(mapOf("elements" to listOf(
                mapOf("type" to "products-container", "style" to mapOf("productContainerType" to "grid")),
                mapOf("type" to "spacer", "style" to mapOf("weight" to 1)),
                mapOf("type" to "text", "text" to "Footer", "style" to mapOf("size" to mapOf("height" to 40)))
            ))), Products("test", false, null, (1..2).map { Slot(emptyMap(), false, it, "slot-$it", null) }, null, null),
            null, null, null)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 240.dp, height = 480.dp).testTag("viewport")) {
                    if (sheet) GravityBottomSheetContent(content, Campaign(null, emptyList()), {}, null)
                    else GravityModalContent(content, Campaign(null, emptyList()), {}, null)
                }
            }
        }
        val viewport = rule.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithText("Footer").getUnclippedBoundsInRoot()
        assertEquals("The weighted spacer must keep the footer at the viewport bottom", viewport.bottom.value,
            footer.bottom.value, 0.5f)
    }

    @Test fun weightedSpacerFillsGridModalViewport() = exercise(sheet = false)
    @Test fun weightedSpacerFillsGridSheetViewport() = exercise(sheet = true)
}
