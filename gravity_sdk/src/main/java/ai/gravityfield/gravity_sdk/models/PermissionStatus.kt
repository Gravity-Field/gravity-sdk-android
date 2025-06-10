package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PermissionStatus {
    @SerialName("granted")
    GRANTED,

    @SerialName("denied")
    DENIED,

    @SerialName("unknown")
    UNKNOWN
}
