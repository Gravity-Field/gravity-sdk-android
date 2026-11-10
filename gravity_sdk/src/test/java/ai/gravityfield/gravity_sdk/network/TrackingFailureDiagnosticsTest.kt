package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.session.*
import ai.gravityfield.gravity_sdk.utils.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingFailureDiagnosticsTest {
    private val reporting = Logger.errorReportingEnabled
    private val level = Logger.logLevel
    @Before fun setup() { Logger.errorReportingEnabled = false; Logger.configure(LogLevel.DEBUG); ShadowLog.clear() }
    @After fun cleanup() { Logger.errorReportingEnabled = reporting; Logger.configure(level) }
    @Test fun refusedTrackingLogsOnlyMethodStatusAndConstantEndpoint() = runBlocking {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)) {
            val http = HttpClient(MockEngine { respond("""{"user":{"uid":"wrong","ses":"wrong"},"campaigns":[],"detail":"BODY-SECRET"}""", status) }) {
                defaultRequest { contentType(ContentType.Application.Json) }
                install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            }
            try {
                val manager = SessionManager(object : UserIdStorage {
                    override fun read() = "uid"
                    override fun write(uid: String) {}
                    override fun remove() {}
                })
                val repo = GravityRepository(manager, http, { "section" }, { Device("agent", "id", null, null) }, { "https://u:PROXYPASS@local.test/v2" })
                val page = PageContext(ContextType.OTHER, emptyList(), "test")
                assertNull(repo.visit(page, Options(), manager.snapshot()))
                assertNull(repo.event(listOf(CustomEvent("test", "Test")), java.util.Date(0), page, Options(), manager.snapshot()))
                val logs = ShadowLog.getLogs()
                val text = logs.joinToString("\n") { it.msg + (it.throwable?.stackTraceToString() ?: "") }
                assertFalse(text, text.contains("BODY-SECRET"))
                assertFalse(text, text.contains("PROXYPASS"))
                assertTrue(logs.all { it.throwable == null })
                assertTrue(text, text.contains("POST failed: HTTP ${status.value} /visit"))
                assertTrue(text, text.contains("POST failed: HTTP ${status.value} /event"))
            } finally { http.close(); ShadowLog.clear() }
        }
    }
}
