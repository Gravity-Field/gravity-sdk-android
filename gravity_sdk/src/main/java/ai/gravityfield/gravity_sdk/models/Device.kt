package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String?,
    val userAgent: Map<String, String?>
)
