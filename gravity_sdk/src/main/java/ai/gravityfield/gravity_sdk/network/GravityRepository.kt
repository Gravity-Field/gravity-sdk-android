package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.TriggerEvent
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.prefs.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONArray
import org.json.JSONObject

private const val BASE_URL = "https://ev.stellarlabs.ai/v2"
private const val CHOOSE = "/choose"
private const val VISIT = "/visit"
private const val EVENT = "/event"

internal class GravityRepository private constructor() {

    companion object {
        private var _instance = GravityRepository()

        val instance: GravityRepository
            get() = _instance
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val authInterceptor = createClientPlugin("AuthInterceptor") {
        onRequest { request, _ ->
            request.header("Authorization", "Bearer ${GravitySDK.instance.apiKey}")
        }
    }

    private val client = HttpClient(CIO) {
        defaultRequest {
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(
                json = json
            )
        }

        install(authInterceptor)

        install(HttpTimeout) {
            connectTimeoutMillis = 5000
            requestTimeoutMillis = 5000
        }

        install(Logging) {
            logger = Logger.ANDROID
            level = LogLevel.ALL
        }
    }

    private var userIdCache: String? = null
    private var sessionIdCache: String? = null

    private val baseUrl: String
        get() = GravitySDK.instance.proxyUrl ?: BASE_URL

    suspend fun visit(
        pageContext: PageContext,
        options: Options,
        customerUser: User?
    ): CampaignIdsResponse {
        val jsonBody = buildJsonObject {
            put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
            put("device", json.encodeToJsonElement(GravitySDK.instance.device))
            put("type", json.encodeToJsonElement("screenview"))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
        }

        val data = client.post("$baseUrl$VISIT") {
            setBody(jsonBody.toString())
        }.body<String>()

        val json = JSONObject(data).toMap()
        val response = CampaignIdsResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)
        return response
    }

    suspend fun event(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        options: Options,
        customerUser: User?
    ): CampaignIdsResponse {
        val jsonBody = buildJsonObject {
            put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
            put("device", json.encodeToJsonElement(GravitySDK.instance.device))
            put("data", json.encodeToJsonElement(events))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
        }

        val data = client.post("$baseUrl$EVENT") {
            setBody(jsonBody)
        }.body<String>()

        val json = JSONObject(data).toMap()
        val response = CampaignIdsResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)
        return response
    }

    suspend fun chooseByCampaignId(
        campaignId: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext? = null
    ): ContentResponse {
        val jsonBody = buildJsonObject {
            put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
            put(
                "data",
                JsonArray(listOf(json.encodeToJsonElement(mapOf("campaignId" to campaignId))))
            )
            put("device", json.encodeToJsonElement(GravitySDK.instance.device))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
            put("contentSettings", json.encodeToJsonElement(contentSettings))
        }

        val data = client.post("$baseUrl$CHOOSE") {
            setBody(jsonBody)
        }.body<String>()

        val json = JSONObject(data).toMap()
        val response = ContentResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)
        return response
    }

    suspend fun chooseBySelector(
        selector: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext? = null
    ): ContentResponse {
        val jsonBody = buildJsonObject {
            put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
            put("data", JsonArray(listOf(json.encodeToJsonElement(mapOf("selector" to selector)))))
            put("device", json.encodeToJsonElement(GravitySDK.instance.device))
            put("user", json.encodeToJsonElement(userForRequest(customerUser)))
            put("ctx", json.encodeToJsonElement(pageContext))
            put("options", json.encodeToJsonElement(options))
            put("contentSettings", json.encodeToJsonElement(contentSettings))
        }

        val data = client.post("$baseUrl$CHOOSE") {
            setBody(jsonBody)
        }.body<String>()

        val json = JSONObject(data).toMap()
        val response = ContentResponse.fromJson(json)
        saveUserIfNeeded(customerUser, response.user)
        return response
    }

    internal suspend fun trackEngagementEvent(urls: List<String>) {
        urls.forEach { client.get(it) }
    }

    private fun userForRequest(customerUser: User?): User {
        if (customerUser != null) {
            return customerUser
        } else if (userIdCache != null && sessionIdCache != null) {
            return User(uid = userIdCache, ses = sessionIdCache)
        } else {
            val userId = Prefs.getUserId()
            return User(uid = userId, ses = null)
        }
    }

    private fun saveUserIfNeeded(customerUser: User?, contentResponseUser: User?) {
        if (customerUser != null) return

        val uid = contentResponseUser?.uid
        val ses = contentResponseUser?.ses

        if (uid != null && ses != null) {
            Prefs.setUserId(uid)
            userIdCache = uid
            sessionIdCache = ses
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
