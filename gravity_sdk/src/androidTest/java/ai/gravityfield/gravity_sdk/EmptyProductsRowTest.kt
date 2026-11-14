package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.Variables
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.GravityModalContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A product row or grid with no products takes no space. */
@RunWith(AndroidJUnit4::class)
class EmptyProductsRowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var content by mutableStateOf<CampaignContent?>(null)

    private fun content(containerType: String?, slots: List<Slot>?) = CampaignContent("empty-row", null, DeliveryMethod.MODAL, "test", 1,
        Variables.fromJson(mapOf("elements" to listOf(
            mapOf("type" to "text", "text" to "Header", "style" to mapOf("size" to mapOf("height" to 40))),
            mapOf("type" to "products-container", "style" to mapOf(
                "productContainerType" to containerType,
                "size" to mapOf("height" to 84),
                "margin" to mapOf("left" to 10, "top" to 10, "right" to 10, "bottom" to 10),
                "padding" to mapOf("left" to 8, "top" to 8, "right" to 8, "bottom" to 8),
                "backgroundColor" to "#FF0000",
            ).filterValues { it != null }),
            mapOf("type" to "text", "text" to "Footer", "style" to mapOf("size" to mapOf("height" to 40))),
        ))),
        slots?.let { Products("test", false, null, it, null, null) }, null, null, null)

    /** Distance between the bottom of the header and the top of the footer, in dp. */
    private fun gap(containerType: String?, slots: List<Slot>?): Float {
        rule.runOnUiThread { content = content(containerType, slots) }
        rule.waitForIdle()
        val header = rule.onNodeWithText("Header").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithText("Footer").getUnclippedBoundsInRoot()
        return footer.top.value - header.bottom.value
    }

    @Test fun emptyProductContainersCollapse() {
        rule.runOnUiThread { GravitySDK.initialize(rule.activity, "empty-row-test", "empty-row-test", {}) }
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    content?.let { GravityModalContent(it, Campaign(null, emptyList()), {}, null) }
                }
            }
        }
        val slot = Slot(emptyMap(), false, 1, "slot-1", null)
        for (type in listOf("row", null)) {
            assertEquals("row type=$type slots=[] must take no height", 0f, gap(type, emptyList()), 0.5f)
            assertEquals("row type=$type products absent must take no height", 0f, gap(type, null), 0.5f)
            assertEquals("row type=$type with products keeps height + margin", 104f, gap(type, listOf(slot)), 0.5f)
        }
        assertEquals("grid slots=[] collapses as before", 0f, gap("grid", emptyList()), 0.5f)
        assertEquals("grid products absent must take no height", 0f, gap("grid", null), 0.5f)
    }
}
