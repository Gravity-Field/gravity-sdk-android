package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.prefs.Prefs
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID

internal object DeviceUtils {
    fun getUserAgent(context: Context): String {
        val info: PackageInfo? = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val appName = info?.applicationInfo?.loadLabel(context.packageManager)?.toString()
        val version = info?.versionName
        val platform = "Android"
        val platformVersion = Build.VERSION.RELEASE
        val model = Build.MODEL
        val device = Build.DEVICE
        val architecture = Build.SUPPORTED_ABIS[0]

        return "$appName/$version ($platform $platformVersion; $model; $device; $architecture)"
    }

    fun getDeviceId(): String {
        var deviceId = Prefs.getDeviceId()
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            Prefs.setDeviceId(deviceId)
        }
        return deviceId
    }
}
