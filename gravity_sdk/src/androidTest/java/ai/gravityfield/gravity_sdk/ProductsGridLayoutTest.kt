package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.*
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import ai.gravityfield.gravity_sdk.utils.ModalImpressionSession
import ai.gravityfield.gravity_sdk.utils.ProductEventService
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class ProductsGridLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val campaign = Campaign(null, emptyList())
    private val impressions = CopyOnWriteArrayList<String?>()
    private var clicks = 0

    private fun initialize() = rule.runOnUiThread {
        rule.activity.actionBar?.hide()
        GravitySDK.initialize(rule.activity, "grid-test-key", "grid-test-section", { event ->
            when (event) {
                is ProductImpressionEvent -> impressions.add(event.slot.slotId)
                is ProductClickEvent -> clicks++
                else -> Unit
            }
        }, productViewBuilder = object : ProductViewBuilder {
            @Composable override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
                Box(Modifier.fillMaxSize().testTag("product-${slot.strId}")
                    .background(Color(0xffdcecff)).clickable {
                        ProductEventService.instance.sendProductClick(slot, content, campaign)
                    }) { Text("Product ${slot.strId}", Modifier.padding(4.dp)) }
            }
        })
    }

    private fun fixture(number: Int, count: Int): CampaignContent {
        val style = mutableMapOf<String, Any?>(
            "backgroundColor" to "#fffff000", "productContainerType" to "grid",
            "size" to mapOf("width" to 500, "height" to if (number == 4) 400 else 250), "rowSpacing" to 8,
            "margin" to mapOf("left" to 16, "right" to 16, "top" to 16, "bottom" to 16))
        if (number >= 2) style["gridSettings"] = mapOf("columns" to 4, "horizontalSpacing" to 6, "verticalSpacing" to 6)
        if (number >= 3) style["padding"] = mapOf("left" to 5, "right" to 5, "top" to 5, "bottom" to 5)
        return CampaignContent("grid-$count", null, DeliveryMethod.BOTTOM_SHEET, "test", 1,
            Variables.fromJson(mapOf("elements" to listOf(mapOf("type" to "products-container", "style" to style)))),
            Products("strategy", false, null, (1..count).map { Slot(emptyMap(), false, it, "slot-$it", null) }, null, null),
            null, null, null)
    }

    private var presentedContentId: String? = null

    private fun framedFixture(number: Int, count: Int, mode: String): CampaignContent {
        val fixture = fixture(number, count)
        return fixture.copy(contentId = "grid-$mode-$count-F$number",
            deliveryMethod = when (mode) {
                "sheet" -> DeliveryMethod.BOTTOM_SHEET
                "modal" -> DeliveryMethod.MODAL
                else -> DeliveryMethod.FULL_SCREEN
            },
            variables = fixture.variables!!.copy(frameUI = FrameUI.fromJson(mapOf(
                "container" to emptyMap<String, Any>(),
                "close" to mapOf("onClick" to mapOf("action" to "close"),
                    "style" to mapOf("positioned" to mapOf("top" to 0, "right" to 0)))
            ))))
    }

    private fun showSdkContent(mode: String, content: CampaignContent) {
        rule.runOnUiThread {
            // The content's delivery method selects the sheet, modal or fullscreen presentation.
            val method = GravitySDK::class.java.getDeclaredMethod("showBackendContent", Activity::class.java,
                CampaignContent::class.java, Campaign::class.java, Item::class.java, ModalImpressionSession::class.java)
            method.isAccessible = true
            method.invoke(GravitySDK.instance, rule.activity, content, campaign, null, ModalImpressionSession { true })
            presentedContentId = content.contentId
        }
    }

    @After fun dismissSdkContent() {
        val id = presentedContentId ?: return
        rule.runOnUiThread { GravitySDK.instance.dismissInApp(id) }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTagPrefix("product-")).fetchSemanticsNodes().isEmpty() }
        presentedContentId = null
    }

    private fun settleWindow(count: Int) {
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTagPrefix("product-")).fetchSemanticsNodes().size == count }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
    }

    private fun closeButton() = rule.onNode(hasClickAction() and !hasTestTagPrefix("product-") and
        !SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))

    private fun exercise(mode: String, count: Int) {
        initialize()
        var fixtureNumber by mutableIntStateOf(1)
        rule.setContent {
            MaterialTheme {
                if (mode == "inline") key(fixtureNumber) {
                    val content = fixture(fixtureNumber, count)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
                    }
                }
            }
        }
        var previousBounds: Pair<androidx.compose.ui.unit.DpRect, androidx.compose.ui.unit.DpRect>? = null
        for (number in 1..4) {
            if (mode == "inline") rule.runOnIdle { fixtureNumber = number }
            else showSdkContent(mode, framedFixture(number, count, mode))
            settleWindow(count)
            rule.onAllNodes(hasTestTagPrefix("product-")).assertCountEquals(count)
            val first = rule.onNodeWithTag("product-1").getUnclippedBoundsInRoot()
            val second = rule.onNodeWithTag("product-2").getUnclippedBoundsInRoot()
            val columns = if (number == 1) 2 else 4
            val nextRow = rule.onNodeWithTag("product-${columns + 1}").getUnclippedBoundsInRoot()
            assertEquals(first.top.value, second.top.value, 0.5f)
            assertEquals((first.right - first.left).value, (first.bottom - first.top).value, 0.5f)
            val spacing = if (number == 1) 8f else 6f
            assertEquals(spacing, second.left.value - first.right.value, 0.5f)
            assertEquals(spacing, nextRow.top.value - first.bottom.value, 0.5f)
            if (mode == "inline") assertEquals(if (number >= 3) 5f else 0f, first.left.value, 0.5f)
            if (mode == "sheet") {
                val sheet = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
                    .getUnclippedBoundsInRoot()
                val topInset = rule.runOnIdle {
                    with(rule.density) {
                        ViewCompat.getRootWindowInsets(rule.activity.window.decorView)!!
                            .getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top.toDp()
                    }
                }
                assertEquals("F$number: no extra gap inside the production sheet", if (number >= 3) 5f else 0f,
                    first.top.value - sheet.top.value, 0.5f)
                assertTrue("Sheet must clear the status bar", sheet.top >= topInset)
            }
            val last = rule.onNodeWithTag("product-$count").getUnclippedBoundsInRoot()
            val rows = (count + columns - 1) / columns
            assertEquals((rows - 1) * (nextRow.top - first.top).value,
                (last.top - first.top).value, 1f)
            if (number == 4) assertEquals(previousBounds, first to last)
            previousBounds = first to last
            val close = if (mode != "inline") closeButton() else null
            val closeBounds = close?.getUnclippedBoundsInRoot()
            screenshot("$mode-$count-F$number-top")
            rule.onNodeWithTag("product-$count").performScrollTo().assertIsDisplayed()
            close?.assertIsDisplayed()
            if (close != null) assertEquals(closeBounds, close.getUnclippedBoundsInRoot())
            screenshot("$mode-$count-F$number-bottom")
            if (mode != "inline") dismissSdkContent()
        }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("product tag") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        rule.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        // Reject a blank/fading window instead of silently saving invalid visual evidence.
        assertTrue("Screenshot $name must contain product cells", (0 until bitmap.height step 8).any { y ->
            (0 until bitmap.width step 8).any { x -> bitmap.getPixel(x, y) == 0xffdcecff.toInt() }
        })
        val png = ByteArrayOutputStream().use { output ->
            assertTrue("PNG encoding must succeed", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size))
        decoded.recycle()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/products-grid")
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { it.write(png) }
        val saved = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
        assertArrayEquals("Stored PNG must match the encoded image", png, saved)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }

    @Test fun inlineFiveProducts() = exercise("inline", 5)
    @Test fun inlineFortyProducts() = exercise("inline", 40)
    @Test fun bottomSheetFiveProducts() = exercise("sheet", 5)
    @Test fun bottomSheetFortyProducts() = exercise("sheet", 40)
    @Test fun fullscreenFortyProducts() = exercise("fullscreen", 40)
    @Test fun modalFortyProducts() = exercise("modal", 40)

    @Test fun productClicksAndVisibilityDoNotDuplicateAfterRecompositionOrScroll() {
        initialize()
        var revision by mutableIntStateOf(0)
        val baseContent = fixture(2, 40)
        rule.setContent {
            val element = baseContent.variables!!.elements!!.first()
            val content = baseContent.copy(variables = baseContent.variables.copy(elements = listOf(
                element.copy(style = element.style.copy(gridSettings = element.style.gridSettings!!.copy(
                    columns = if (revision == 0) 4 else 5))))))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("Revision $revision")
                ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
            }
        }
        rule.waitUntil(5000) { impressions.contains("slot-1") && impressions.contains("slot-5") }
        rule.onNodeWithTag("product-1").performClick()
        rule.runOnIdle { revision++ }
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnIdle { assertEquals(1, impressions.count { it == "slot-5" }) }
        rule.onNodeWithTag("product-40").performScrollTo().assertIsDisplayed()
        rule.waitUntil(5000) { impressions.contains("slot-40") }
        rule.onNodeWithTag("product-1").performScrollTo().assertIsDisplayed()
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, impressions.count { it == "slot-1" })
            assertEquals(1, impressions.count { it == "slot-40" })
        }
    }
}
