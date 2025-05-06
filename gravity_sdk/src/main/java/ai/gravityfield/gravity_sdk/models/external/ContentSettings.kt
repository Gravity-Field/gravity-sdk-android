package ai.gravityfield.gravity_sdk.models.external

import kotlinx.serialization.Serializable

@Serializable
data class ContentSettings(
    val skusOnly: Boolean = false,
    val fields: List<String>? = null
)
