package ai.gravityfield.gravity_sdk.network

internal class EngagementHttpException(val status: Int) : Exception("Engagement HTTP status: $status")
