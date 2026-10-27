package ai.gravityfield.gravity_sdk.models

/**
 * A parsed model together with the raw JSON of the same server response.
 * [json] keeps every key, including the ones the model does not know, and
 * JSON null values.
 */
data class GravityDataResponse<T>(
    val data: T,
    val json: Map<String, Any?>,
)
