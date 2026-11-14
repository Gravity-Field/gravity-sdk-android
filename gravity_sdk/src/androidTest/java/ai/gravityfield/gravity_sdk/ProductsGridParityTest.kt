package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.ProductsGrid
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import androidx.activity.ComponentActivity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ProductsGridParityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val campaign = Campaign(null, emptyList())
    private val impressions = CopyOnWriteArrayList<String?>()

    private fun initialize(withBuilder: Boolean = true) = rule.runOnUiThread {
        rule.activity.actionBar?.hide()
        GravitySDK.initialize(rule.activity, "grid-parity", "grid-parity", { event ->
            if (event is ProductImpressionEvent) impressions.add(event.slot.slotId)
        }, productViewBuilder = if (!withBuilder) null else object : ProductViewBuilder {
            @Composable override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
                Box(Modifier.fillMaxSize().testTag("product-${slot.strId}").background(Color.Blue))
            }
        })
    }

    private fun content(slots: List<Slot>?, extraStyle: Map<String, Any?> = emptyMap()): CampaignContent =
        CampaignContent("parity", null, DeliveryMethod.INLINE, "test", 1,
            Variables.fromJson(mapOf("elements" to listOf(mapOf("type" to "products-container",
                "style" to (mapOf("productContainerType" to "grid") + extraStyle))))),
            Products("test", false, null, slots, null, null), null, null, null)

    private fun slots() = listOf(
        Slot(emptyMap(), false, 1, "slot-1", null),
        Slot(emptyMap(), false, 2, "slot-2", null),
    )

    @Test fun gridIgnoresMarginBackgroundAndCardDimensionsButKeepsPadding() {
        initialize()
        val content = content(slots(), mapOf(
            "size" to mapOf("width" to 400, "height" to 100),
            "margin" to mapOf("left" to 16, "right" to 16, "top" to 16, "bottom" to 16),
            "padding" to mapOf("left" to 5, "right" to 5, "top" to 5, "bottom" to 5),
            "backgroundColor" to "#ffff00",
        ))
        rule.setContent {
            Column(Modifier.width(240.dp).background(Color.Magenta).testTag("host")) {
                ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
            }
        }
        val host = rule.onNodeWithTag("host").getUnclippedBoundsInRoot()
        val first = rule.onNodeWithTag("product-1").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("product-2").getUnclippedBoundsInRoot()
        assertEquals(5f, (first.left - host.left).value, 0.5f)
        assertEquals(5f, (first.top - host.top).value, 0.5f)
        assertEquals((first.right - first.left).value, (first.bottom - first.top).value, 0.5f)
        assertEquals(8f, (second.left - first.right).value, 0.5f)
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        assertEquals(Color.Magenta, rule.onNodeWithTag("host").captureToImage().toPixelMap()[1, 1])
    }

    @Test fun absentOrEmptyProductsGridTakesNoSpace() {
        initialize()
        var mode by mutableIntStateOf(0)
        val base = content(null, mapOf("size" to mapOf("height" to 173),
            "padding" to mapOf("top" to 5, "bottom" to 5),
            "margin" to mapOf("top" to 7, "bottom" to 7),
            "backgroundColor" to "#FF0000"))
        rule.setContent {
            val current = when (mode) {
                0 -> base.copy(products = null)
                1 -> base
                else -> base.copy(products = base.products!!.copy(slots = emptyList()))
            }
            Column(Modifier.width(240.dp).testTag("host")) {
                GravityElements(current, campaign, {})
                Text("Footer", Modifier.testTag("footer"))
            }
        }
        for (value in 0..2) {
            rule.runOnIdle { mode = value }
            val host = rule.onNodeWithTag("host").getUnclippedBoundsInRoot()
            val footer = rule.onNodeWithTag("footer").getUnclippedBoundsInRoot()
            assertEquals("mode=$value", 0f, (footer.top - host.top).value, 0.5f)
        }
    }

    @Test fun nonFiniteGridHeightWithoutProductsStillTakesNoSpace() = nonFiniteDimension("placeholder")
    @Test fun nonFiniteSizeActsAsAbsent() = nonFiniteDimension("size")
    @Test fun nonFinitePaddingActsAsAbsent() = nonFiniteDimension("padding")
    @Test fun nonFiniteMarginActsAsAbsent() = nonFiniteDimension("margin")
    @Test fun nonFiniteCornerRadiusRenders() = nonFiniteDimension("cornerRadius")

    private fun nonFiniteDimension(field: String) {
        initialize()
        var value by mutableStateOf<Any?>(null)
        rule.setContent {
            val style = when (field) {
                "placeholder", "size" -> mapOf("size" to mapOf("width" to value, "height" to value))
                "padding", "margin" -> mapOf(field to mapOf("top" to value, "bottom" to value, "left" to value, "right" to value))
                else -> mapOf(field to value)
            }
            val current = if (field == "placeholder") content(null, style).copy(products = null)
            else content(null).copy(variables = Variables.fromJson(mapOf("elements" to listOf(
                mapOf("type" to "button", "text" to "Button", "style" to style)))))
            Column(Modifier.width(240.dp).testTag("dimension-host")) {
                GravityElements(current, campaign, {})
                Text("Footer", Modifier.testTag("dimension-footer"))
            }
        }
        val baseline = rule.onNodeWithTag("dimension-footer").getUnclippedBoundsInRoot()
        for (invalid in listOf("Infinity", "-Infinity", "NaN", "1e400", "Infinitypx",
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            rule.runOnIdle { value = invalid }
            val footer = rule.onNodeWithTag("dimension-footer").getUnclippedBoundsInRoot()
            assertEquals("$field=$invalid", baseline, footer)
            rule.onNodeWithTag("dimension-footer").assertIsDisplayed()
        }
    }

    @Test fun onlyIdentifiedSlotsNotifyAndViewportMustExposeAtLeastHalfTheCell() {
        initialize()
        var height by mutableIntStateOf(45)
        val content = content(listOf(
            Slot(emptyMap(), false, 1, null, null),
            Slot(emptyMap(), false, 2, "slot-2", null),
        ))
        rule.setContent {
            Box(Modifier.width(240.dp).height(height.dp).clipToBounds()) {
                // Keep the grid's full natural height even inside a short viewport.
                Column(Modifier.wrapContentHeight(unbounded = true, align = androidx.compose.ui.Alignment.Top)) {
                    ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnIdle { assertTrue("Less than half visible", impressions.isEmpty()) }
        rule.runOnIdle { height = 80 }
        rule.waitUntil(5000) { impressions.contains("slot-2") }
        rule.runOnIdle { assertEquals(listOf("slot-2"), impressions.toList()) }
    }

    @Test fun embeddedViewUsesWindowPositionAndNativeAncestorClipping() {
        initialize()
        lateinit var viewport: android.widget.FrameLayout
        val content = content(slots())
        rule.runOnUiThread {
            fun px(dp: Int) = (dp * rule.activity.resources.displayMetrics.density).toInt()
            val root = android.widget.FrameLayout(rule.activity)
            viewport = android.widget.FrameLayout(rule.activity).apply { clipChildren = true }
            root.addView(viewport, android.widget.FrameLayout.LayoutParams(px(240), px(45)).apply {
                topMargin = px(240)
            })
            val compose = androidx.compose.ui.platform.ComposeView(rule.activity)
            viewport.addView(compose, android.widget.FrameLayout.LayoutParams(px(240), px(200)))
            compose.setContent {
                ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
            }
            rule.activity.setContentView(root)
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnIdle { assertTrue("Native parent clips below half", impressions.isEmpty()) }
        rule.runOnUiThread {
            viewport.layoutParams = viewport.layoutParams.apply {
                height = (80 * rule.activity.resources.displayMetrics.density).toInt()
            }
        }
        rule.waitUntil(5000) { impressions.containsAll(listOf("slot-1", "slot-2")) }
    }

    private fun inlineNativeHostScrolls(list: Boolean) {
        initialize()
        val page = PageContext(ContextType.OTHER, emptyList(), "/grid")
        val base = content((1..40).map { Slot(emptyMap(), false, it, "slot-$it", null) })
        val content = base.copy(variables = base.variables!!.copy(frameUI = FrameUI.fromJson(mapOf(
            "container" to mapOf("style" to mapOf("size" to mapOf("width" to 240, "height" to 180)))
        ))))
        val response = ai.gravityfield.gravity_sdk.network.ContentResponse(null, listOf(
            Campaign("grid", listOf(CampaignVariation("1", "1", "1", "1", listOf(content))))))
        val field = GravitySDK::class.java.getDeclaredField(if (list) "inlineListViewCache" else "inlineViewCache")
        field.isAccessible = true
        (field.get(GravitySDK.instance) as ai.gravityfield.gravity_sdk.models.internal.InlineViewCacheStore)
            .put("grid", page, ai.gravityfield.gravity_sdk.models.internal.InlineViewCache(response))
        rule.runOnUiThread {
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val resource = testContext.resources.getIdentifier(
                if (list) "grid_inline_list" else "grid_inline", "layout", testContext.packageName)
            val parser = testContext.resources.getLayout(resource)
            val view = rule.activity.layoutInflater.inflate(parser, null)
            parser.close()
            if (list) (view as ai.gravityfield.gravity_sdk.ui.GravityInlineListView).init(page)
            else (view as ai.gravityfield.gravity_sdk.ui.GravityInlineView).init(page)
            rule.activity.setContentView(view, android.view.ViewGroup.LayoutParams(
                (240 * rule.activity.resources.displayMetrics.density).toInt(),
                (180 * rule.activity.resources.displayMetrics.density).toInt()))
        }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("product-40").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("product-40").performScrollTo().assertIsDisplayed()
        rule.waitUntil(5000) { impressions.contains("slot-40") }
    }

    @Test fun fixedHeightInlineViewScrollsToLastRow() = inlineNativeHostScrolls(false)
    @Test fun fixedHeightInlineListViewScrollsToLastRow() = inlineNativeHostScrolls(true)

    private fun inlineComposeScrolls(fixedHeight: Boolean) {
        initialize()
        val server = RecordingServer()
        val frame = if (fixedHeight) mapOf("container" to mapOf("style" to
            mapOf("size" to mapOf("height" to 180)))) else mapOf("container" to emptyMap<String, Any>())
        val rawContent = mapOf(
            "contentId" to "inline-compose", "deliveryMethod" to "inline", "contentType" to "test", "step" to 1,
            "variables" to mapOf("frameUI" to frame, "elements" to listOf(mapOf(
                "type" to "products-container", "style" to mapOf("productContainerType" to "grid")))),
            "products" to mapOf("strategyId" to "test", "fallback" to false, "slots" to (1..40).map {
                mapOf("item" to emptyMap<String, Any>(), "fallback" to false, "strId" to it, "slotId" to "slot-$it")
            }))
        server.chooseData = org.json.JSONArray(listOf(mapOf("selector" to "grid", "payload" to listOf(mapOf(
            "campaignId" to "1", "experienceId" to "1", "variationId" to "1", "decisionId" to "1",
            "contents" to listOf(rawContent))))))
        server.start()
        GravitySDK.instance.setOptions(null, null, server.url)
        try {
            rule.setContent {
                val parentScroll = androidx.compose.foundation.rememberScrollState()
                Column(Modifier.width(240.dp).height(300.dp).then(
                    if (fixedHeight) Modifier else Modifier.verticalScroll(parentScroll))) {
                    ai.gravityfield.gravity_sdk.ui.GravityInlineCompose(Modifier.fillMaxWidth(), "grid",
                        PageContext(ContextType.OTHER, emptyList(), "/grid"), null)
                }
            }
            rule.waitUntil(5000) { rule.onAllNodesWithTag("product-40").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("product-40").performScrollTo().assertIsDisplayed()
            rule.waitUntil(5000) { impressions.contains("slot-40") }
        } finally {
            GravitySDK.instance.setOptions(null, null, null)
            server.stop()
        }
    }

    @Test fun fixedHeightInlineComposeScrollsToLastRow() = inlineComposeScrolls(true)
    @Test fun naturalHeightInlineComposeWorksInsideScrollingParent() = inlineComposeScrolls(false)

    @Test fun missingBuilderDoesNotReportProductImpressions() {
        initialize(withBuilder = false)
        val content = content(slots())
        rule.setContent {
            Box(Modifier.width(240.dp)) {
                ProductsGrid(content.variables!!.elements!!.first(), content.products!!.slots!!, content, campaign)
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnIdle { assertTrue(impressions.isEmpty()) }
    }
}
