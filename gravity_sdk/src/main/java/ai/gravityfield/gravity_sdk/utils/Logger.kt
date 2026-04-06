package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.network.GravityRepository
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object Logger {

    private const val TAG = "GravitySDK"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    fun e(
        prefix: String,
        message: String,
        throwable: Throwable? = null,
        sendToBack: Boolean = true,
    ) {
        val fullMessage = "[$prefix] $message"
        if (LogLevel.ERROR.priority >= logLevel.priority) {
            Log.e(TAG, fullMessage, throwable)
        }

        if (sendToBack) {
            ioScope.launch {
                GravityRepository.instance.sendErrorMessage(
                    message = fullMessage,
                    stacktrace = throwable?.stackTraceToString() ?: ""
                )
            }
        }
    }
}
