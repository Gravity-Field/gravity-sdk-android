package ai.gravityfield.gravity_sdk.prefs

import ai.gravityfield.gravity_sdk.session.UserIdStorage
import android.content.SharedPreferences
import java.io.IOException

/** Invoked only on the session storage IO queue; commit acknowledges durable identity changes. */
internal class SharedPreferencesUserIdStorage(private val preferences: SharedPreferences) : UserIdStorage {
    override fun read(): String? = try {
        preferences.getString(KEY, null)
    } catch (_: ClassCastException) {
        // A foreign value is not a UID; reset, restore or a server response can replace it.
        null
    }
    override fun write(uid: String) = change(uid, "Cannot persist server UID")
    override fun remove() = change(null, "Cannot remove server UID")

    private fun change(uid: String?, message: String) {
        val previous = read()
        try {
            if (!editor(uid).commit()) throw IOException(message)
        } catch (error: Exception) {
            // Android applies commitToMemory before disk IO, even when commit returns false.
            // Restore the process cache; failure of this best-effort rollback never hides the original error.
            try { editor(previous).apply() } catch (rollback: Exception) { error.addSuppressed(rollback) }
            throw error
        }
    }
    private fun editor(uid: String?): SharedPreferences.Editor = preferences.edit().let {
        if (uid == null) it.remove(KEY) else it.putString(KEY, uid)
    }
    private companion object { const val KEY = "gravity_user_id" }
}
