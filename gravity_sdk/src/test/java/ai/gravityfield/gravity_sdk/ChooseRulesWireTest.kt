package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import ai.gravityfield.gravity_sdk.network.ContentResponse
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChooseRulesWireTest {
    private val priceRule = RtRule(
        type = "filter",
        conditions = listOf(
            RtRuleCondition(
                field = "price",
                arguments = listOf(
                    RtRuleArgument(action = "gte", value = listOf("1000")),
                    RtRuleArgument(action = "lte", value = listOf("5000")),
                ),
            ),
        ),
        id = 3,
        slots = listOf(0, 2),
    )

    // Flutter's json_serializable output for the same rule: nullable id/slots are always present.
    private val priceRuleJson = """
        {"id":3,"type":"filter","slots":[0,2],"conditions":[
          {"field":"price","arguments":[{"action":"gte","value":["1000"]},{"action":"lte","value":["5000"]}]}
        ]}
    """

    private fun assertJson(expected: String, actual: String) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))

    private fun withServer(
        respond: (JSONObject) -> String = { """{"data":[]}""" },
        block: suspend (GravitySDK, CopyOnWriteArrayList<JSONObject>, CopyOnWriteArrayList<String>) -> Unit,
    ) = runBlocking<Unit> {
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val raw = CopyOnWriteArrayList<String>()
        val server = HeadlessRecordingServer { path, body ->
            if (path == "/choose") bodies += body
            200 to respond(body)
        }
        server.observeRawBody = { raw += it }
        val reportingEnabled = Logger.errorReportingEnabled
        Logger.errorReportingEnabled = false
        server.start()
        try {
            GravitySDK.initialize(RuntimeEnvironment.getApplication(), "rules-key", "rules-section", {})
            val sdk = GravitySDK.instance
            sdk.setOptions(null, null, server.url)
            block(sdk, bodies, raw)
        } finally {
            GravitySDK.instance.dispose()
            server.stop()
            Logger.errorReportingEnabled = reportingEnabled
        }
    }

    @Test fun everyContentGetterEmitsFlutterRulesShapeAndPreservesOptions() = runBlocking<Unit> {
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val server = HeadlessRecordingServer { _, body -> bodies += body; 200 to """{"data":[]}""" }
        val reportingEnabled = Logger.errorReportingEnabled
        Logger.errorReportingEnabled = false
        server.start()
        try {
            GravitySDK.initialize(RuntimeEnvironment.getApplication(), "rules-key", "rules-section", {})
            val sdk = GravitySDK.instance
            sdk.setOptions(Options(isImplicitImpression = false, isReturnUserInfo = true), ContentSettings(skusOnly = true, fields = listOf("name")), server.url)
            val page = PageContext(ContextType.OTHER, emptyList(), "rules")
            val rule = RtRule(id = 1, type = "t", slots = listOf(0), conditions = listOf(
                RtRuleCondition("price", listOf(RtRuleArgument("eq", listOf("10", "20")))),
            ))
            val getters: List<suspend (List<RtRule>?) -> Any?> = listOf(
                { sdk.getContentBySelector("101", page, it) },
                { sdk.getContentBySelectorWithDetails("101", page, it) },
                { sdk.getContentByCampaignId("101", page, it) },
                { sdk.getContentByCampaignIdWithDetails("101", page, it) },
                { sdk.getContentByGroupSelector("101", page, it) },
            )
            for (get in getters) {
                for (rules in listOf(null, emptyList(), listOf(rule), listOf(RtRule("t", emptyList())))) {
                    assertNotNull(get(rules))
                    val body = bodies.last()
                    val data = body.getJSONArray("data").getJSONObject(0)
                    assertEquals(rules != null, data.has("rules"))
                    if (rules != null) {
                        val actual = data.getJSONArray("rules")
                        assertEquals(rules.size, actual.length())
                        if (rules.isNotEmpty()) {
                            val expected = if (rules[0].id == null)
                                """{"id":null,"type":"t","slots":null,"conditions":[]}"""
                            else """{"id":1,"type":"t","slots":[0],"conditions":[{"field":"price","arguments":[{"action":"eq","value":["10","20"]}]}]}"""
                            assertJson(expected, actual.getJSONObject(0).toString())
                        }
                    }
                    assertTrue(data.getJSONObject("option").getBoolean("skusOnly"))
                    assertEquals("name", data.getJSONObject("option").getJSONArray("fields").getString(0))
                    assertFalse(body.getJSONObject("options").getBoolean("isImplicitImpression"))
                    assertTrue(body.getJSONObject("options").getBoolean("isReturnUserInfo"))
                }
            }
            assertEquals(20, bodies.size)
            // Original overloads remain usable and omit the key too.
            assertNotNull(sdk.getContentBySelector("101", page))
            assertFalse(bodies.last().getJSONArray("data").getJSONObject(0).has("rules"))
        } finally {
            GravitySDK.instance.dispose()
            server.stop()
            Logger.errorReportingEnabled = reportingEnabled
        }
    }

    @Test fun priceFilterIsTheOnlyAdditionToEachDataEntry() = withServer { sdk, bodies, _ ->
        val page = PageContext(ContextType.CATEGORY, listOf("shoes"), "catalog")
        val entryPoints: List<Triple<String, String, suspend () -> Any?>> = listOf(
            Triple("getContentBySelector", "selector") { sdk.getContentBySelector("recs", page, listOf(priceRule)) },
            Triple("getContentBySelectorWithDetails", "selector") { sdk.getContentBySelectorWithDetails("recs", page, listOf(priceRule)) },
            Triple("getContentByCampaignId", "campaignId") { sdk.getContentByCampaignId("recs", page, listOf(priceRule)) },
            Triple("getContentByCampaignIdWithDetails", "campaignId") { sdk.getContentByCampaignIdWithDetails("recs", page, listOf(priceRule)) },
            Triple("getContentByGroupSelector", "group") { sdk.getContentByGroupSelector("recs", page, listOf(priceRule)) },
        )
        for ((name, identifier, call) in entryPoints) {
            val before = bodies.size
            assertNotNull(name, call())
            assertEquals(name, before + 1, bodies.size)
            val body = bodies.last()
            val dataArray = body.getJSONArray("data")
            assertEquals(name, 1, dataArray.length())
            val data = dataArray.getJSONObject(0)
            assertEquals(name, setOf(identifier, "option", "rules"), data.keys().asSequence().toSet())
            assertEquals(name, "recs", data.getString(identifier))
            assertJson("[$priceRuleJson]", data.getJSONArray("rules").toString())
            // Rules belong to the data entry only; the envelope keeps its usual keys.
            assertEquals(name, setOf("sec", "data", "device", "user", "ctx", "options"), body.keys().asSequence().toSet())
        }
    }

    @Test fun nullRulesNeverSendTheKeyFromAnyEntryPoint() = withServer { sdk, _, raw ->
        val page = PageContext(ContextType.OTHER, emptyList(), "no-rules")
        sdk.getContentBySelector("recs", page)
        sdk.getContentBySelector("recs", page, null)
        sdk.getContentBySelectorWithDetails("recs", page)
        sdk.getContentBySelectorWithDetails("recs", page, null)
        sdk.getContentByCampaignId("recs", page)
        sdk.getContentByCampaignId("recs", page, null)
        sdk.getContentByCampaignIdWithDetails("recs", page)
        sdk.getContentByCampaignIdWithDetails("recs", page, null)
        sdk.getContentByGroupSelector("recs", page)
        sdk.getContentByGroupSelector("recs", page, null)
        val chooseBodies = raw.filter { it.contains("\"data\"") }
        assertEquals(10, chooseBodies.size)
        chooseBodies.forEach { assertFalse(it, Regex("\"rules\"\\s*:").containsMatchIn(it)) }
    }

    @Test fun concurrentCallsWithDifferentRulesEachGetTheirOwnResult() {
        // Once a session exists, every /choose is held until its sibling also reached the server,
        // so neither call can be served from the other's request or response.
        val barrier = AtomicReference<CyclicBarrier?>(null)
        withServer(respond = { body ->
            barrier.get()?.await(5, TimeUnit.SECONDS)
            val tag = minPrice(body.getJSONArray("data").getJSONObject(0))
            """{"user":{"uid":"u","ses":"s"},"data":[{"selector":"recs","payload":[{"campaignId":"c-$tag","experienceId":"e","variationId":"v","decisionId":"d","contents":[{"contentId":"content-$tag","contentType":"native","deliveryMethod":"inline"}]}]}]}"""
        }) { sdk, bodies, _ ->
            val page = PageContext(ContextType.CATEGORY, listOf("shoes"), "catalog")
            fun price(min: String) = listOf(priceRule.copy(conditions = listOf(
                RtRuleCondition("price", listOf(RtRuleArgument("gte", listOf(min)))),
            )))
            fun contentId(result: Any?): String? = when (result) {
                is ContentResponse -> result.data.single().payload.single().contents.single().contentId
                is GravityDataResponse<*> -> contentId(result.data)
                else -> null
            }
            val calls: List<Pair<String, suspend (List<RtRule>?) -> Any?>> = listOf(
                "selector" to { r -> sdk.getContentBySelector("recs", page, r) },
                "selectorDetails" to { r -> sdk.getContentBySelectorWithDetails("recs", page, r) },
                "campaign" to { r -> sdk.getContentByCampaignId("recs", page, r) },
                "campaignDetails" to { r -> sdk.getContentByCampaignIdWithDetails("recs", page, r) },
                "group" to { r -> sdk.getContentByGroupSelector("recs", page, r) },
            )
            suspend fun race(name: String, call: suspend (List<RtRule>?) -> Any?) = coroutineScope {
                val before = bodies.size
                val gate = CompletableDeferred<Unit>()
                val cheap = async(Dispatchers.IO) { gate.await(); call(price("100")) }
                val expensive = async(Dispatchers.IO) { gate.await(); call(price("9000")) }
                val plain = async(Dispatchers.IO) { gate.await(); call(null) }
                gate.complete(Unit)
                withTimeout(10_000) {
                    assertEquals(name, "content-100", contentId(cheap.await()))
                    assertEquals(name, "content-9000", contentId(expensive.await()))
                    assertEquals(name, "content-none", contentId(plain.await()))
                }
                assertEquals(name, before + 3, bodies.size)
                assertEquals(name, setOf("100", "9000", "none"),
                    bodies.drop(before).map { minPrice(it.getJSONArray("data").getJSONObject(0)) }.toSet())
            }
            // Usually no session exists yet: the calls share session initialization but still send their own rules.
            race("first-session") { r -> sdk.getContentBySelector("recs", page, r) }
            barrier.set(CyclicBarrier(3))
            for ((name, call) in calls) race(name, call)
        }
    }

    private fun minPrice(data: JSONObject): String =
        if (!data.has("rules")) "none" else data.getJSONArray("rules").getJSONObject(0)
            .getJSONArray("conditions").getJSONObject(0).getJSONArray("arguments")
            .getJSONObject(0).getJSONArray("value").getString(0)
}
