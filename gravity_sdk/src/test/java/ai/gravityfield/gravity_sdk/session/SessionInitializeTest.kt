package ai.gravityfield.gravity_sdk.session

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionInitializeTest {
    @Test(timeout = 15000) fun reinitializeDoesNotAnnounceTransientUidDuringPendingRestore() = runBlocking<Unit> {
        for (refused in listOf(false, true)) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            var stored: String? = "previous"
            val sessions = SessionManager(object : UserIdStorage {
                override fun read() = stored
                override fun write(uid: String) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    if (refused) throw IOException("write refused")
                    stored = uid
                }
                override fun remove() { stored = null }
            })
            val heard = mutableListOf<String?>()
            sessions.setUserIdListener { heard.add(it) }
            sessions.initialize()
            assertEquals("previous", sessions.getUserId())
            assertTrue("Stored reads must not notify", heard.isEmpty())
            sessions.execute(sessions.snapshot(), { it }) { ai.gravityfield.gravity_sdk.models.User(uid = "previous", ses = "session") }
            assertEquals(listOf("previous"), heard)
            val pending = async(Dispatchers.IO) { runCatching { sessions.restoreUserId("restored") } }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                sessions.initialize()
                assertEquals("Reinitialization must not publish the pending restore's temporary null", listOf("previous"), heard)
                release.countDown()
                assertEquals(refused, pending.await().isFailure)
                assertEquals(if (refused) "previous" else "restored", sessions.getUserId())
                assertEquals(if (refused) listOf("previous") else listOf("previous", "restored"), heard)
            } finally { release.countDown() }
        }
    }
}
