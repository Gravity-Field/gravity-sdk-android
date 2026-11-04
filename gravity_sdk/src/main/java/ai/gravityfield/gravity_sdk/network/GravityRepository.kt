package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.session.SessionManager
import ai.gravityfield.gravity_sdk.models.Device
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
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

private const val BASE_URL = "https://evs-01.gravityfield.ai/v2"
private const val CHOOSE = "/choose"
private const val VISIT = "/visit"
private const val EVENT = "/event"

internal class GravityRepository(
    private val sessions: SessionManager = sessionManager,
    suppliedClient: HttpClient? = null,
    private val section: () -> String = { GravitySDK.instance.section },
    private val device: () -> Device = { DeviceUtils.getDevice() },
    private val endpoint: () -> String = { GravitySDK.instance.proxyUrl ?: BASE_URL },
    private val suppliedErrorClient: HttpClient? = null,
    private val errorReportLimiter: ErrorReportLimiter = ErrorReportLimiter(),
) {

    companion object {
        val sessionManager = SessionManager(Prefs) {
            Logger.e(TAG, "Cannot persist server UID: ${it.message}", it)
        }
        private val _instance by lazy { GravityRepository() }

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

                // Log only request metadata; Ktor's raw logging includes bodies and exception text.
                install(createClientPlugin("SafeHttpDiagnostics") {
                    onRequest { request, _ ->
                        Logger.d("HTTP", "${request.method.value} ${ErrorReportSanitizer.sanitizeUrls(request.url.buildString())}")
                        if (request.headers[HttpHeaders.Authorization] != null) {
                            Logger.d("HTTP", "Authorization: [redacted]")
                        }
                    }
                    onResponse { response ->
                        val request = response.call.request
                        Logger.d("HTTP", "${request.method.value} ${ErrorReportSanitizer.sanitizeUrls(request.url.toString())} ${response.status.value}")
                    }
                })
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val client = suppliedClient ?: createHttpClient(json, auth = true)
    private val errorReportingClient by lazy { suppliedErrorClient ?: createHttpClient(json, auth = false) }

    private val baseUrl: String
        get() = endpoint()

    suspend fun visit(
        pageContext: PageContext,
        options: Options,
        identity: SessionManager.Request,
        requireSuccess: Boolean = false,
    ): CampaignIdsResponse? {
        return try {
            sessions.execute(identity, { it.user }, deliverAlways = true) { requestUser ->
                val jsonBody = buildJsonObject {
                    put("sec", json.encodeToJsonElement(section()))
                    put("device", json.encodeToJsonElement(device()))
                    put("type", json.encodeToJsonElement("screenview"))
                    put("user", json.encodeToJsonElement(requestUser))
                    put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                    put("options", json.encodeToJsonElement(options))
                }

                val data = client.post("$baseUrl$VISIT") {
                    expectSuccess = requireSuccess
                    setBody(jsonBody.toString())
                }.body<String>()

                val json = JSONObject(data).toMap()
                CampaignIdsResponse.fromJson(json)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            Logger.e(TAG, httpFailure(e))
            null
        } catch (e: Exception) {
            // Parser exceptions can contain response bodies and credentials.
            Logger.e(TAG, "visit failed: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun event(
        events: List<TriggerEvent>,
        raisedAt: Date,
        pageContext: PageContext,
        options: Options,
        identity: SessionManager.Request,
        requireSuccess: Boolean = false,
    ): CampaignIdsResponse? {
        return try {
            sessions.execute(identity, { it.user }, deliverAlways = true) { requestUser ->
                val jsonBody = buildJsonObject {
                    put("sec", json.encodeToJsonElement(section()))
                    put("device", json.encodeToJsonElement(device()))
                    put("data", encodeEvents(json, events, raisedAt))
                    put("user", json.encodeToJsonElement(requestUser))
                    put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                    put("options", json.encodeToJsonElement(options))
                }

                val data = client.post("$baseUrl$EVENT") {
                    expectSuccess = requireSuccess
                    setBody(jsonBody)
                }.body<String>()

                val json = JSONObject(data).toMap()
                CampaignIdsResponse.fromJson(json)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            Logger.e(TAG, httpFailure(e))
            null
        } catch (e: Exception) {
            // Parser exceptions can contain response bodies and credentials.
            Logger.e(TAG, "event failed: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun chooseByCampaignIdWithDetails(
        campaignId: String,
        options: Options,
        contentSettings: ContentSettings,
        identity: SessionManager.Request = sessions.snapshot(),
        pageContext: PageContext,
        requireSuccess: Boolean = false,
    ): GravityDataResponse<ContentResponse>? {
        return try {
            sessions.execute(identity, { it.data.user }) { requestUser ->
                val data = buildJsonObject {
                    put("campaignId", Json.parseToJsonElement(campaignId))
                    put("option", json.encodeToJsonElement(contentSettings))
                }
                val jsonBody = buildJsonObject {
                    put("sec", json.encodeToJsonElement(section()))
                    put("data", JsonArray(listOf(data)))
                    put("device", json.encodeToJsonElement(device()))
                    put("user", json.encodeToJsonElement(requestUser))
                    put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                    put("options", json.encodeToJsonElement(options))
                }

                val stringData = client.post("$baseUrl$CHOOSE") {
                    expectSuccess = requireSuccess
                    setBody(jsonBody)
                }.body<String>()

                parseContentResponse(stringData)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            Logger.e(TAG, httpFailure(e))
            null
        } catch (e: Exception) {
            // Parser exceptions can contain response bodies and credentials.
            Logger.e(TAG, "chooseByCampaignIdWithDetails failed: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun chooseBySelectorWithDetails(
        selector: String,
        options: Options,
        contentSettings: ContentSettings,
        identity: SessionManager.Request = sessions.snapshot(),
        pageContext: PageContext,
    ): GravityDataResponse<ContentResponse>? {
        return try {
            sessions.execute(identity, { it.data.user }) { requestUser ->
                val data = buildJsonObject {
                    put("selector", Json.parseToJsonElement(selector))
                    put("option", json.encodeToJsonElement(contentSettings))
                }
                val jsonBody = buildJsonObject {
                    put("sec", json.encodeToJsonElement(section()))
                    put("data", JsonArray(listOf(data)))
                    put("device", json.encodeToJsonElement(device()))
                    put("user", json.encodeToJsonElement(requestUser))
                    put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                    put("options", json.encodeToJsonElement(options))
                }

                val stringData = client.post("$baseUrl$CHOOSE") {
                    setBody(jsonBody)
                }.body<String>()

                parseContentResponse(stringData)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            Logger.e(TAG, httpFailure(e))
            null
        } catch (e: Exception) {
            // Parser exceptions can contain response bodies and credentials.
            Logger.e(TAG, "chooseBySelectorWithDetails failed: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun chooseByGroup(
        group: String,
        options: Options,
        contentSettings: ContentSettings,
        identity: SessionManager.Request = sessions.snapshot(),
        pageContext: PageContext,
    ): ContentResponse? {
        return try {
            sessions.execute(identity, { it.data.user }) { requestUser ->
                val data = buildJsonObject {
                    put("group", Json.parseToJsonElement(group))
                    put("option", json.encodeToJsonElement(contentSettings))
                }
                val jsonBody = buildJsonObject {
                    put("sec", json.encodeToJsonElement(section()))
                    put("data", JsonArray(listOf(data)))
                    put("device", json.encodeToJsonElement(device()))
                    put("user", json.encodeToJsonElement(requestUser))
                    put("ctx", json.encodeToJsonElement(mixPageContextAttributes(pageContext)))
                    put("options", json.encodeToJsonElement(options))
                }

                val stringData = client.post("$baseUrl$CHOOSE") {
                    setBody(jsonBody)
                }.body<String>()

                parseContentResponse(stringData)
            }?.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            Logger.e(TAG, httpFailure(e))
            null
        } catch (e: Exception) {
            // Parser exceptions can contain response bodies and credentials.
            Logger.e(TAG, "chooseByGroup failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun httpFailure(error: ResponseException): String {
        val request = error.response.call.request
        val path = when {
            request.url.encodedPath.endsWith(CHOOSE) -> CHOOSE
            request.url.encodedPath.endsWith(VISIT) -> VISIT
            request.url.encodedPath.endsWith(EVENT) -> EVENT
            else -> "/request"
        }
        return "${request.method.value} failed: HTTP ${error.response.status.value} $path"
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
        var permit: ErrorReportLimiter.Permit? = null
        var successfully = false
        try {
            currentCoroutineContext().ensureActive()
            val admitted = errorReportLimiter.begin(message, stacktrace) ?: return
            permit = admitted
            val current = sessions.reportingUser()
            val logMessage = LogMessage(
                message = admitted.message,
                level = LogMessageLevel.ERROR,
                sec = section(),
                uid = current.uid?.takeIf { it.isNotEmpty() } ?: device().id.orEmpty(),
                ses = current.ses ?: "no-session",
                sdkVersion = DeviceUtils.getSDKVersion(),
                platform = DeviceUtils.getPlatformVersion() ?: "",
                stacktrace = admitted.stacktrace,
            )
            errorReportingClient.post("https://sdk-sentry.gravityfield.ai/error") {
                expectSuccess = true
                setBody(json.encodeToJsonElement(logMessage))
            }.body<String>()
            successfully = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "sendErrorMessage failed: ${e.javaClass.simpleName}", sendToBack = false)
        } finally {
            permit?.let { errorReportLimiter.complete(it, successfully) }
        }
    }

    internal fun userForRequest(customerUser: User?): User = sessions.userForRequest(customerUser)

    private fun mixPageContextAttributes(pageContext: PageContext): PageContext {
        val attributes = pageContext.attributes.toMutableMap()
        attributes.putAll(DeviceUtils.contextAttributes)
        return pageContext.copy(attributes = attributes)
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
