package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.HeadlessRecordingServer
import ai.gravityfield.gravity_sdk.R
import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class InlineRulesTest {
    @Test fun singleViewReloadsRulesWithoutReusingUnqualifiedCache() = checkRules("single")
    @Test fun listViewReloadsRulesWithoutReusingUnqualifiedCache() = checkRules("list")

    @Test fun composeReloadsRules() = checkRules("compose")

    @Test fun singleViewShowsAndReportsContentOfTheNewRules() = checkRuledContent("single")
    @Test fun listViewShowsAndReportsContentOfTheNewRules() = checkRuledContent("list")
    @Test fun composeShowsAndReportsContentOfTheNewRules() = checkRuledContent("compose")
    @Test fun composeLoaderCannotInheritPlacementRules() = checkNestedPlacementWithoutRules()

    private fun ruleTag(body: JSONObject): String {
        val data = body.getJSONArray("data").getJSONObject(0)
        if (!data.has("rules")) return "plain"
        return data.getJSONArray("rules").getJSONObject(0).getJSONArray("conditions").getJSONObject(0)
            .getJSONArray("arguments").getJSONObject(0).getJSONArray("value").getString(0)
    }

    private fun priceRules(min: String) = listOf(RtRule("filter", listOf(
        RtRuleCondition("price", listOf(RtRuleArgument("gte", listOf(min)))),
    )))

    private fun checkRuledContent(kind: String) = runBlocking<Unit> {
        val list = kind == "list"
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val events = CopyOnWriteArrayList<TrackingEvent>()
        val server = HeadlessRecordingServer { path, body ->
            if (path != "/choose") return@HeadlessRecordingServer 200 to "{}"
            bodies += body
            val tag = ruleTag(body)
            val selector = if (list) "" else "\"selector\":\"slot\","
            200 to """{"user":{"uid":"u","ses":"s"},"data":[{$selector"payload":[{"campaignId":"c-$tag","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"content-$tag","contentType":"native","deliveryMethod":"inline","variables":{"elements":[]}}]}]}]}"""
        }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        suspend fun pump() {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            clock.sendFrame(System.nanoTime())
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        val reportingEnabled = Logger.errorReportingEnabled
        Logger.errorReportingEnabled = false
        server.start()
        try {
            GravitySDK.initialize(activity.get(), "inline-rules", "section", { events += it })
            GravitySDK.instance.setOptions(null, null, server.url)
            val parent = FrameLayout(activity.get())
            activity.get().setContentView(parent)
            val attrs = Robolectric.buildAttributeSet()
                .addAttribute(if (list) R.attr.groupSelector else R.attr.selector, "slot").build()
            val page = PageContext(ContextType.OTHER, emptyList(), "inline-rules")
            val selection = androidx.compose.runtime.mutableStateOf<List<RtRule>?>(null)
            val view = if (kind == "compose") androidx.compose.ui.platform.ComposeView(activity.get()).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    GravityInlineCompose(androidx.compose.ui.Modifier, "slot", page, null, rules = selection.value)
                }
            } else if (list) GravityInlineListView(activity.get(), attrs).apply {
                setParentCompositionContext(recomposer); init(page)
            } else GravityInlineView(activity.get(), attrs).apply {
                setParentCompositionContext(recomposer); init(page)
            }
            fun setRules(value: List<RtRule>?) = when (view) {
                is GravityInlineView -> view.setRules(value)
                is GravityInlineListView -> view.setRules(value)
                else -> selection.value = value
            }
            parent.addView(view, ViewGroup.LayoutParams(300, 120))
            activity.visible()
            // The list placement reports loaded content only (it has no impression tracking).
            fun impressions() = if (list) events.filterIsInstance<ContentLoadEvent>().map { it.content.contentId }
                else events.filterIsInstance<ContentImpressionEvent>().map { it.content.contentId }
            suspend fun awaitImpression(contentId: String) = withTimeout(10000) {
                while (contentId !in impressions()) pump()
                while (recomposer.hasPendingWork) pump()
            }
            awaitImpression("content-plain")
            setRules(priceRules("1000"))
            awaitImpression("content-1000")
            setRules(priceRules("5000"))
            awaitImpression("content-5000")
            assertEquals(listOf("plain", "1000", "5000"), bodies.map(::ruleTag))
            assertEquals(listOf("content-plain", "content-1000", "content-5000"), impressions())
        } finally {
            GravitySDK.instance.dispose()
            activity.pause().stop().destroy()
            recomposer.cancel(); scope.cancel(); server.stop()
            Logger.errorReportingEnabled = reportingEnabled
        }
    }

    // A placement composed inside another placement's loader must not pick up the outer rules.
    private fun checkNestedPlacementWithoutRules() = runBlocking<Unit> {
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val innerArrived = java.util.concurrent.CountDownLatch(1)
        val server = HeadlessRecordingServer { path, body ->
            if (path != "/choose") return@HeadlessRecordingServer 200 to "{}"
            bodies += body
            // Keep the outer placement loading, so its loader stays composed until the inner request is sent.
            when (body.getJSONArray("data").getJSONObject(0).getString("selector")) {
                "outer" -> innerArrived.await(10, TimeUnit.SECONDS)
                "inner" -> innerArrived.countDown()
            }
            200 to """{"user":{"uid":"u","ses":"s"},"data":[]}"""
        }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        suspend fun pump() {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            clock.sendFrame(System.nanoTime())
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        val reportingEnabled = Logger.errorReportingEnabled
        Logger.errorReportingEnabled = false
        server.start()
        try {
            GravitySDK.initialize(activity.get(), "inline-rules", "section", {})
            GravitySDK.instance.setOptions(null, null, server.url)
            val page = PageContext(ContextType.OTHER, emptyList(), "inline-rules")
            // An established session lets both placements request concurrently instead of
            // waiting for the outer response to initialize the session.
            GravitySDK.instance.getContentBySelector("prime", page)
            bodies.clear()
            val view = androidx.compose.ui.platform.ComposeView(activity.get()).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    GravityInlineCompose(androidx.compose.ui.Modifier, "outer", page, loader = {
                        GravityInlineCompose(androidx.compose.ui.Modifier, "inner", page, null)
                    }, rules = priceRules("1000"))
                }
            }
            activity.get().setContentView(view, ViewGroup.LayoutParams(300, 120))
            activity.visible()
            withTimeout(10000) { while (bodies.size < 2) pump() }
            val bySelector = bodies.associateBy { it.getJSONArray("data").getJSONObject(0).getString("selector") }
            assertEquals("1000", ruleTag(bySelector.getValue("outer")))
            assertEquals("plain", ruleTag(bySelector.getValue("inner")))
        } finally {
            innerArrived.countDown()
            GravitySDK.instance.dispose()
            activity.pause().stop().destroy()
            recomposer.cancel(); scope.cancel(); server.stop()
            Logger.errorReportingEnabled = reportingEnabled
        }
    }

    private fun checkRules(kind: String) = runBlocking<Unit> {
        val list = kind == "list"
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val server = HeadlessRecordingServer { _, body -> bodies += body; 200 to """{"data":[]}""" }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        suspend fun pump() {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            clock.sendFrame(System.nanoTime())
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        val reportingEnabled = Logger.errorReportingEnabled
        Logger.errorReportingEnabled = false
        server.start()
        try {
            GravitySDK.initialize(activity.get(), "inline-rules", "section", {})
            GravitySDK.instance.setOptions(null, null, server.url)
            val parent = FrameLayout(activity.get())
            activity.get().setContentView(parent)
            val attrs = Robolectric.buildAttributeSet()
                .addAttribute(if (list) R.attr.groupSelector else R.attr.selector, "\"slot\"").build()
            val page = PageContext(ContextType.OTHER, emptyList(), "inline-rules")
            val selection = androidx.compose.runtime.mutableStateOf<List<RtRule>?>(null)
            val view = if (kind == "compose") androidx.compose.ui.platform.ComposeView(activity.get()).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    GravityInlineCompose(androidx.compose.ui.Modifier, "\"slot\"", page, null, rules = selection.value)
                }
            } else if (list) GravityInlineListView(activity.get(), attrs).apply {
                setParentCompositionContext(recomposer); init(page)
            } else GravityInlineView(activity.get(), attrs).apply {
                setParentCompositionContext(recomposer); init(page)
            }
            val setter = view.javaClass.methods.firstOrNull { it.name == "setRules" }
            if (kind != "compose") assertNotNull("Inline placements must expose setRules", setter)
            fun setRules(value: List<RtRule>?) {
                if (kind == "compose") selection.value = value else setter!!.invoke(view, value)
            }
            parent.addView(view, ViewGroup.LayoutParams(300, 120))
            activity.visible()
            suspend fun awaitCount(count: Int) = withTimeout(10000) {
                while (bodies.size < count) pump()
                // Wait for delivery to main before changing the placement inputs.
                while (recomposer.hasPendingWork) pump()
            }
            awaitCount(1)
            assertFalse(bodies[0].getJSONArray("data").getJSONObject(0).has("rules"))
            val rules = listOf(RtRule("recommend", emptyList(), id = 7))
            setRules(rules)
            awaitCount(2)
            assertEquals(7, bodies[1].getJSONArray("data").getJSONObject(0)
                .getJSONArray("rules").getJSONObject(0).getInt("id"))
            setRules(emptyList())
            awaitCount(3)
            assertEquals(0, bodies[2].getJSONArray("data").getJSONObject(0).getJSONArray("rules").length())
            setRules(null)
            if (kind == "compose") awaitCount(4)
            // The original unqualified response may be reused, but ruled responses cannot replace it.
            withTimeout(10000) { while (recomposer.hasPendingWork) pump() }
            assertTrue(bodies.drop(3).all { !it.getJSONArray("data").getJSONObject(0).has("rules") })
        } finally {
            GravitySDK.instance.dispose()
            activity.pause().stop().destroy()
            recomposer.cancel(); scope.cancel(); server.stop()
            Logger.errorReportingEnabled = reportingEnabled
        }
    }
}
