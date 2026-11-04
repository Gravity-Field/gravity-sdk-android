package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionManagerTest {
    private class Store(var uid: String? = null) : UserIdStorage {
        var refuseWrite = false
        var refuseRemove = false
        override fun read() = uid
        override fun write(uid: String) { if (refuseWrite) throw IOException("write refused"); this.uid = uid }
        override fun remove() { if (refuseRemove) throw IOException("remove refused"); uid = null }
    }
    private suspend fun answer(manager: SessionManager, uid: String = "server", ses: String = "session"): User? =
        manager.execute(manager.snapshot(), { it }) { User(uid = uid, ses = ses) }

    @Test(timeout = 10000) fun coldStartAndRelaunchUseServerUidWithoutPersistingSession() = runBlocking<Unit> {
        val store = Store()
        val manager = SessionManager(store)
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        assertNull(manager.getUserId())
        answer(manager)
        answer(manager)
        assertEquals("server", manager.getUserId())
        assertEquals(listOf("server"), heard)
        val relaunched = SessionManager(store)
        assertEquals("server", relaunched.getUserId())
        relaunched.execute(relaunched.snapshot(), { it }) { user ->
            assertEquals(User(uid = "server"), user)
            User(uid = "server", ses = "new-session")
        }
    }

    @Test(timeout = 10000) fun resetAndRestoreClearCustomUserAndSession() = runBlocking<Unit> {
        val manager = SessionManager(Store("saved"))
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        manager.setUser("custom", "custom-session")
        assertEquals("saved", manager.getUserId())
        manager.resetUser()
        assertNull(manager.getUserId())
        manager.execute(manager.snapshot(), { it }) { user -> assertEquals(User(), user); User(uid = "new", ses = "new-session") }
        manager.setUser("other-custom", "other-session")
        manager.restoreUserId("restored")
        assertEquals("restored", manager.getUserId())
        manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals(User(uid = "restored"), user)
            User(uid = "restored", ses = "restored-session")
        }
        assertEquals(listOf(null, "new", "restored"), heard)
    }

    @Test(timeout = 10000) fun emptyRestoreDoesNotChangeIdentity() = runBlocking<Unit> {
        val manager = SessionManager(Store("saved"))
        manager.setUser("custom", "ses")
        try { manager.restoreUserId(""); fail("empty uid accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("saved", manager.getUserId())
        assertEquals("custom", manager.customUser?.custom)
    }

    @Test(timeout = 10000) fun storageFailuresAreExplicitAndNeverAnnounceAnUnwrittenRestore() = runBlocking<Unit> {
        val store = Store("saved")
        val manager = SessionManager(store)
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        manager.setUser("custom", "ses")
        store.refuseWrite = true
        try { manager.restoreUserId("refused"); fail("restore succeeded") } catch (_: IOException) { }
        assertNull(manager.customUser)
        assertEquals("saved", manager.getUserId())
        assertTrue(heard.isEmpty())
        store.refuseRemove = true
        try { manager.resetUser(); fail("reset succeeded") } catch (_: IOException) { }
        assertEquals("saved", store.uid)
        assertEquals(listOf<String?>(null), heard)
    }

    @Test(timeout = 10000) fun serverWriteFailureKeepsGetterAndListenerConsistentAndRetriesPersistence() = runBlocking<Unit> {
        val store = Store().apply { refuseWrite = true }
        val errors = mutableListOf<Exception>()
        val manager = SessionManager(store, { errors.add(it) })
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        assertNotNull(answer(manager))
        assertEquals("server", manager.getUserId())
        assertNull(store.uid)
        assertEquals(1, errors.size)
        store.refuseWrite = false
        answer(manager)
        assertEquals("server", store.uid)
        assertEquals(listOf("server"), heard)
    }

    @Test(timeout = 10000) fun parallelRequestsShareOneInitializationAndGetterWaitsForIt() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals(User(), user); entered.complete(Unit); release.await(); User(uid = "one", ses = "ses")
        } }
        entered.await()
        val second = async { manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals(User(uid = "one", ses = "ses"), user); User(uid = "one", ses = "ses")
        } }
        val getter = async { manager.getUserId() }
        yield()
        assertFalse(second.isCompleted)
        assertFalse(getter.isCompleted)
        release.complete(Unit)
        assertNotNull(first.await()); assertNotNull(second.await())
        assertEquals("one", getter.await())
    }

    @Test(timeout = 10000) fun resetReleasesWaitersAndLateResponseCannotReviveIdentityOrContent() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldRequest = manager.snapshot()
        val old = async { manager.execute(oldRequest, { it }) {
            entered.complete(Unit); release.await(); User(uid = "old", ses = "old-session")
        } }
        entered.await()
        val getter = async { manager.getUserId() }
        yield()
        manager.resetUser()
        assertNull(withTimeout(1000) { getter.await() })
        answer(manager, "new", "new-session")
        release.complete(Unit)
        assertNull(old.await())
        assertEquals("new", manager.getUserId())
        assertNull(manager.execute(oldRequest, { it }) { fail("stale queued request reached server"); User() })
    }

    @Test(timeout = 10000) fun changingCustomUserDiscardsInFlightContent() = runBlocking<Unit> {
        val manager = SessionManager(Store("anonymous"))
        manager.setUser("A", "session-A")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async { manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals("A", user.custom); entered.complete(Unit); release.await(); User(uid = "custom-response", ses = "A")
        } }
        entered.await()
        manager.setUser("B", "session-B")
        release.complete(Unit)
        assertNull(old.await())
        assertEquals("anonymous", manager.getUserId())
        assertEquals("B", manager.customUser?.custom)
    }

    @Test(timeout = 10000) fun cancellationReleasesInitializationForNextRequest() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val entered = CompletableDeferred<Unit>()
        val old = launch { manager.execute<User>(manager.snapshot(), { it }) { entered.complete(Unit); awaitCancellation() } }
        entered.await(); old.cancelAndJoin()
        assertNotNull(withTimeout(1000) { answer(manager) })
    }
    @Test(timeout = 10000) fun restoringWhileOldInitializationFinishesDoesNotReleaseTheNewGate() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val oldEntered = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val old = async { manager.execute(manager.snapshot(), { it }) {
            oldEntered.complete(Unit); oldRelease.await(); User(uid = "old", ses = "old-session")
        } }
        oldEntered.await()
        manager.restoreUserId("restored")
        val newEntered = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        val new = async { manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals(User(uid = "restored"), user)
            newEntered.complete(Unit); newRelease.await(); User(uid = "restored", ses = "new-session")
        } }
        newEntered.await()
        oldRelease.complete(Unit)
        assertNull(old.await())
        val getter = async { manager.getUserId() }
        yield()
        assertFalse(getter.isCompleted)
        newRelease.complete(Unit)
        assertNotNull(new.await())
        assertEquals("restored", getter.await())
    }

    @Test(timeout = 10000) fun failedInitializationReleasesWaitingRequest() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            runCatching { manager.execute<User>(manager.snapshot(), { it }) {
                entered.complete(Unit); release.await(); throw IOException("network unavailable")
            } }
        }
        entered.await()
        val next = async { answer(manager) }
        yield()
        assertFalse(next.isCompleted)
        release.complete(Unit)
        assertTrue(first.await().isFailure)
        assertNotNull(withTimeout(1000) { next.await() })
        assertEquals("server", manager.getUserId())
    }

    @Test(timeout = 10000) fun resetSerializesWithInFlightPersistence() = runBlocking<Unit> {
        val writeEntered = java.util.concurrent.CountDownLatch(1)
        val writeRelease = java.util.concurrent.CountDownLatch(1)
        var stored: String? = null
        val storage = object : UserIdStorage {
            override fun read() = stored
            override fun write(uid: String) {
                writeEntered.countDown()
                check(writeRelease.await(5, java.util.concurrent.TimeUnit.SECONDS))
                stored = uid
            }
            override fun remove() { stored = null }
        }
        val manager = SessionManager(storage)
        val first = async(Dispatchers.IO) { answer(manager, "old") }
        assertTrue(writeEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val resetStarted = CompletableDeferred<Unit>()
        val reset = async(Dispatchers.IO) { resetStarted.complete(Unit); manager.resetUser() }
        resetStarted.await()
        writeRelease.countDown()
        first.await(); reset.await()
        assertNull(stored)
        assertNull(manager.getUserId())
    }

    @Test(timeout = 10000) fun reinitializationDropsCustomUserAndPreservesAnonymousSession() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        answer(manager)
        manager.setUser("custom", "custom-session")
        manager.initialize()
        assertNull(manager.customUser)
        manager.execute(manager.snapshot(), { it }) { user ->
            assertEquals(User(uid = "server", ses = "session"), user)
            User(uid = "server", ses = "session")
        }
        Unit
    }

    @Test(timeout = 10000) fun serverUidChangesNotifyOnceAndListenerCanBeRemoved() = runBlocking<Unit> {
        val manager = SessionManager(Store())
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        answer(manager, "first")
        answer(manager, "second")
        answer(manager, "second")
        manager.setUserIdListener(null)
        answer(manager, "third")
        assertEquals(listOf("first", "second"), heard)
        assertEquals("third", manager.getUserId())
    }

    @Test(timeout = 10000) fun setUserDuringColdStartRetainsAssignedServerUidButDiscardsContent() = runBlocking<Unit> {
        val store = Store()
        val manager = SessionManager(store)
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { manager.execute(manager.snapshot(), { it }) {
            entered.complete(Unit); release.await(); User(uid = "assigned", ses = "anonymous-session")
        } }
        entered.await()
        manager.setUser("custom", "custom-session")
        release.complete(Unit)
        assertNull(first.await())
        assertEquals("assigned", manager.getUserId())
        assertEquals("assigned", store.uid)
        assertEquals(listOf("assigned"), heard)
    }

    @Test(timeout = 10000) fun blankRestoreIsRejectedAndUidIsTrimmed() = runBlocking<Unit> {
        val manager = SessionManager(Store("saved"))
        manager.setUser("custom", "session")
        try { manager.restoreUserId("  \t\n"); fail("blank uid accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("custom", manager.customUser?.custom)
        assertEquals("saved", manager.getUserId())
        manager.restoreUserId("  restored \n")
        assertEquals("restored", manager.getUserId())
    }


}
