package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.Device
import ai.gravityfield.gravity_sdk.prefs.Prefs
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.util.UUID

internal object DeviceUtils {
    private var userAgent: String = ""
    var contextAttributes: Map<String, String> = emptyMap()
        private set

    fun initialize(context: Context) {
        userAgent = getUserAgent(context)
        contextAttributes = getContextAttributes(context)
    }

    fun getDevice(): Device {
        return Device(
            userAgent = userAgent,
            id = getDeviceId(),
            permission = GravitySDK.instance.notificationPermissionStatus,
            tracking = "notSupported",
        )
    }

    private fun getUserAgent(context: Context): String {
        val info: PackageInfo? = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Throwable) {
            null
        }
        val appName: String? = try {
            info?.applicationInfo?.loadLabel(context.packageManager)?.toString()
        } catch (e: Throwable) {
            null
        }
        val version = info?.versionName
        val platform = "Android"
        val platformVersion = Build.VERSION.RELEASE
        val model = Build.MODEL
        val device = Build.DEVICE
        val architecture = Build.SUPPORTED_ABIS[0]

        return "$appName/$version ($platform $platformVersion; $model; $device; $architecture)"
    }

    private fun getContextAttributes(context: Context): Map<String, String> {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val version = packageInfo.versionName ?: ""
        val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode
        }.toString()

        val contextAttributes = mapOf(
            "app_version" to "$version+$buildNumber",
            // TODO: SDK version
            "sdk_version" to "0.0.1",
            "app_platform" to "android"

        )
        return contextAttributes
    }

    private fun getDeviceId(): String {
        var deviceId = Prefs.getDeviceId()
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            Prefs.setDeviceId(deviceId)
        }
        return deviceId
    }
}
