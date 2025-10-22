package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    @SerialName("ua")
    val userAgent: String,
    val id: String?,
    val tracking: String?,
    val permission: NotificationPermissionStatus?,
)
