package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Options(
    val isReturnCounter: Boolean = false,
    val isReturnUserInfo: Boolean = false,
    val isReturnAnalyticsMetadata: Boolean = false,
    val isImplicitPageview: Boolean = false,
    val isImplicitImpression: Boolean = true,
) {
    val isBuildEngagementUrl: Boolean = true
}
