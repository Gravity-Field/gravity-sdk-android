package ai.gravityfield.gravity_sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import org.json.JSONArray
import org.json.JSONObject

private const val BASE_URL = "https://mock.apidog.com/m1/807903-786789-default"
private const val GET_CONTENT = "$BASE_URL/choose"

class GravityRepository {

    private val client = HttpClient(CIO)

    suspend fun sendEvent() {

    }

    suspend fun getContent(templateId: String): ContentResponse {
        val data = client.post(GET_CONTENT, fun HttpRequestBuilder.() {
            parameter("templateId", templateId)
        }).body<String>()
        val json = JSONObject(data).toMap()
        return ContentResponse.fromJson(json)
    }
}

fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keysItr: Iterator<String> = this.keys()
    while (keysItr.hasNext()) {
        val key = keysItr.next()
        var value: Any = this.get(key)
        when (value) {
            is JSONArray -> value = value.toList()
            is JSONObject -> value = value.toMap()
        }
        map[key] = value
    }
    return map
}

fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until this.length()) {
        var value: Any = this[i]
        when (value) {
            is JSONArray -> value = value.toList()
            is JSONObject -> value = value.toMap()
        }
        list.add(value)
    }
    return list
}
