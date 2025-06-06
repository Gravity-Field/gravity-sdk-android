package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.prefs.Prefs
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import java.util.UUID

internal object DeviceUtils {
    fun getUserAgent(context: Context): Map<String, String?> {
        val info: PackageInfo? = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return mapOf(
            "platform" to "Android",
            "platformVersion" to Build.VERSION.RELEASE,
            "architecture" to Build.SUPPORTED_ABIS[0],
            "model" to Build.MODEL,
            "brand" to Build.MANUFACTURER,
            "version" to info?.versionName,
            "mobile" to (uiManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL).toString(),
            "device" to Build.DEVICE,
            "appName" to info?.applicationInfo?.loadLabel(context.packageManager)?.toString(),
            "appVersion" to info?.versionName,
            "packageName" to info?.applicationInfo?.packageName,
            "buildNumber" to getVersionCode(context)
        )
    }

    fun getDeviceId(): String {
        var deviceId = Prefs.getDeviceId()
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            Prefs.setDeviceId(deviceId)
        }
        return deviceId
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode.toString() else packageInfo.versionCode.toString()
    }
}