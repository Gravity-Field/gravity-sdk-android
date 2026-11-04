package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooseIdentifierWireTest {
    private lateinit var server: RecordingServer
    private val page = PageContext(ContextType.OTHER, emptyList(), "/choose-wire")

    private val wasReportingEnabled = ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled

    @Before fun setUp() {
        ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled = false
        server = RecordingServer().also { it.start() }
        GravitySDK.initialize(InstrumentationRegistry.getInstrumentation().targetContext,
            "choose-key", "choose-section", {})
        GravitySDK.instance.setUser("choose-user", "choose-session")
        GravitySDK.instance.setOptions(null, null, server.url)
    }

    @After fun tearDown() {
        GravitySDK.instance.setOptions(null, null, null)
        server.stop()
        ai.gravityfield.gravity_sdk.utils.Logger.errorReportingEnabled = wasReportingEnabled
    }

    @Test fun chooseIdentifiersRemainExactJsonStrings() = runBlocking {
        for (value in listOf("101", "1e3", "1e5", "-0", "true", "false", "null", "123456789012345678901234567890", "selector", "", "001", "a\"b", "[1]", "{\"key\":1}", "with space")) {
            server.reset()
            GravitySDK.instance.getContentBySelector(value, page)
            GravitySDK.instance.getContentByGroupSelector(value, page)
            GravitySDK.instance.getContentByCampaignId(value, page)
            val requests = server.recorded.filter { it.path.endsWith("/choose") }
            assertEquals("All identifiers must reach the server: '$value'", 3, requests.size)
            for ((request, key) in requests.zip(listOf("selector", "group", "campaignId"))) {
                val actual = request.body.getJSONArray("data").getJSONObject(0).get(key)
                assertTrue("$key '$value' must be a JSON string, got $actual", actual is String)
                assertEquals(value, actual)
            }
        }
    }
}
