package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.session.*
import ai.gravityfield.gravity_sdk.utils.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
class ChooseFailureDiagnosticsTest {
    private val reporting = Logger.errorReportingEnabled
    private val logLevel = Logger.logLevel
    @Before fun setup() { Logger.errorReportingEnabled = false; Logger.configure(LogLevel.DEBUG); ShadowLog.clear() }
    @After fun cleanup() { Logger.errorReportingEnabled = reporting; Logger.configure(logLevel) }

    @Test fun chooseHttpErrorsDoNotExposeBodyOrProxyCredentials() = runBlocking {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)) {
            val http = HttpClient(MockEngine { respond("BODY-SECRET", status) }) {
                defaultRequest { contentType(ContentType.Application.Json) }
                install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            }
            try {
                val sessions = SessionManager(object : UserIdStorage {
                    override fun read(): String? = "uid"
                    override fun write(uid: String) {}
                    override fun remove() {}
                })
                val repository = GravityRepository(sessions, http, { "section" },
                    { Device("agent", "device", null, null) }, { "https://u:PROXYPASS@local.test/v2" })
                val page = PageContext(ContextType.OTHER, emptyList(), "test")
                assertNull(repository.chooseByCampaignIdWithDetails("101", Options(), ContentSettings(), pageContext = page))
                assertNull(repository.chooseBySelectorWithDetails("\"slot\"", Options(), ContentSettings(), pageContext = page))
                assertNull(repository.chooseByGroup("\"group\"", Options(), ContentSettings(), pageContext = page))
                val logs = ShadowLog.getLogs()
                val diagnostics = logs.joinToString("\n") { it.msg + (it.throwable?.stackTraceToString() ?: "") }
                assertFalse(diagnostics, diagnostics.contains("BODY-SECRET"))
                assertFalse(diagnostics, diagnostics.contains("PROXYPASS"))
                assertTrue(logs.all { it.throwable == null })
                assertTrue(diagnostics.contains("HTTP ${status.value}"))
            } finally { http.close(); ShadowLog.clear() }
        }
    }
}
