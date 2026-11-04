package ai.gravityfield.gravity_sdk.prefs

import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.session.SessionManager
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvalidStoredUserIdTest {
    private val preferences: SharedPreferences
        get() = RuntimeEnvironment.getApplication().getSharedPreferences("invalid-uid", Context.MODE_PRIVATE)

    @Test fun nonStringStoredValuesAreAbsentToTheGetter() = runBlocking<Unit> {
        val values: List<SharedPreferences.Editor.() -> SharedPreferences.Editor> = listOf(
            { putInt("gravity_user_id", 42) },
            { putLong("gravity_user_id", 42L) },
            { putFloat("gravity_user_id", 42f) },
            { putBoolean("gravity_user_id", true) },
            { putStringSet("gravity_user_id", setOf("foreign")) },
        )
        for (putValue in values) {
            assertTrue(preferences.edit().clear().putValue().commit())
            val manager = SessionManager(SharedPreferencesUserIdStorage(preferences))
            assertNull(manager.getUserId())
        }
    }

    @Test fun resetRemovesNonStringUid() = runBlocking<Unit> {
        assertTrue(preferences.edit().putInt("gravity_user_id", 42).commit())
        val manager = SessionManager(SharedPreferencesUserIdStorage(preferences))
        manager.resetUser()
        assertFalse(preferences.contains("gravity_user_id"))
        assertNull(manager.getUserId())
    }

    @Test fun restoreReplacesNonStringUid() = runBlocking<Unit> {
        assertTrue(preferences.edit().putInt("gravity_user_id", 42).commit())
        val manager = SessionManager(SharedPreferencesUserIdStorage(preferences))
        manager.restoreUserId("x")
        assertEquals("x", preferences.getString("gravity_user_id", null))
        assertEquals("x", manager.getUserId())
    }

    @Test fun firstAnonymousRequestOverwritesNonStringUidWithServerUid() = runBlocking<Unit> {
        assertTrue(preferences.edit().putInt("gravity_user_id", 42).commit())
        val manager = SessionManager(SharedPreferencesUserIdStorage(preferences))
        val response = manager.execute(manager.snapshot(), { it }) { outgoing ->
            assertEquals(User(), outgoing)
            User(uid = "assigned", ses = "session")
        }
        assertNotNull(response)
        assertEquals("assigned", preferences.getString("gravity_user_id", null))
        assertEquals("assigned", manager.getUserId())
    }
}
