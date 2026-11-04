package ai.gravityfield.gravity_sdk.prefs

import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.session.SessionManager
import android.content.SharedPreferences
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SharedPreferencesUserIdStorageTest {
    /** Models Android commitToMemory(): a failed commit still applies the value in this process. */
    private class Preferences(var uid: Any? = "stored", var accepted: Boolean = true) {
        var readFailure: RuntimeException? = null
        val delegate: SharedPreferences
        init {
            var pending: Any? = uid
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                when (method.name) {
                    "putString" -> { assertEquals("gravity_user_id", args!![0]); pending = args[1] as String?; editor }
                    "remove" -> { assertEquals("gravity_user_id", args!![0]); pending = null; editor }
                    "commit" -> { uid = pending; accepted }
                    "apply" -> { uid = pending; null }
                    else -> throw AssertionError(method.name)
                }
            } as SharedPreferences.Editor
            delegate = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                when (method.name) {
                    "edit" -> { pending = uid; editor }
                    "getString" -> {
                        assertEquals("gravity_user_id", args!![0])
                        readFailure?.let { throw it }
                        uid
                    }
                    else -> throw AssertionError(method.name)
                }
            } as SharedPreferences
        }
    }

    @Test fun refusedSaveThrowsAndRollsBackAppliedMemory() {
        val preferences = Preferences(accepted = false)
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        try { storage.write("rejected"); fail("refused write succeeded") } catch (_: IOException) { }
        assertEquals("stored", storage.read())
    }
    @Test fun refusedRemovalThrowsAndRollsBackAppliedMemory() {
        val preferences = Preferences(accepted = false)
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        try { storage.remove(); fail("refused removal succeeded") } catch (_: IOException) { }
        assertEquals("stored", storage.read())
    }
    @Test fun readsCompatibleKeyAndAcceptsConfirmedWrites() {
        val storage = SharedPreferencesUserIdStorage(Preferences().delegate)
        assertEquals("stored", storage.read())
        storage.write("uid")
        assertEquals("uid", storage.read())
        storage.remove()
        assertNull(storage.read())
    }
    @Test fun refusedSaveOfNonStringUidRollsBackToAbsence() {
        val preferences = Preferences(uid = 42, accepted = false)
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        try { storage.write("rejected"); fail("refused write succeeded") } catch (_: IOException) { }
        assertNull(preferences.uid)
        assertNull(storage.read())
    }
    @Test fun refusedRemovalOfNonStringUidRollsBackToAbsence() {
        val preferences = Preferences(uid = 42, accepted = false)
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        try { storage.remove(); fail("refused removal succeeded") } catch (_: IOException) { }
        assertNull(preferences.uid)
        assertNull(storage.read())
    }
    @Test fun genuineReadFailureIsNotTreatedAsAnAbsentUid() {
        val expected = SecurityException("storage access denied")
        val preferences = Preferences().apply { readFailure = expected }
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        try { storage.read(); fail("read failure was hidden") } catch (error: SecurityException) {
            assertSame(expected, error)
        }
    }
    @Test fun failedRestoreNeverSendsRejectedAppliedUid() = runBlocking<Unit> {
        val preferences = Preferences(accepted = false)
        val storage = SharedPreferencesUserIdStorage(preferences.delegate)
        val manager = SessionManager(storage)
        try { manager.restoreUserId("rejected"); fail("restore succeeded") } catch (_: IOException) { }
        assertEquals("stored", manager.getUserId())
        manager.execute(manager.snapshot(), { it }) { outgoing ->
            assertEquals(User(uid = "stored"), outgoing)
            null
        }
    }
}
