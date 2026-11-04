package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionStorageConcurrencyTest {
    @Test(timeout = 10000) fun serverResponsePublishesUidAndSessionTogetherAfterPersistence() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = object : UserIdStorage {
            var uid: String? = null
            override fun read() = uid
            override fun write(uid: String) {
                if (uid == "second") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                this.uid = uid
            }
            override fun remove() { uid = null }
        }
        val manager = SessionManager(storage)
        manager.execute(manager.snapshot(), { it }) { User(uid = "first", ses = "first-session") }
        val announced = mutableListOf<User>()
        manager.setUserIdListener { announced.add(manager.cachedUser()) }
        val observing = AtomicBoolean(true)
        val observerStarted = CountDownLatch(1)
        val observer = Executors.newSingleThreadExecutor()
        val observations = observer.submit<Set<User>> {
            val seen = mutableSetOf<User>()
            do {
                seen.add(manager.cachedUser())
                manager.snapshot().knownUser?.let { seen.add(it) }
                observerStarted.countDown()
            } while (observing.get())
            seen
        }
        assertTrue(observerStarted.await(5, TimeUnit.SECONDS))
        val response = async(Dispatchers.Default) {
            manager.execute(manager.snapshot(), { it }) { User(uid = "second", ses = "second-session") }
        }
        try {
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(User(uid = "first", ses = "first-session"), manager.cachedUser())
                assertEquals(User(uid = "first", ses = "first-session"), manager.snapshot().knownUser)
                manager.execute<User>(manager.snapshot(), { null }) { outgoing ->
                    assertEquals(User(uid = "first", ses = "first-session"), outgoing)
                    null
                }
            } finally { release.countDown() }

            assertNotNull(response.await())
            observing.set(false)
            val seen = observations.get(5, TimeUnit.SECONDS)
            val allowed = setOf(
                User(uid = "first", ses = "first-session"),
                User(uid = "second", ses = "second-session"),
            )
            assertTrue("Readers observed a split uid/session publication: $seen", allowed.containsAll(seen))
            assertEquals(User(uid = "second", ses = "second-session"), manager.cachedUser())
            assertEquals(User(uid = "second", ses = "second-session"), manager.snapshot().knownUser)
            assertEquals(listOf(User(uid = "second", ses = "second-session")), announced)
        } finally {
            release.countDown()
            observing.set(false)
            observer.shutdownNow()
        }
    }

    @Test(timeout = 10000) fun blockedCommitRunsOffCallerAndDoesNotBlockSnapshotOrSetUser() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var writerThread: String? = null
        val storage = object : UserIdStorage {
            var uid: String? = null
            override fun read() = uid
            override fun write(uid: String) {
                writerThread = Thread.currentThread().name
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                this.uid = uid
            }
            override fun remove() { uid = null }
        }
        val manager = SessionManager(storage)
        val caller = Executors.newSingleThreadExecutor { Thread(it, "sdk-host-caller") }.asCoroutineDispatcher()
        val observer = Executors.newSingleThreadExecutor()
        observer.submit {}.get(1, TimeUnit.SECONDS)
        try {
            val request = async(caller) { manager.execute(manager.snapshot(), { it }) { User(uid = "assigned", ses = "session") } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            try {
                observer.submit {
                    manager.setUser("custom", "custom-session")
                    assertEquals("custom", manager.snapshot().customUser?.custom)
                }.get(1, TimeUnit.SECONDS)
                assertNotEquals("sdk-host-caller", writerThread)
            } finally { release.countDown() }
            assertNull(request.await())
            assertEquals("assigned", manager.getUserId())
        } finally {
            release.countDown()
            observer.shutdownNow()
            caller.close()
        }
    }

    @Test(timeout = 10000) fun failedRestorePinsPreviousUidEvenIfStorageRollbackCannotWork() = runBlocking<Unit> {
        val storage = object : UserIdStorage {
            var uid: String? = "previous"
            override fun read() = uid
            override fun write(uid: String) { this.uid = uid; throw IOException("applied in memory but not persisted") }
            override fun remove() { uid = null }
        }
        val manager = SessionManager(storage)
        try { manager.restoreUserId("rejected"); fail("restore succeeded") } catch (_: IOException) { }
        assertEquals("rejected", storage.uid) // The platform-side cache really is dirty.
        assertEquals("previous", manager.getUserId())
        manager.execute(manager.snapshot(), { it }) { outgoing ->
            assertEquals(User(uid = "previous"), outgoing)
            null
        }
    }

    @Test(timeout = 10000) fun queuedRestoreAndResetCannotBeOvertakenByOldPersistence() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = object : UserIdStorage {
            var uid: String? = null
            override fun read() = uid
            override fun write(uid: String) {
                if (uid == "old") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                this.uid = uid
            }
            override fun remove() { uid = null }
        }
        val manager = SessionManager(storage)
        val first = async { manager.execute(manager.snapshot(), { it }) { User(uid = "old", ses = "old-session") } }
        // Let the request reach the independent IO queue before blocking the test thread.
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        val restore = manager.beginRestore("restored")
        val reset = manager.beginReset()
        assertNull(manager.snapshot().knownUser)
        release.countDown()
        restore.await(); reset.await()
        assertNull(first.await())
        assertNull(storage.uid)
        assertNull(manager.getUserId())
    }
}
