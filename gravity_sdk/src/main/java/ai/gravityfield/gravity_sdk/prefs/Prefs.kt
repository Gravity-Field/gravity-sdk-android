package ai.gravityfield.gravity_sdk.prefs

import com.russhwolf.settings.Settings

private const val keyUserId = "gravity_user_id"
private const val keyDeviceId = "gravity_device_id"

internal object Prefs {

    private val prefs = Settings()

    fun setUserId(uid: String) {
        prefs.putString(keyUserId, uid)
    }

    fun getUserId(): String? {
        return prefs.getStringOrNull(keyUserId)
    }

    fun setDeviceId(uid: String) {
        prefs.putString(keyDeviceId, uid)
    }

    fun getDeviceId(): String? {
        return prefs.getStringOrNull(keyDeviceId)
    }
}
