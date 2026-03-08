package ai.gravityfield.gravity_sdk.utils

import android.util.Log

internal object Logger {

    private const val TAG = "GravitySDK"

    var logLevel: LogLevel = LogLevel.NONE
        private set

    fun configure(logLevel: LogLevel) {
        this.logLevel = logLevel
    }

    fun d(prefix: String, message: String) {
        if (LogLevel.DEBUG.priority >= logLevel.priority) {
            Log.d(TAG, "[$prefix] $message")
        }
    }

    fun i(prefix: String, message: String) {
        if (LogLevel.INFO.priority >= logLevel.priority) {
            Log.i(TAG, "[$prefix] $message")
        }
    }

    fun e(prefix: String, message: String, throwable: Throwable? = null) {
        if (LogLevel.ERROR.priority >= logLevel.priority) {
            Log.e(TAG, "[$prefix] $message", throwable)
        }
    }
}
