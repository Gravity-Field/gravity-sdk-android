package ai.gravityfield.gravity_sdk.prefs

import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CompletableDeferred
import android.content.Context
import ai.gravityfield.gravity_sdk.session.UserIdStorage

private const val keyDeviceId = "gravity_device_id"

internal object Prefs : UserIdStorage {

    @Volatile private lateinit var prefs: SharedPreferencesSettings
    @Volatile private lateinit var userIds: SharedPreferencesUserIdStorage
    @Volatile private var readiness = CompletableDeferred<Unit>()
    override val isReady: Boolean get() = ::userIds.isInitialized
    override suspend fun awaitReady() { readiness.await() }

    fun initialize(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences(
            "${context.packageName}_preferences", Context.MODE_PRIVATE,
        )
        prefs = SharedPreferencesSettings(preferences)
        userIds = SharedPreferencesUserIdStorage(preferences)
        readiness.complete(Unit)
    }

    override fun read(): String? = userIds.read()
    override fun write(uid: String) = userIds.write(uid)
    override fun remove() = userIds.remove()

    fun setDeviceId(uid: String) {
        prefs.putString(keyDeviceId, uid)
    }

    fun getDeviceId(): String? = try {
        prefs.getStringOrNull(keyDeviceId)
    } catch (_: ClassCastException) {
        // DeviceUtils replaces a foreign value through the normal device ID creation path.
        null
    }
}
