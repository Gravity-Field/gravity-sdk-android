package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.models.external.ContentSettings
import ai.gravityfield.gravity_sdk.models.external.Options
import ai.gravityfield.gravity_sdk.models.external.PageContext
import ai.gravityfield.gravity_sdk.models.external.TriggerEvent
import ai.gravityfield.gravity_sdk.prefs.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONArray
import org.json.JSONObject

private const val BASE_URL = "https://mock.apidog.com/m1/807903-786789-default"
private const val GET_CONTENT = "$BASE_URL/choose"
private const val VISIT = "$BASE_URL/visit"
private const val EVENT = "$BASE_URL/event"

class GravityRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val client = HttpClient(CIO) {
        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(
                json = json
            )
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 5000
            requestTimeoutMillis = 5000
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
        }
    }

    private val prefs = Prefs()

    private var userIdCache: String? = null
    private var sessionIdCache: String? = null

    suspend fun visit(
        pageContext: PageContext,
        options: Options,
        customerUser: User?
    ): ContentResponse {
        val jsonBody = buildJsonObject {
            put("type", json.encodeToJsonElement("mobile"))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
        }

        val data = client.post(VISIT) {
            setBody(jsonBody.toString())
        }.body<String>()

        val json = JSONObject(data).toMap()
        // todo: change ContentResponse to campaignIds response
        val response = ContentResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)

        return response
    }

    suspend fun event(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        options: Options,
        customerUser: User?
    ): ContentResponse {
        val jsonBody = buildJsonObject {
            put("events", json.encodeToJsonElement(events))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
        }

        val data = client.post(EVENT) {
            setBody(jsonBody)
        }.body<String>()

        val json = JSONObject(data).toMap()
        // todo: change ContentResponse to campaignIds response
        val response = ContentResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)

        return response
    }

    suspend fun getContent(
        templateId: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext? = null
    ): ContentResponse {
        val data = client.post(
            GET_CONTENT,
        ) {
            parameter("templateId", templateId)
            setBody(userForRequest(customerUser))
        }.body<String>()

        val json = JSONObject(data).toMap()
        val response = ContentResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)
        return response
    }

    internal suspend fun trackEngagementEvent(urls: List<String>) {
        // todo: uncomment when back will send urls values
        // urls.forEach { client.get(it) }
    }

    private fun userForRequest(customerUser: User?): User {
        if (customerUser != null) {
            return customerUser
        } else if (userIdCache != null && sessionIdCache != null) {
            return User(uid = userIdCache, ses = sessionIdCache)
        } else {
            val userId = prefs.getUserId()
            return User(uid = userId, ses = null)
        }
    }

    private fun saveUserIfNeeded(customerUser: User?, contentResponseUser: User?) {
        if (customerUser != null) return

        if (contentResponseUser != null) {
            prefs.setUserId(contentResponseUser.uid!!)
            userIdCache = contentResponseUser.uid
            sessionIdCache = contentResponseUser.ses
        }
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
