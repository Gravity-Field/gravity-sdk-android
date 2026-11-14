package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.RtRule
import ai.gravityfield.gravity_sdk.models.RtRuleArgument
import ai.gravityfield.gravity_sdk.models.RtRuleCondition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooseRulesDeviceWireTest {
    private lateinit var server: RecordingServer
    private val page = PageContext(ContextType.CATEGORY, listOf("shoes"), "/catalog")
    private val wasReportingEnabled = ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled

    @Before fun setUp() {
        ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled = false
        server = RecordingServer().also { it.start() }
        GravitySDK.initialize(InstrumentationRegistry.getInstrumentation().targetContext,
            "rules-key", "rules-section", {})
        GravitySDK.instance.setUser("rules-user", "rules-session")
        GravitySDK.instance.setOptions(null, null, server.url)
    }

    @After fun tearDown() {
        GravitySDK.instance.setOptions(null, null, null)
        server.stop()
        ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled = wasReportingEnabled
    }

    @Test fun priceFilterReachesEveryContentRequestAndNullOmitsIt() = runBlocking {
        val price = listOf(RtRule(
            type = "filter",
            conditions = listOf(RtRuleCondition("price", listOf(
                RtRuleArgument("gte", listOf("1000")),
                RtRuleArgument("lte", listOf("5000")),
            ))),
        ))
        val sdk = GravitySDK.instance
        server.reset()
        sdk.getContentBySelector("recs", page, price)
        sdk.getContentByGroupSelector("recs", page, price)
        sdk.getContentByCampaignId("recs", page, price)
        sdk.getContentBySelector("recs", page)
        sdk.getContentByGroupSelector("recs", page, null)
        sdk.getContentByCampaignId("recs", page)
        val data = server.recorded.filter { it.path.endsWith("/choose") }
            .map { it.body.getJSONArray("data").getJSONObject(0) }
        assertEquals(6, data.size)
        val expected = Json.parseToJsonElement("""[{"id":null,"type":"filter","slots":null,"conditions":[
            {"field":"price","arguments":[{"action":"gte","value":["1000"]},{"action":"lte","value":["5000"]}]}]}]""")
        data.take(3).forEach { assertEquals(expected, Json.parseToJsonElement(it.getJSONArray("rules").toString())) }
        data.drop(3).forEach { assertFalse(it.toString(), it.has("rules")) }
    }
}
