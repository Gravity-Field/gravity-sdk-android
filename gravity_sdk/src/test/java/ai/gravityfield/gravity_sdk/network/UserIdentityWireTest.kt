package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.session.SessionManager
import ai.gravityfield.gravity_sdk.session.UserIdStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UserIdentityWireTest {
    private class Store(var uid: String? = null) : UserIdStorage {
        override fun read() = uid
        override fun write(uid: String) { this.uid = uid }
        override fun remove() { uid = null }
    }
    private val page = PageContext(ContextType.OTHER, emptyList(), "test")
    private fun repository(sessions: SessionManager, client: HttpClient) = GravityRepository(
        sessions, client, { "test-section" }, { Device("test-agent", "device", null, null) }, { "https://test.local" },
    )
    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        defaultRequest { contentType(ContentType.Application.Json) }
        install(ContentNegotiation) { json(Json { explicitNulls = false; encodeDefaults = true }) }
    }
    private suspend fun request(repo: GravityRepository, sessions: SessionManager, kind: Int): Any? = when (kind) {
        0 -> repo.visit(page, Options(), sessions.snapshot())
        1 -> repo.event(emptyList(), Date(0), page, Options(), sessions.snapshot())
        2 -> repo.chooseByCampaignIdWithDetails("101", Options(), ContentSettings(), sessions.snapshot(), page)
        3 -> repo.chooseBySelectorWithDetails("\"slot\"", Options(), ContentSettings(), sessions.snapshot(), page)
        else -> repo.chooseByGroup("\"group\"", Options(), ContentSettings(), sessions.snapshot(), page)
    }

    @Test(timeout = 15000) fun allEndpointsShareTheFirstDeviceSession() = runBlocking<Unit> {
        // Each endpoint must be able to own initialization, not just /visit.
        for (owner in 0..4) {
            val store = Store()
            val sessions = SessionManager(store)
            val bodies = CopyOnWriteArrayList<JSONObject>()
            val firstEntered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client = client { req ->
                bodies.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
                if (bodies.size == 1) { firstEntered.complete(Unit); release.await() }
                respond("""{"user":{"uid":"one","ses":"session"},"data":[],"campaigns":[]}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            try {
                val repo = repository(sessions, client)
                val first = async { request(repo, sessions, owner) }
                firstEntered.await()
                val followers = (0..4).map { async { request(repo, sessions, it) } }
                yield()
                assertEquals(1, bodies.size)
                release.complete(Unit)
                assertNotNull(first.await())
                followers.forEach { assertNotNull(it.await()) }
                assertTrue(bodies.first().isNull("uid"))
                bodies.drop(1).forEach { body ->
                    assertEquals("one", body.getString("uid")); assertEquals("session", body.getString("ses"))
                }
                assertEquals("one", sessions.getUserId())
                assertEquals("one", store.uid)
            } finally { client.close() }
        }
    }

    @Test(timeout = 15000) fun restoreDuringEachEndpointDiscardsOldContentAndOldUid() = runBlocking<Unit> {
        for (kind in 0..4) {
            val store = Store("old")
            val sessions = SessionManager(store)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client = client {
                entered.complete(Unit); release.await()
                respond("""{"user":{"uid":"old","ses":"old-session"},"data":[],"campaigns":[]}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            try {
                val old = async { request(repository(sessions, client), sessions, kind) }
                entered.await()
                sessions.restoreUserId("restored")
                release.complete(Unit)
                assertNull(old.await())
                assertEquals("restored", sessions.getUserId())
                assertEquals("restored", store.uid)
            } finally { client.close() }
        }
    }

    @Test(timeout = 15000) fun customUserIsSentButNeverReplacesServerUid() = runBlocking<Unit> {
        val store = Store("anonymous")
        val sessions = SessionManager(store)
        sessions.setUser("custom", "custom-session")
        val client = client { req ->
            val user = JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user")
            assertEquals("custom", user.getString("custom"))
            assertEquals("custom-session", user.getString("ses"))
            assertTrue(user.isNull("uid"))
            respond("""{"user":{"uid":"custom-response","ses":"other-session"},"data":[],"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val repo = repository(sessions, client)
            for (kind in 0..4) assertNotNull(request(repo, sessions, kind))
            assertEquals("anonymous", sessions.getUserId())
            assertEquals("anonymous", store.uid)
        } finally { client.close() }
    }
    @Test(timeout = 15000) fun eventRaisedBeforeSetUserIsSentAndItsResultIsDiscarded() = runBlocking<Unit> {
        val sessions = SessionManager(Store("stored-anonymous"))
        val snapshot = sessions.snapshot()
        sessions.setUser("logged-in", "login-session")
        val sent = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            sent.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            respond("""{"user":{"uid":"late"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val result = repository(sessions, client).event(emptyList(), Date(0), page, Options(), snapshot)
            assertEquals("event dropped before send", 1, sent.size)
            assertTrue(sent.single().isNull("custom"))
            assertEquals("stored-anonymous", sent.single().getString("uid"))
            assertNull(result)
            assertEquals("late", sessions.getUserId())
        } finally { client.close() }
    }

    @Test(timeout = 15000) fun eventWaitingBehindColdStartGateSurvivesResetWithOriginalUid() = runBlocking<Unit> {
        val store = Store("original")
        val sessions = SessionManager(store)
        val visitEntered = CompletableDeferred<Unit>()
        val visitRelease = CompletableDeferred<Unit>()
        val sentEvents = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            if (req.url.encodedPath.endsWith("/visit")) {
                visitEntered.complete(Unit); visitRelease.await()
            } else {
                sentEvents.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            }
            respond("""{"user":{"uid":"original","ses":"late-session"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val repo = repository(sessions, client)
            val first = async { repo.visit(page, Options(), sessions.snapshot()) }
            visitEntered.await()
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                repo.event(emptyList(), Date(0), page, Options(), sessions.snapshot())
            }
            sessions.resetUser()
            assertNull(withTimeout(1000) { waiting.await() })
            assertEquals("event waiting for session dropped by reset", 1, sentEvents.size)
            assertEquals("original", sentEvents.single().getString("uid"))
            assertNull(store.uid)
            assertNull(sessions.getUserId())
            visitRelease.complete(Unit)
            assertNull(first.await())
        } finally { visitRelease.complete(Unit); client.close() }
    }

    @Test(timeout = 15000) fun coldVisitRaisedWithStoredUidSurvivesRestoreAndDoesNotOverwriteIt() = runBlocking<Unit> {
        val store = Store("original")
        val sessions = SessionManager(store)
        val raised = sessions.snapshot()
        sessions.restoreUserId("restored")
        val sent = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            sent.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            respond("""{"user":{"uid":"original","ses":"old-session"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            assertNull(repository(sessions, client).visit(page, Options(), raised))
            assertEquals(1, sent.size)
            assertEquals("original", sent.single().getString("uid"))
            assertEquals("restored", store.uid)
        } finally { client.close() }
    }

    @Test(timeout = 15000) fun unknownOwnerWaitingOnGateAdoptsRestoredIdentityWithoutDroppingEvent() = runBlocking<Unit> {
        val sessions = SessionManager(Store())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            val event = req.url.encodedPath.endsWith("/event")
            if (!event) { entered.complete(Unit); release.await() }
            else events.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            respond("""{"user":{"uid":"restored","ses":"new-session"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val repo = repository(sessions, client)
            val first = async { repo.visit(page, Options(), sessions.snapshot()) }
            entered.await()
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                repo.event(emptyList(), Date(0), page, Options(), sessions.snapshot())
            }
            sessions.restoreUserId("restored")
            assertNull(withTimeout(1000) { waiting.await() })
            assertEquals(1, events.size)
            assertEquals("restored", events.single().getString("uid"))
            assertEquals("restored", sessions.getUserId())
            assertEquals("new-session", sessions.cachedUser().ses)
            release.complete(Unit)
            assertNull(first.await())
        } finally { release.complete(Unit); client.close() }
    }

    @Test(timeout = 15000) fun delayedEventKeepsItsOriginalCustomUser() = runBlocking<Unit> {
        val sessions = SessionManager(Store("anonymous"))
        sessions.setUser("original-custom", "original-session")
        val raised = sessions.snapshot()
        sessions.setUser("current-custom", "current-session")
        val sent = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            sent.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            respond("""{"user":{"uid":"old-response"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            assertNull(repository(sessions, client).event(emptyList(), Date(0), page, Options(), raised))
            assertEquals(1, sent.size)
            assertEquals("original-custom", sent.single().getString("custom"))
            assertEquals("original-session", sent.single().getString("ses"))
            assertEquals("anonymous", sessions.getUserId())
        } finally { client.close() }
    }

    @Test(timeout = 15000) fun anonymousEventWaitingForSessionIsNotAttributedToLaterCustomUser() = runBlocking<Unit> {
        verifyAnonymousEventWaitsThroughSetUser(storedUid = null, responseUid = "assigned")
    }

    @Test(timeout = 15000) fun eventWithKnownUidKeepsWaitingForSessionAfterSetUser() = runBlocking<Unit> {
        verifyAnonymousEventWaitsThroughSetUser(storedUid = "stored", responseUid = "stored")
    }

    @Test(timeout = 15000) fun serverCanReplaceStoredUidBeforePreLoginEventIsSent() = runBlocking<Unit> {
        verifyAnonymousEventWaitsThroughSetUser(storedUid = "obsolete", responseUid = "replacement")
    }

    private suspend fun verifyAnonymousEventWaitsThroughSetUser(storedUid: String?, responseUid: String) = coroutineScope {
        val sessions = SessionManager(Store(storedUid))
        val visitEntered = CompletableDeferred<Unit>()
        val visitRelease = CompletableDeferred<Unit>()
        val eventSent = CompletableDeferred<JSONObject>()
        val client = client { req ->
            if (req.url.encodedPath.endsWith("/visit")) {
                visitEntered.complete(Unit)
                visitRelease.await()
            } else {
                eventSent.complete(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            }
            respond("""{"user":{"uid":"$responseUid","ses":"anonymous-session"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val repo = repository(sessions, client)
            val visit = async { repo.visit(page, Options(), sessions.snapshot()) }
            visitEntered.await()
            val raised = sessions.snapshot()
            val event = async(start = CoroutineStart.UNDISPATCHED) {
                repo.event(emptyList(), Date(0), page, Options(), raised)
            }
            sessions.setUser("later-customer", "customer-session")
            // Scheduling the released gate waiter must not turn it into a second session opener.
            yield()
            assertFalse("pre-login event bypassed the cold-start gate", eventSent.isCompleted)
            assertFalse(event.isCompleted)
            visitRelease.complete(Unit)
            val outgoing = withTimeout(1000) { eventSent.await() }
            assertTrue("anonymous event acquired a later custom user", outgoing.isNull("custom"))
            assertEquals(responseUid, outgoing.opt("uid"))
            assertEquals("anonymous-session", outgoing.opt("ses"))
            assertNull(visit.await())
            assertNull(event.await())
            assertEquals(responseUid, sessions.getUserId())
            assertEquals("later-customer", sessions.customUser?.custom)
        } finally { visitRelease.complete(Unit); client.close() }
    }

    @Test(timeout = 15000) fun anonymousVisitRaisedBeforeSetUserUsesStoredAnonymousUid() = runBlocking<Unit> {
        val sessions = SessionManager(Store("stored-anonymous"))
        val raised = sessions.snapshot() // The process has not read preferences yet.
        sessions.setUser("later-customer", "customer-session")
        val sent = CopyOnWriteArrayList<JSONObject>()
        val client = client { req ->
            sent.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
            respond("""{"user":{"uid":"stored-anonymous","ses":"anonymous-session"},"campaigns":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            assertNull(repository(sessions, client).visit(page, Options(), raised))
            assertEquals(1, sent.size)
            assertTrue(sent.single().isNull("custom"))
            assertEquals("stored-anonymous", sent.single().getString("uid"))
            assertEquals("stored-anonymous", sessions.getUserId())
        } finally { client.close() }
    }

    @Test(timeout = 15000) fun frozenEventMayBorrowSessionOnlyFromTheSameRestoredUid() = runBlocking<Unit> {
        for (restoredUid in listOf("same-user", "different-user")) {
            val sessions = SessionManager(Store("same-user"))
            assertEquals("same-user", sessions.getUserId())
            val raised = sessions.snapshot()
            sessions.restoreUserId(restoredUid)
            val sent = CopyOnWriteArrayList<JSONObject>()
            val client = client { req ->
                sent.add(JSONObject((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).getJSONObject("user"))
                respond("""{"user":{"uid":"$restoredUid","ses":"restored-session"},"campaigns":[]}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            try {
                val repo = repository(sessions, client)
                assertNotNull(repo.visit(page, Options(), sessions.snapshot()))
                assertNull(repo.event(emptyList(), Date(0), page, Options(), raised))
                assertEquals(2, sent.size)
                assertEquals("same-user", sent.last().getString("uid"))
                if (restoredUid == "same-user") assertEquals("restored-session", sent.last().opt("ses"))
                else assertTrue(sent.last().isNull("ses"))
                assertEquals(restoredUid, sessions.getUserId())
            } finally { client.close() }
        }
    }

}
