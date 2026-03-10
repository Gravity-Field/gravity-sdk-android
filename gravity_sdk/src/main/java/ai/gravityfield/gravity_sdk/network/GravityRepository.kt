package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.TriggerEvent
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.prefs.Prefs
import ai.gravityfield.gravity_sdk.utils.DeviceUtils
import ai.gravityfield.gravity_sdk.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger

private const val BASE_URL = "https://evs-01.gravityfield.ai/v2"
private const val CHOOSE = "/choose"
private const val VISIT = "/visit"
private const val EVENT = "/event"

internal class GravityRepository private constructor() {

    companion object {
        private var _instance = GravityRepository()

        val instance: GravityRepository
            get() = _instance

        private const val TAG = "Repository"
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

    private val client = HttpClient(Android) {
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
            logger = object : KtorLogger {
                override fun log(message: String) {
                    Logger.d(prefix = "HTTP", message)
                }
            }
            level = KtorLogLevel.ALL
        }
    }

    private var userIdCache: String? = null
    private var sessionIdCache: String? = null

    private val baseUrl: String
        get() = GravitySDK.instance.proxyUrl ?: BASE_URL

    suspend fun visit(
        pageContext: PageContext,
        options: Options,
        customerUser: User?,
    ): CampaignIdsResponse? {
        return try {
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("type", json.encodeToJsonElement("screenview"))
                put("user", json.encodeToJsonElement(userForRequest(customerUser)))
                put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                put("options", json.encodeToJsonElement(options))
            }

            val data = client.post("$baseUrl$VISIT") {
                setBody(jsonBody.toString())
            }.body<String>()

            val json = JSONObject(data).toMap()
            val response = CampaignIdsResponse.fromJson(json)
            saveUserIfNeeded(customerUser, response.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "visit failed: ${e.message}", e)
            null
        }
    }

    suspend fun event(
        events: List<TriggerEvent>,
        pageContext: PageContext,
        options: Options,
        customerUser: User?,
    ): CampaignIdsResponse? {
        return try {
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("data", json.encodeToJsonElement(events))
                put("user", json.encodeToJsonElement(userForRequest(customerUser)))
                put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                put("options", json.encodeToJsonElement(options))
            }

            val data = client.post("$baseUrl$EVENT") {
                setBody(jsonBody)
            }.body<String>()

            val json = JSONObject(data).toMap()
            val response = CampaignIdsResponse.fromJson(json)
            saveUserIfNeeded(customerUser, response.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "event failed: ${e.message}", e)
            null
        }
    }

    suspend fun chooseByCampaignId(
        campaignId: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext,
    ): ContentResponse? {
        return try {
            val data = buildJsonObject {
                put("campaignId", Json.parseToJsonElement(campaignId))
                put("option", json.encodeToJsonElement(contentSettings))
            }
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("data", JsonArray(listOf(data)))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("user", json.encodeToJsonElement(userForRequest(customerUser)))
                put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                put("options", json.encodeToJsonElement(options))
            }

            val stringData = client.post("$baseUrl$CHOOSE") {
                setBody(jsonBody)
            }.body<String>()

            val json = JSONObject(stringData).toMap()
            val response = ContentResponse.fromJson(json)
            saveUserIfNeeded(customerUser, response.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "chooseByCampaignId failed: ${e.message}", e)
            null
        }
    }

    suspend fun chooseBySelector(
        selector: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext,
    ): ContentResponse? {
        return try {
            val data = buildJsonObject {
                put("selector", Json.parseToJsonElement(selector))
                put("option", json.encodeToJsonElement(contentSettings))
            }
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("data", JsonArray(listOf(data)))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("user", json.encodeToJsonElement(userForRequest(customerUser)))
                put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                put("options", json.encodeToJsonElement(options))
            }

            val stringData = client.post("$baseUrl$CHOOSE") {
                setBody(jsonBody)
            }.body<String>()

            val json = JSONObject(stringData).toMap()
            val response = ContentResponse.fromJson(json)
            saveUserIfNeeded(customerUser, response.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "chooseBySelector failed: ${e.message}", e)
            null
        }
    }

    suspend fun chooseByGroup(
        group: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext,
    ): ContentResponse? {
        return try {
            val data = buildJsonObject {
                put("group", Json.parseToJsonElement(group))
                put("option", json.encodeToJsonElement(contentSettings))
            }
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("data", JsonArray(listOf(data)))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("user", json.encodeToJsonElement(userForRequest(customerUser)))
                put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                put("options", json.encodeToJsonElement(options))
            }

            val stringData = client.post("$baseUrl$CHOOSE") {
                setBody(jsonBody)
            }.body<String>()

            val json = JSONObject(stringData).toMap()
            val response = ContentResponse.fromJson(json)
            saveUserIfNeeded(customerUser, response.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "chooseByGroup failed: ${e.message}", e)
            null
        }
    }

    internal suspend fun trackEngagementEvent(urls: List<String>) {
        urls.forEach {
            try {
                client.get(it)
            } catch (e: Exception) {
                Logger.e(TAG, "trackEngagementEvent failed: ${e.message}", e)
            }
        }
    }

    private fun userForRequest(customerUser: User?): User {
        if (customerUser != null) {
            return customerUser
        } else if (userIdCache != null && sessionIdCache != null) {
            return User(uid = userIdCache, ses = sessionIdCache)
        } else if (userIdCache == null && sessionIdCache != null) {
            val userId = Prefs.getUserId()
            return User(uid = userId, ses = sessionIdCache);
        } else {
            val userId = Prefs.getUserId()
            return User(uid = userId, ses = null)
        }
    }

    private fun mixPageContextAttributes(pageContext: PageContext): PageContext {
        val attributes = pageContext.attributes.toMutableMap()
        attributes.putAll(DeviceUtils.contextAttributes)
        return pageContext.copy(attributes = attributes)
    }

    private fun saveUserIfNeeded(customerUser: User?, contentResponseUser: User?) {
        if (customerUser != null) return

        val uid = contentResponseUser?.uid
        val ses = contentResponseUser?.ses

        if (uid != null) {
            Prefs.setUserId(uid)
            userIdCache = uid;
        }

        if (ses != null) {
            sessionIdCache = ses
        }
    }
}

fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keysItr: Iterator<String> = this.keys()
    while (keysItr.hasNext()) {
        val key = keysItr.next()
        val rawValue = this.get(key)
        if (rawValue == JSONObject.NULL) continue
        var value: Any = rawValue
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
        val rawValue = this[i]
        if (rawValue == JSONObject.NULL) continue
        var value: Any = rawValue
        when (value) {
            is JSONArray -> value = value.toList()
            is JSONObject -> value = value.toMap()
        }
        list.add(value)
    }
    return list
}
