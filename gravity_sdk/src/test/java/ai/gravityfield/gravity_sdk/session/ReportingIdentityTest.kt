package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReportingIdentityTest {
    private class Storage(var uid: String? = "persisted") : UserIdStorage {
        var gate: CompletableDeferred<Unit>? = null
        var failRead = false
        var failRemove = false
        override suspend fun awaitReady() { gate?.await() }
        override fun read(): String? { check(!failRead); return uid }
        override fun write(uid: String) { this.uid = uid }
        override fun remove() { check(!failRemove); uid = null }
    }

    @Test fun coldStorageWinsOverCustomIdentity() = runBlocking<Unit> {
        val manager = SessionManager(Storage())
        manager.setUser("customer-secret", "custom-session")
        assertEquals(User(uid = "persisted", ses = "custom-session"), manager.reportingUser())
    }

    @Test fun serverSessionWinsOverCustomSession() = runBlocking<Unit> {
        val manager = SessionManager(Storage())
        manager.execute(manager.snapshot(), { it }) { User(uid = "server", ses = "server-session") }
        manager.setUser("customer-secret", "custom-session")
        assertEquals(User(uid = "server", ses = "server-session"), manager.reportingUser())
    }

    @Test fun reportDoesNotWaitForPendingNetworkInitialization() = runBlocking<Unit> {
        val manager = SessionManager(Storage())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val request = async { manager.execute(manager.snapshot(), { it }) { entered.complete(Unit); release.await(); User(uid = "later") } }
        entered.await()
        try { assertEquals("persisted", withTimeout(1000) { manager.reportingUser().uid }) }
        finally { release.complete(Unit); request.await() }
    }

    @Test fun reportWaitsForPendingRestoreAndUsesAcceptedIdentity() = runBlocking<Unit> {
        val storage = Storage()
        val manager = SessionManager(storage)
        manager.reportingUser()
        storage.gate = CompletableDeferred()
        val restore = manager.beginRestore("accepted")
        val report = async(start = CoroutineStart.UNDISPATCHED) { manager.reportingUser() }
        assertFalse(report.isCompleted)
        storage.gate!!.complete(Unit)
        restore.await()
        assertEquals("accepted", report.await().uid)
    }

    @Test fun failedResetNeverRevivesPersistedUid() = runBlocking<Unit> {
        val storage = Storage().apply { failRemove = true }
        val manager = SessionManager(storage)
        manager.reportingUser()
        try { manager.resetUser(); fail("Expected storage failure") } catch (_: IllegalStateException) { }
        assertEquals("persisted", storage.uid)
        assertNull(manager.reportingUser().uid)
    }

    @Test fun unreadableStorageFallsBackAndCancellationPropagates() = runBlocking<Unit> {
        val storage = Storage().apply { failRead = true }
        val manager = SessionManager(storage)
        assertNull(manager.reportingUser().uid)
        storage.gate = CompletableDeferred()
        val report = async(start = CoroutineStart.UNDISPATCHED) { manager.reportingUser() }
        report.cancel()
        try { report.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        storage.gate!!.complete(Unit)
    }
}
