package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.Device
import ai.gravityfield.gravity_sdk.session.SessionManager
import ai.gravityfield.gravity_sdk.session.UserIdStorage
import ai.gravityfield.gravity_sdk.utils.LogLevel
import ai.gravityfield.gravity_sdk.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ErrorReportLimiterWireTest {
    @Before fun setup() { Logger.errorReportingEnabled = false; Logger.configure(LogLevel.DEBUG); ShadowLog.clear() }
    @After fun cleanup() { Logger.errorReportingEnabled = true; Logger.configure(LogLevel.NONE) }

    @Test fun budgetDropsBeforeDeviceAndPayloadFormatting() = runBlocking {
        val h = Harness()
        try {
            repeat(12) { h.repo.sendErrorMessage("failure-$it", "") }
            assertEquals(10, h.posts.get())
            assertEquals(10, h.sections.get())
            assertEquals(10, h.devices.get())
        } finally { h.close() }
    }

    @Test fun sanitizedDuplicateReportsSendOnlyOnce() = runBlocking {
        val h = Harness()
        try {
            h.repo.sendErrorMessage("GET https://u:FIRST@host/p?token=one", "https://host/s?token=one")
            h.repo.sendErrorMessage("GET https://u:SECOND@host/p?token=two", "https://host/s?token=two")
            assertEquals(1, h.posts.get())
            assertEquals(1, h.sections.get())
        } finally { h.close() }
    }

    @Test fun failedHttpSendCanRetryAndSuccessfulRetryDeduplicates() = runBlocking {
        val h = Harness { if (it == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK }
        try {
            repeat(3) { h.repo.sendErrorMessage("same failure", "same stack") }
            assertEquals(2, h.posts.get())
        } finally { h.close() }
    }

    @Test fun transportFailureCanRetryAndSuccessfulRetryDeduplicates() = runBlocking {
        val h = Harness { if (it == 1) throw IOException("synthetic transport failure") else HttpStatusCode.OK }
        try {
            repeat(3) { h.repo.sendErrorMessage("same failure", "same stack") }
            assertEquals(2, h.posts.get())
        } finally { h.close() }
    }

    @Test fun budgetDropDoesNotWaitForPendingIdentityOrLogAgain() = runBlocking {
        val h = Harness()
        try {
            repeat(10) { h.repo.sendErrorMessage("failure-$it", "") }
            val change = h.blockIdentity()
            val dropped = async(start = CoroutineStart.UNDISPATCHED) { h.repo.sendErrorMessage("over budget", "") }
            try {
                assertTrue("Spent budget must return before awaiting identity storage", dropped.isCompleted)
                assertEquals(10, h.posts.get())
                assertTrue("A limiter drop must be silent", ShadowLog.getLogs().isEmpty())
            } finally { h.storage.release?.complete(Unit); change.await(); dropped.await() }
        } finally { h.close() }
    }

    @Test fun duplicateDropDoesNotWaitForPendingIdentity() = runBlocking {
        val h = Harness()
        try {
            h.repo.sendErrorMessage("same failure", "same stack")
            val change = h.blockIdentity()
            val duplicate = async(start = CoroutineStart.UNDISPATCHED) { h.repo.sendErrorMessage("same failure", "same stack") }
            try {
                assertTrue("Duplicate admission must precede identity storage", duplicate.isCompleted)
                assertEquals(1, h.posts.get())
            } finally { h.storage.release?.complete(Unit); change.await(); duplicate.await() }
        } finally { h.close() }
    }

    @Test fun concurrentDuplicatesShareOnlyOneInflightSend() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val h = Harness { entered.complete(Unit); release.await(); HttpStatusCode.OK }
        try {
            h.manager.reportingUser()
            val jobs = List(20) { async(start = CoroutineStart.UNDISPATCHED) { h.repo.sendErrorMessage("same failure", "same stack") } }
            try {
                withTimeout(30_000) { entered.await() }
                assertEquals("Only the accepted report may suspend in transport", 19, jobs.count { it.isCompleted })
                assertEquals(1, h.posts.get())
            } finally { release.complete(Unit); jobs.awaitAll() }
        } finally { h.close() }
    }

    @Test fun failedSendsStillConsumeTheAttemptBudget() = runBlocking {
        val h = Harness { HttpStatusCode.InternalServerError }
        try {
            repeat(12) { h.repo.sendErrorMessage("failure-$it", "") }
            assertEquals(10, h.posts.get())
            assertEquals(10, h.sections.get())
        } finally { h.close() }
    }

    @Test fun cancelledSendReleasesFingerprintForARetry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val h = Harness { n ->
            if (n == 1) { entered.complete(Unit); release.await() }
            HttpStatusCode.OK
        }
        try {
            val first = launch { h.repo.sendErrorMessage("same failure", "same stack") }
            try {
                withTimeout(30_000) { entered.await() }
                first.cancelAndJoin()
                h.repo.sendErrorMessage("same failure", "same stack")
                h.repo.sendErrorMessage("same failure", "same stack")
                assertEquals(2, h.posts.get())
            } finally { release.complete(Unit); first.cancelAndJoin() }
        } finally { h.close() }
    }

    @Test fun sendFailureDiagnosticsExcludePerRequestIdsAndThrowableStacks() = runBlocking {
        val h = Harness { n -> throw IOException("per-request-id-$n BODY-SECRET") }
        try {
            repeat(2) { h.repo.sendErrorMessage("failure-$it", "") }
            val logs = ShadowLog.getLogs()
            assertEquals(2, logs.size)
            assertEquals(logs[0].msg, logs[1].msg)
            assertFalse(logs.toString().contains("per-request-id"))
            assertFalse(logs.toString().contains("BODY-SECRET"))
            assertTrue(logs.all { it.throwable == null })
            assertEquals(2, h.posts.get())
        } finally { h.close() }
    }

    private class Storage : UserIdStorage {
        var release: CompletableDeferred<Unit>? = null
        override val isReady get() = release?.isCompleted != false
        override suspend fun awaitReady() { release?.await() }
        override fun read(): String? = null
        override fun write(uid: String) = Unit
        override fun remove() = Unit
    }
    private class Harness(status: suspend (Int) -> HttpStatusCode = { HttpStatusCode.OK }) {
        val posts = AtomicInteger()
        val sections = AtomicInteger()
        val devices = AtomicInteger()
        val storage = Storage()
        val manager = SessionManager(storage)
        private val http = HttpClient(MockEngine {
            val code = status(posts.incrementAndGet())
            respond("{}", code)
        }) {
            defaultRequest { contentType(ContentType.Application.Json) }
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        }
        val repo = GravityRepository(manager, http, { sections.incrementAndGet(); "section" },
            { devices.incrementAndGet(); Device("agent", "device", null, null) }, { "https://local.test" },
            suppliedErrorClient = http)
        fun blockIdentity(): SessionManager.Change {
            storage.release = CompletableDeferred()
            return manager.beginReset()
        }
        fun close() { storage.release?.complete(Unit); http.close() }
    }
}
