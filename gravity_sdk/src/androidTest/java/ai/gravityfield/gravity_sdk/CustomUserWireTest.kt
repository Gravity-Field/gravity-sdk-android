package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.PageContext
import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.russhwolf.settings.Settings
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wire-level checks for the user set via setUser: content requests must carry
 * custom/ses like /visit and /event do, and stop carrying the previous user
 * after a switch.
 */
@RunWith(AndroidJUnit4::class)
class CustomUserWireTest {
    private val pageContext = PageContext(type = ContextType.OTHER, data = emptyList(), location = "/custom-user")
    private lateinit var server: RecordingServer

    // The anonymous test persists a server uid; put back whatever was there
    private val settings = Settings()
    private var savedUserId: String? = null

    @Before
    fun setUp() {
        savedUserId = settings.getStringOrNull(USER_ID_KEY)
        server = RecordingServer().also { it.start() }
        initializeAnonymousSdk()
    }

    private fun initializeAnonymousSdk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        GravitySDK.initialize(
            context = context,
            apiKey = "custom-user-key",
            section = "custom-user-section",
            gravityEventCallback = {},
        )
        GravitySDK.instance.setOptions(options = null, contentSettings = null, proxyUrl = server.url)
    }

    @After
    fun tearDown() {
        initializeAnonymousSdk()
        GravitySDK.instance.setOptions(options = null, contentSettings = null, proxyUrl = null)
        server.stop()
        savedUserId?.let { settings.putString(USER_ID_KEY, it) } ?: settings.remove(USER_ID_KEY)
    }

    private fun user(request: RecordingServer.Request): JSONObject = request.body.optJSONObject("user") ?: JSONObject()

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

    private fun chooseRequests() = server.recorded.filter { it.path.endsWith("/choose") }

    private fun assertCustomUser(requests: List<RecordingServer.Request>, custom: String, ses: String) {
        requests.forEach {
            assertEquals(it.path, custom, user(it).str("custom"))
            assertEquals(it.path, ses, user(it).str("ses"))
        }
    }

    private fun getContentAllWays() = runBlocking {
        GravitySDK.instance.getContentBySelector("sel", pageContext)
        GravitySDK.instance.getContentByCampaignId("101", pageContext)
        GravitySDK.instance.getContentByGroupSelector("grp", pageContext)
    }

    private fun activity(): Activity {
        var activity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity = Activity() }
        return activity!!
    }

    @Test
    fun allThreeChooseVariantsCarryTheCustomUser() {
        GravitySDK.instance.setUser("user-A", "session-A")

        getContentAllWays()

        assertEquals(3, chooseRequests().size)
        assertCustomUser(chooseRequests(), "user-A", "session-A")
    }

    @Test
    fun switchingUserReplacesCustomAndSession() {
        GravitySDK.instance.setUser("user-A", "session-A")
        getContentAllWays()
        server.reset()

        GravitySDK.instance.setUser("user-B", "session-B")
        getContentAllWays()

        assertEquals(3, chooseRequests().size)
        assertCustomUser(chooseRequests(), "user-B", "session-B")
    }

    @Test
    fun chooseAnswerDoesNotOverwriteTheAnonymousSession() = runBlocking {
        // Anonymous: the server assigns the session, the SDK keeps it
        server.responseUser = JSONObject(mapOf("uid" to "anon-uid", "ses" to "anon-ses"))
        GravitySDK.instance.getContentBySelector("anon-1", pageContext)
        assertEquals(null, user(chooseRequests().single()).str("custom"))
        GravitySDK.instance.getContentBySelector("anon-2", pageContext)
        val anonymous = user(chooseRequests().last())
        assertEquals("anon-uid", anonymous.str("uid"))
        assertEquals("anon-ses", anonymous.str("ses"))
        assertEquals(null, anonymous.str("custom"))

        // With a custom user the server's answer must not replace anything
        server.reset()
        server.responseUser = JSONObject(mapOf("uid" to "other-uid", "ses" to "other-ses"))
        GravitySDK.instance.setUser("user-A", "session-A")
        getContentAllWays()
        GravitySDK.instance.getContentBySelector("after", pageContext)
        assertEquals(4, chooseRequests().size)
        assertCustomUser(chooseRequests(), "user-A", "session-A")
        assertEquals("user-A", GravitySDK.instance.user?.custom)
        assertEquals("session-A", GravitySDK.instance.user?.ses)

        // Back to anonymous: the earlier session is still the one in use
        server.reset()
        initializeAnonymousSdk()
        GravitySDK.instance.getContentBySelector("anon-3", pageContext)
        val back = user(chooseRequests().last())
        assertEquals("anon-uid", back.str("uid"))
        assertEquals("anon-ses", back.str("ses"))
        assertEquals(null, back.str("custom"))
    }

    @Test
    fun trackViewSendsTheSameUserToVisitAndChoose() {
        server.campaigns = JSONArray().put(JSONObject(mapOf("campaignId" to "101", "trigger" to "view", "priority" to 1)))
        GravitySDK.instance.setUser("user-A", "session-A")

        GravitySDK.instance.trackView(pageContext, activity())

        val requests = server.waitFor(2).filter { it.path.endsWith("/visit") || it.path.endsWith("/choose") }
        assertEquals(listOf("/visit", "/choose"), requests.map { it.path.substringAfterLast('/').let { p -> "/$p" } })
        assertCustomUser(requests, "user-A", "session-A")
    }

    @Test
    fun triggerEventSendsTheSameUserToEventAndChoose() {
        server.campaigns = JSONArray().put(JSONObject(mapOf("campaignId" to "101", "trigger" to "event", "priority" to 1)))
        GravitySDK.instance.setUser("user-A", "session-A")

        GravitySDK.instance.triggerEvent(listOf(CustomEvent(type = "x", name = "X")), pageContext, activity())

        val requests = server.waitFor(2).filter { it.path.endsWith("/event") || it.path.endsWith("/choose") }
        assertEquals(listOf("/event", "/choose"), requests.map { it.path.substringAfterLast('/').let { p -> "/$p" } })
        assertCustomUser(requests, "user-A", "session-A")
        assertFalse(requests.isEmpty())
    }

    private companion object {
        // Same key as Prefs
        const val USER_ID_KEY = "gravity_user_id"
    }
}
