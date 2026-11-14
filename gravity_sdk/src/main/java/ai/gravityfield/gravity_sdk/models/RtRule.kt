package ai.gravityfield.gravity_sdk.models

import kotlinx.serialization.Serializable

/**
 * Recommendation rule sent with a content request, e.g. a filter by category or price.
 * Encoded like the Flutter SDK: `id` and `slots` are always present, as null when not set.
 */
@Serializable
data class RtRule(
    val type: String,
    val conditions: List<RtRuleCondition>,
    val id: Int? = null,
    val slots: List<Int>? = null,
)

/** Condition on a product [field] with its [arguments]. */
@Serializable
data class RtRuleCondition(
    val field: String,
    val arguments: List<RtRuleArgument>,
)

/** Rule argument: an [action] name with its string [value] list. */
@Serializable
data class RtRuleArgument(
    val action: String,
    val value: List<String>,
)
