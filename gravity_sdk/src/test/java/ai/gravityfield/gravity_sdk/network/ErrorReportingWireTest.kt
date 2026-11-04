package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.HeadlessRecordingServer
import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.session.*
import ai.gravityfield.gravity_sdk.utils.*
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ErrorReportingWireTest {
    private class Storage(var value: String? = null) : UserIdStorage {
        override fun read() = value
        override fun write(uid: String) { value = uid }
        override fun remove() { value = null }
    }
    @Before fun setup() { Logger.errorReportingEnabled = false; Logger.configure(LogLevel.DEBUG); ShadowLog.clear() }
    @After fun cleanup() { Logger.errorReportingEnabled = true; Logger.configure(LogLevel.NONE) }
    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        // Diagnostic fixtures must raise an HTTP exception independently of repository policy flags.
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value !in 200..299) throw ResponseException(response, "BODY-SECRET")
            }
        }
        defaultRequest { contentType(ContentType.Application.Json) }
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
    }
    private fun repository(manager: SessionManager, http: HttpClient) = GravityRepository(
        manager, http, { "section" }, { Device("agent", "device-id", null, null) }, { "https://local.test" },
        suppliedErrorClient = http,
    )
    private fun logs() = ShadowLog.getLogs().joinToString("\n") { it.msg + (it.throwable?.stackTraceToString() ?: "") }

    @Test fun reportsUseCurrentServerIdentityAndSanitizeWireText() = runBlocking {
        val bodies = mutableListOf<JSONObject>()
        val http = client { request ->
            bodies += JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond("{}", HttpStatusCode.OK)
        }
        try {
            val manager = SessionManager(Storage())
            val repo = repository(manager, http)
            repo.sendErrorMessage("https://public.test/,https://u:SECRET@private.test/path?token=x", "https://host/a?SECRET")
            assertEquals("device-id", bodies.last().getString("uid"))
            assertEquals("no-session", bodies.last().getString("ses"))
            manager.restoreUserId("restored")
            manager.setUser("CUSTOM-SECRET", "custom-session")
            repo.sendErrorMessage("probe", "")
            assertEquals("restored", bodies.last().getString("uid"))
            assertEquals("custom-session", bodies.last().getString("ses"))
            assertFalse(bodies.toString().contains("SECRET"))
            assertFalse(bodies.toString().contains("token="))
        } finally { http.close() }
    }

    @Test fun reportDeliversDuringPendingNetworkSessionWithColdPersistedUid() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var report: JSONObject? = null
        val http = client { request ->
            if (request.url.encodedPath == "/visit") {
                entered.complete(Unit)
                release.await()
                respond("""{"user":{"uid":"new","ses":"new-session"},"campaigns":[]}""", HttpStatusCode.OK)
            } else {
                report = JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                respond("{}", HttpStatusCode.OK)
            }
        }
        val manager = SessionManager(Storage("persisted"))
        val repo = repository(manager, http)
        val visit = async { repo.visit(PageContext(ContextType.OTHER, emptyList(), "test"), Options(), manager.snapshot()) }
        try {
            entered.await()
            withTimeout(1000) { repo.sendErrorMessage("probe", "") }
            assertEquals("persisted", report!!.getString("uid"))
            assertEquals("no-session", report!!.getString("ses"))
        } finally { release.complete(Unit); visit.await(); http.close() }
    }

    @Test fun emptyStoredDeviceIdIsRepairedAndSharedWithReport() = runBlocking {
        ai.gravityfield.gravity_sdk.prefs.Prefs.initialize(org.robolectric.RuntimeEnvironment.getApplication())
        ai.gravityfield.gravity_sdk.prefs.Prefs.setDeviceId("")
        var report: JSONObject? = null
        val http = client { request ->
            report = JSONObject((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond("{}", HttpStatusCode.OK)
        }
        try {
            GravityRepository(SessionManager(Storage()), http, { "section" }, suppliedErrorClient = http)
                .sendErrorMessage("probe", "")
            assertTrue(report!!.getString("uid").isNotEmpty())
            assertEquals(DeviceUtils.getDevice().id, report!!.getString("uid"))
        } finally { http.close() }
    }

    @Test fun deviceFailureDoesNotEscapeOrLogExceptionSecrets() = runBlocking {
        val http = client { error("No report expected") }
        try {
            GravityRepository(SessionManager(Storage()), http, { "section" },
                device = { error("DEVICE-SECRET") }, suppliedErrorClient = http).sendErrorMessage("probe", "")
            assertFalse(logs().contains("DEVICE-SECRET"))
            assertTrue(logs().contains("sendErrorMessage failed: IllegalStateException"))
        } finally { http.close() }
    }

    @Test fun httpAndParsingDiagnosticsNeverIncludeBodiesOrExceptionStacks() = runBlocking {
        val page = PageContext(ContextType.OTHER, emptyList(), "test")
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.OK)) {
            val http = client { respond("BODY-SECRET https://host/path?token=SECRET", status) }
            try {
                val manager = SessionManager(Storage("uid"))
                val repo = repository(manager, http)
                assertNull(repo.visit(page, Options(), manager.snapshot()))
                assertNull(repo.event(emptyList(), Date(0), page, Options(), manager.snapshot()))
                assertNull(repo.chooseByCampaignIdWithDetails("101", Options(), ContentSettings(), manager.snapshot(), page))
                assertNull(repo.chooseBySelectorWithDetails("\"slot\"", Options(), ContentSettings(), manager.snapshot(), page))
                assertNull(repo.chooseByGroup("\"group\"", Options(), ContentSettings(), manager.snapshot(), page))
                assertFalse(logs().contains("SECRET"))
                assertTrue(ShadowLog.getLogs().all { it.throwable == null })
                if (status == HttpStatusCode.BadRequest) assertTrue(logs().contains("POST failed: HTTP 400 /visit"))
            } finally { http.close(); ShadowLog.clear() }
        }
    }

    @Test fun productionHttpLoggingRedactsAuthorization() = runBlocking {
        val server = HeadlessRecordingServer { _, _ -> 200 to "{}" }
        server.start()
        val method = GravityRepository.Companion.javaClass.getDeclaredMethod("createHttpClient", Json::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val http = method.invoke(GravityRepository.Companion, Json, false) as HttpClient
        try {
            http.post(server.url + "/event") { header(HttpHeaders.Authorization, "Bearer AUTH-SECRET"); setBody("{}") }
            assertTrue(logs().contains("Authorization"))
            assertFalse(logs().contains("AUTH-SECRET"))
        } finally { http.close(); server.stop() }
    }
    @Test fun crossReviewProductionLoggingMustNotExposeErrorBodyOrUrlSecrets() = runBlocking {
        val server = HeadlessRecordingServer { _, _ -> 503 to "HTTP-BODY-SECRET" }
        server.start()
        val method = GravityRepository.Companion.javaClass.getDeclaredMethod("createHttpClient", Json::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val http = method.invoke(GravityRepository.Companion, Json, false) as HttpClient
        try {
            http.post(server.url + "/event?token=URL-SECRET") { setBody("""{"secret":"REQUEST-BODY-SECRET"}""") }.body<String>()
            val observed = logs()
            assertFalse("Request body leaked into SDK diagnostics", observed.contains("REQUEST-BODY-SECRET"))
            assertFalse("Error body leaked into SDK diagnostics", observed.contains("HTTP-BODY-SECRET"))
            assertFalse("Query leaked into SDK diagnostics", observed.contains("URL-SECRET"))
            assertTrue("Keep method, endpoint and status useful", observed.contains("POST") && observed.contains("/event") && observed.contains("503"))
        } finally { http.close(); server.stop() }
    }
}
