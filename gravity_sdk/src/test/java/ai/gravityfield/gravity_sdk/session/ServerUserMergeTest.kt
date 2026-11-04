package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ServerUserMergeTest {
    private class Store(var uid: String? = null) : UserIdStorage {
        val writes = mutableListOf<String>()
        override fun read() = uid
        override fun write(uid: String) { writes.add(uid); this.uid = uid }
        override fun remove() { uid = null }
    }

    private suspend fun answer(manager: SessionManager, response: User) {
        assertNotNull(manager.execute(manager.snapshot(), { it }) { response })
    }

    private suspend fun outgoing(manager: SessionManager): User? =
        manager.execute(manager.snapshot(), { null }) { it }

    @Test(timeout = 10000) fun newUidWithoutSessionKeepsCachedSessionForFlutterParity() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        answer(manager, User(uid = "first", ses = "cached-session"))
        answer(manager, User(uid = "second"))

        // Flutter merges uid and ses independently; an omitted ses keeps its cached value.
        assertEquals(User(uid = "second", ses = "cached-session"), outgoing(manager))
    }

    @Test(timeout = 10000) fun sparseUserResponseKeepsCachedPair() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        answer(manager, User(uid = "known", ses = "cached-session"))
        answer(manager, Json.decodeFromString<User>("{}"))

        assertEquals(User(uid = "known", ses = "cached-session"), outgoing(manager))
    }

    @Test(timeout = 10000) fun emptyResponseUidKeepsCachedPairWithoutNotifyingOrPersistingIt() = runBlocking<Unit> {
        val store = Store()
        val manager = SessionManager(store)
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        answer(manager, User(uid = "known", ses = "cached-session"))
        answer(manager, User(uid = ""))

        assertEquals(User(uid = "known", ses = "cached-session"), outgoing(manager))
        assertEquals("known", manager.getUserId())
        assertEquals("known", store.uid)
        assertEquals(listOf("known"), store.writes)
        assertEquals(listOf("known"), heard)
    }

    @Test(timeout = 10000) fun emptyResponseUidStillMergesReceivedSession() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        answer(manager, User(uid = "known", ses = "cached-session"))
        answer(manager, User(uid = "", ses = "new-session"))

        assertEquals(User(uid = "known", ses = "new-session"), outgoing(manager))
    }
}
