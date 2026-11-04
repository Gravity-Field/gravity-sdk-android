package ai.gravityfield.gravity_sdk.session

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DeferredIdentityStorageTest {
    @Test fun consecutiveResetsBeforeInitializeNotifyOnlyForStoredUid() = runBlocking<Unit> {
        for (stored in listOf(null, "stored")) for (published in listOf(false, true)) {
            val ready = CompletableDeferred<Unit>()
            var disk = stored
            val sessions = SessionManager(object : UserIdStorage {
                override val isReady get() = ready.isCompleted
                override suspend fun awaitReady() { ready.await() }
                override fun read() = disk
                override fun write(uid: String) { disk = uid }
                override fun remove() { disk = null }
            })
            val heard = mutableListOf<String?>()
            sessions.setUserIdListener { heard.add(it) }
            val restore = if (published) sessions.beginRestore("published") else null
            val first = sessions.beginReset()
            val second = sessions.beginReset()
            assertEquals(if (published) listOf("published", null) else emptyList<String?>(), heard)
            ready.complete(Unit)
            sessions.initialize()
            restore?.await()
            first.await()
            second.await()
            assertNull(sessions.getUserId())
            assertNull(disk)
            assertEquals(when {
                published -> listOf("published", null)
                stored != null -> listOf(null)
                else -> emptyList<String?>()
            }, heard)
        }
    }

    @Test(timeout = 15000) fun failedRestoreAfterReadinessChangeReconcilesPreviouslyPublishedIdentity() = runBlocking<Unit> {
        for (restoreFirst in listOf(true, false)) {
            val ready = CompletableDeferred<Unit>()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            var available = false
            var attempts = 0
            val sessions = SessionManager(object : UserIdStorage {
                override val isReady get() = available
                override suspend fun awaitReady() { ready.await() }
                override fun read() = "stored"
                private fun refuse() {
                    if (++attempts == 1) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    throw IOException("write refused")
                }
                override fun write(uid: String) = refuse()
                override fun remove() = refuse()
            })
            val heard = mutableListOf<String?>()
            sessions.setUserIdListener { heard.add(it) }
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { if (restoreFirst) sessions.restoreUserId("first") else sessions.resetUser() }
            }
            try {
                assertEquals(if (restoreFirst) listOf("first") else emptyList<String?>(), heard)
                available = true
                ready.complete(Unit)
                sessions.initialize()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val last = async(start = CoroutineStart.UNDISPATCHED) { runCatching { sessions.restoreUserId("last") } }
                release.countDown()
                assertTrue(first.await().exceptionOrNull() is IOException)
                assertTrue(last.await().exceptionOrNull() is IOException)
                assertEquals("stored", sessions.getUserId())
                assertEquals(if (restoreFirst) listOf("first", "stored") else emptyList<String?>(), heard)
            } finally { ready.complete(Unit); release.countDown() }
        }
    }
}
