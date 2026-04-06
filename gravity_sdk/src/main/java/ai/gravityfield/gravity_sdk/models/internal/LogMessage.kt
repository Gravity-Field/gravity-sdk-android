package ai.gravityfield.gravity_sdk.models.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LogMessageLevel {
    @SerialName("error") ERROR,
    @SerialName("warning") WARNING,
    @SerialName("info") INFO,
    @SerialName("debug") DEBUG,
}

@Serializable
data class LogMessage(
    val message: String,
    val level: LogMessageLevel,
    val sec: String,
    val uid: String,
    val ses: String,
    val sdkVersion: String,
    val platform: String,
    val stacktrace: String = "",
    val tags: Map<String, String> = emptyMap(),
    val extra: Map<String, String> = emptyMap(),
) {
    val sdkType: String = "android_native"
}
