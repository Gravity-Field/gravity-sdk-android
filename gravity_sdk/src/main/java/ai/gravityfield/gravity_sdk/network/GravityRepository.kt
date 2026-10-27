package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.GravityDataResponse
import ai.gravityfield.gravity_sdk.models.internal.LogMessage
import ai.gravityfield.gravity_sdk.models.internal.LogMessageLevel
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.TriggerEvent
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.models.encodeEvents
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
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

        private fun createAuthInterceptor() = createClientPlugin("AuthInterceptor") {
            onRequest { request, _ ->
                if (!request.headers.contains("Authorization")) {
                    request.header("Authorization", "Bearer ${GravitySDK.instance.apiKey}")
                }
            }
        }

        private fun createHttpClient(json: Json, auth: Boolean): HttpClient {
            return HttpClient(Android) {
                defaultRequest {
                    contentType(ContentType.Application.Json)
                }

                install(ContentNegotiation) {
                    json(json = json)
                }

                if (auth) {
                    install(createAuthInterceptor())
                }

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
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val client = createHttpClient(json, auth = true)
    private val errorReportingClient = createHttpClient(json, auth = false)

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
        raisedAt: Date,
        pageContext: PageContext,
        options: Options,
        customerUser: User?,
    ): CampaignIdsResponse? {
        return try {
            val jsonBody = buildJsonObject {
                put("sec", json.encodeToJsonElement(GravitySDK.instance.section))
                put("device", json.encodeToJsonElement(DeviceUtils.getDevice()))
                put("data", encodeEvents(json, events, raisedAt))
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

    suspend fun chooseByCampaignIdWithDetails(
        campaignId: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext,
    ): GravityDataResponse<ContentResponse>? {
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

            val response = parseContentResponse(stringData)
            saveUserIfNeeded(customerUser, response.data.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "chooseByCampaignIdWithDetails failed: ${e.message}", e)
            null
        }
    }

    suspend fun chooseBySelectorWithDetails(
        selector: String,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User? = null,
        pageContext: PageContext,
    ): GravityDataResponse<ContentResponse>? {
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

            val response = parseContentResponse(stringData)
            saveUserIfNeeded(customerUser, response.data.user)
            response
        } catch (e: Exception) {
            Logger.e(TAG, "chooseBySelectorWithDetails failed: ${e.message}", e)
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

            val response = parseContentResponse(stringData)
            saveUserIfNeeded(customerUser, response.data.user)
            response.data
        } catch (e: Exception) {
            Logger.e(TAG, "chooseByGroup failed: ${e.message}", e)
            null
        }
    }

    internal suspend fun trackEngagementEvent(urls: List<String>, apiKey: String? = null) {
        var failure: Exception? = null
        for (url in urls) {
            try {
                EngagementTransport(client).send(url, apiKey)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    internal suspend fun sendErrorMessage(
        message: String,
        stacktrace: String,
    ) {
        val uid: String?
        val ses: String?
        val customerUser = GravitySDK.instance.user
        if (customerUser != null) {
            uid = customerUser.custom
            ses = customerUser.ses
        } else {
            uid = userIdCache
            ses = sessionIdCache
        }
        val sec = GravitySDK.instance.section

        val logMessage = LogMessage(
            message = message,
            level = LogMessageLevel.ERROR,
            sec = sec,
            uid = uid ?: "",
            ses = ses ?: "",
            sdkVersion = DeviceUtils.getSDKVersion(),
            platform = DeviceUtils.getPlatformVersion() ?: "",
            stacktrace = stacktrace,
        )

        try {
            errorReportingClient.post("https://sdk-sentry.gravityfield.ai/error") {
                setBody(json.encodeToJsonElement(logMessage))
            }.body<String>()
        } catch (e: Exception) {
            Logger.e(TAG, "sendErrorMessage failed: ${e.message}", e, sendToBack = false)
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
            // variables keep nulls and unknown keys for Variables.raw
            is JSONObject -> value = if (key == VARIABLES_KEY) value.toRawMap() else value.toMap()
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

private const val VARIABLES_KEY = "variables"

/** Converts to plain Kotlin values keeping JSON nulls, unlike [toMap]. */
internal fun JSONObject.toRawMap(): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    val keysItr: Iterator<String> = this.keys()
    while (keysItr.hasNext()) {
        val key = keysItr.next()
        map[key] = this.get(key).toRawValue()
    }
    return map
}

internal fun JSONArray.toRawList(): List<Any?> {
    return (0 until this.length()).map { this[it].toRawValue() }
}

private fun Any.toRawValue(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> toRawMap()
    is JSONArray -> toRawList()
    else -> this
}

/** Drops JSON nulls at every level, the shape typed fromJson parsers expect. */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.withoutNulls(): Map<String, Any> {
    val map = linkedMapOf<String, Any>()
    for ((key, value) in this) {
        map[key] = value?.withoutNulls() ?: continue
    }
    return map
}

@Suppress("UNCHECKED_CAST")
private fun Any.withoutNulls(): Any = when (this) {
    is Map<*, *> -> (this as Map<String, Any?>).withoutNulls()
    is List<*> -> mapNotNull { it?.withoutNulls() }
    else -> this
}

/** Parses a /choose answer into the model and the raw JSON of the same body. */
internal fun parseContentResponse(body: String): GravityDataResponse<ContentResponse> {
    val jsonObject = JSONObject(body)
    return GravityDataResponse(
        data = ContentResponse.fromJson(jsonObject.toMap()),
        json = jsonObject.toRawMap(),
    )
}
