package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChooseIdentifierJvmWireTest {
    private val rawBodies = CopyOnWriteArrayList<String>()
    private val bodies = CopyOnWriteArrayList<JSONObject>()
    private lateinit var server: HeadlessRecordingServer
    private lateinit var sdk: GravitySDK
    private val reporting = Logger.errorReportingEnabled
    private val page = PageContext(ContextType.OTHER, emptyList(), "/choose-identifiers")
    @Before fun setup() = runBlocking {
        Logger.errorReportingEnabled = false
        server = HeadlessRecordingServer { path, body ->
            assertEquals("/choose", path)
            bodies += body
            200 to """{"data":[]}"""
        }.also { it.observeRawBody = rawBodies::add; it.start() }
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "key", "section", {})
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, server.url)
        sdk.restoreUserId("uid")
    }
    @After fun cleanup() { sdk.dispose(); server.stop(); Logger.errorReportingEnabled = reporting }

    @Test fun everyChooseEntrypointPreservesExactStringIdentifiers() = runBlocking {
        for (value in listOf("101", "1e3", "-0", "true", "false", "null", "", "001",
            "123456789012345678901234567890", "slot", "a\"b", "[1]", "{\"key\":1}", "with space", "\"slot\"")) {
            bodies.clear()
            rawBodies.clear()
            sdk.getContentBySelector(value, page)
            sdk.getContentByGroupSelector(value, page)
            sdk.getContentByCampaignId(value, page)
            sdk.getContentBySelectorWithDetails(value, page)
            sdk.getContentByCampaignIdWithDetails(value, page)
            assertEquals("Five requests for '$value'", 5, bodies.size)
            rawBodies.zip(listOf("selector", "group", "campaignId", "selector", "campaignId")).forEach { (raw, key) ->
                val actual = Json.parseToJsonElement(raw).jsonObject.getValue("data").jsonArray[0].jsonObject.getValue(key).jsonPrimitive
                assertTrue("$key must be a quoted JSON string: $raw", actual.isString)
                assertEquals(value, actual.content)
            }
            bodies.zip(listOf("selector", "group", "campaignId", "selector", "campaignId")).forEach { (body, key) ->
                val actual = body.getJSONArray("data").getJSONObject(0).get(key)
                assertTrue("$key '$value' must be a JSON string, got $actual", actual is String)
                assertEquals(value, actual)
            }
        }
    }
}
