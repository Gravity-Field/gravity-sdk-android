package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionStorageReadFailureTest {
    private class Store(@Volatile var uid: String?) : UserIdStorage {
        @Volatile var failRead = false
        override fun read(): String? {
            if (failRead) throw IOException("Unreadable UID")
            return uid
        }
        override fun write(uid: String) { this.uid = uid }
        override fun remove() { uid = null }
    }

    @Test(timeout = 10000) fun failedTransitionReadsKeepResetAnonymousButAllowRestoreToReloadDisk() = runBlocking<Unit> {
        for (restore in listOf(false, true)) {
            val store = Store("cached")
            val manager = SessionManager(store)
            assertEquals("cached", manager.getUserId())
            manager.setUser("custom", "custom-session")
            store.uid = "on-disk"
            store.failRead = true

            expectReadFailure { if (restore) manager.restoreUserId("rejected") else manager.resetUser() }
            assertEquals("on-disk", store.uid)
            assertNull(manager.customUser)
            store.failRead = false

            assertEquals(if (restore) "on-disk" else null, manager.getUserId())
            assertEquals(User(uid = if (restore) "on-disk" else null), manager.cachedUser())
        }
    }

    @Test(timeout = 10000) fun requestsRetryFailedTransitionReadsAndUseTheRepairedStoredUid() = runBlocking<Unit> {
        for (restore in listOf(false, true)) {
            val store = Store("stored").apply { failRead = true }
            val manager = SessionManager(store)
            expectReadFailure { if (restore) manager.restoreUserId("rejected") else manager.resetUser() }
            if (restore) expectReadFailure {
                manager.execute<User>(manager.snapshot(), { it }) {
                    fail("A request must not bypass unreadable identity storage")
                    null
                }
            }
            assertEquals("stored", store.uid)
            store.failRead = false

            val response = manager.execute(manager.snapshot(), { it }) { outgoing ->
                assertEquals(User(uid = if (restore) "stored" else null), outgoing)
                User(uid = "stored", ses = "recovered-session")
            }
            assertNotNull(response)
            assertEquals("stored", manager.getUserId())
        }
    }

    private suspend fun expectReadFailure(operation: suspend () -> Unit) {
        try {
            operation()
            fail("An unreadable UID must fail explicitly")
        } catch (error: IOException) {
            assertEquals("Unreadable UID", error.message)
        }
    }
}
