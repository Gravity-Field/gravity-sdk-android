package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionRevisionTest {
    @Test fun refusedResetClearsMemoryAndListenerButReportsFailure() = runBlocking<Unit> {
        val manager = SessionManager(object : UserIdStorage {
            override fun read() = "stored"
            override fun write(uid: String) = Unit
            override fun remove(): Unit = throw IOException("remove refused")
        })
        val heard = mutableListOf<String?>()
        manager.setUserIdListener { heard.add(it) }
        assertEquals("stored", manager.getUserId())
        manager.setUser("custom", "session")
        assertTrue(runCatching { manager.resetUser() }.exceptionOrNull() is IOException)
        assertNull(manager.getUserId())
        assertNull(manager.customUser)
        assertEquals(User(), manager.cachedUser())
        assertEquals(listOf<String?>(null), heard)
        manager.execute(manager.snapshot(), { null }) { user -> assertEquals(User(), user); user }
    }

    @Test(timeout = 10000) fun coldTrackingSnapshotKeepsStoredIdentityAcrossReset() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var uid: String? = "original"
        val manager = SessionManager(object : UserIdStorage {
            override fun read(): String? {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return uid
            }
            override fun write(uid: String) { error("old response must not write") }
            override fun remove() { uid = null }
        })
        val identity = manager.snapshot()
        val event = async(Dispatchers.IO) {
            manager.execute(identity, { it }, deliverAlways = true) { user ->
                assertEquals(User(uid = "original"), user)
                User(uid = "late", ses = "late-session")
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val reset = manager.beginReset()
            release.countDown()
            reset.await()
            assertNull(event.await())
            assertNull(manager.getUserId())
        } finally { release.countDown() }
    }

    @Test(timeout = 10000) fun failedDeferredReadAfterResetStillDeliversAnonymousEvent() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var reads = 0
        val manager = SessionManager(object : UserIdStorage {
            override fun read(): String? {
                if (++reads == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    throw IOException("cold read refused")
                }
                return null
            }
            override fun write(uid: String) = Unit
            override fun remove() = Unit
        })
        val identity = manager.snapshot()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val reset = manager.beginReset()
            release.countDown()
            reset.await()
            var sent = false
            assertNull(manager.execute(identity, { null }, deliverAlways = true) { user ->
                assertEquals(User(), user)
                sent = true
                user
            })
            assertTrue("A failed original read must not drop raised analytics after reset", sent)
        } finally { release.countDown() }
    }

    @Test fun emptySessionReplacesCacheWhileEmptyUidIsIgnored() = runBlocking<Unit> {
        var stored: String? = null
        val manager = SessionManager(object : UserIdStorage {
            override fun read() = stored
            override fun write(uid: String) { stored = uid }
            override fun remove() { stored = null }
        })
        manager.execute(manager.snapshot(), { it }) { User(uid = "known", ses = "session") }
        manager.execute(manager.snapshot(), { it }) { User(uid = "", ses = "") }
        assertEquals(User(uid = "known", ses = ""), manager.cachedUser())
        assertEquals("known", stored)
    }
}
