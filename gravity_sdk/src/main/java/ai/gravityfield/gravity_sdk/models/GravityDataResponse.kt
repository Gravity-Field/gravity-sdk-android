package ai.gravityfield.gravity_sdk.models

/**
 * A parsed model together with the raw JSON of the same server response.
 * [json] keeps every key, including the ones the model does not know, and
 * JSON null values.
 * [delayTime] is the chosen campaign’s non-negative delay in milliseconds for NoShow
 * requests. The app may wait before rendering; the SDK returns without waiting.
 * Missing, zero or negative server delays and direct getContent requests return 0.
 * The SDK sets a non-negative value; a value passed to the constructor or [copy]
 * is stored as is.
 */
data class GravityDataResponse<T> @JvmOverloads constructor(
    val data: T,
    val json: Map<String, Any?>,
    val delayTime: Int = 0,
)
