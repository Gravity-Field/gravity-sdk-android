package ai.gravityfield.gravity_sdk.prefs

import com.russhwolf.settings.Settings

private const val keyUserId = "gravity_user_id"

internal class Prefs {

    private val prefs = Settings()

    fun setUserId(uid: String) {
        prefs.putString(keyUserId, uid)
    }

    fun getUserId(): String? {
        return prefs.getStringOrNull(keyUserId)
    }
}
