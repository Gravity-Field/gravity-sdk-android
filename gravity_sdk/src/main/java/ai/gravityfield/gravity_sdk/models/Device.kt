package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val userAgent: String,
    val id: String?,
    val tracking: String?,
    val permission: NotificationPermissionStatus?,
)
