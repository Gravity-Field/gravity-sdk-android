package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.assistant.AssistantErrorCode
import ai.gravityfield.gravity_sdk.models.assistant.ShoppingGenerateResponse
import ai.gravityfield.gravity_sdk.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger

private const val ASSISTANT_BASE_URL = "https://shopping-assistant-api.gravityfield.ai"
private const val GENERATE = "/shopping/generate"

@Serializable
internal data class ShoppingMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ShoppingRuntimeContext(
    @SerialName("tenant_id") val tenantId: String,
    /** Canonical Gravity uid; also used as the request resourceId. */
    val uid: String,
    val ctx: PageContext? = null,
)

@Serializable
internal data class ShoppingGenerateRequest(
    val threadId: String,
    val resourceId: String,
    val messages: List<ShoppingMessage>,
    val runtimeContext: ShoppingRuntimeContext,
    val trafficType: String? = null,
)

/** Result of an assistant request. The failure code is used for diagnostics only. */
internal sealed interface ShoppingGenerateOutcome {

    data class Success(val response: ShoppingGenerateResponse) : ShoppingGenerateOutcome

    data class Failure(val code: AssistantErrorCode) : ShoppingGenerateOutcome
}

/** HTTP client for the Shopping Assistant API. Requests are sent without an API key. */
internal class AssistantRepository private constructor() {

    companion object {
        private var _instance = AssistantRepository()

        val instance: AssistantRepository
            get() = _instance

        private const val TAG = "AssistantRepository"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val client = HttpClient(Android) {
        defaultRequest {
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(json = json)
        }

        install(HttpTimeout) {
            // The connect timeout must be set explicitly: otherwise the engine's
            // own 100 s limit cuts the call before the request timeout.
            connectTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 120_000
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

    suspend fun generate(request: ShoppingGenerateRequest): ShoppingGenerateOutcome {
        return try {
            val response = client.post("$ASSISTANT_BASE_URL$GENERATE") {
                setBody(json.encodeToJsonElement(request).toString())
            }

            val status = response.status.value
            if (status !in 200..299) {
                return failure(statusCode(status), "status $status")
            }

            parse(JSONObject(response.body<String>()).toMap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            failure(AssistantErrorCode.Timeout, e.message)
        } catch (e: ConnectTimeoutException) {
            failure(AssistantErrorCode.Timeout, e.message)
        } catch (e: SocketTimeoutException) {
            failure(AssistantErrorCode.Timeout, e.message)
        } catch (e: IOException) {
            failure(AssistantErrorCode.Network, e.message)
        } catch (e: Exception) {
            failure(AssistantErrorCode.InvalidResponse, e.message)
        }
    }

    /** Requires a `response` object; an empty `contents` is a valid response. */
    private fun parse(json: Map<String, Any?>): ShoppingGenerateOutcome {
        if (json["response"] !is Map<*, *>) {
            return failure(AssistantErrorCode.InvalidResponse, "no response object")
        }
        return ShoppingGenerateOutcome.Success(ShoppingGenerateResponse.fromJson(json))
    }

    private fun statusCode(status: Int): AssistantErrorCode = when {
        status == 401 || status == 403 -> AssistantErrorCode.Unauthorized
        status == 429 -> AssistantErrorCode.RateLimited
        status >= 500 -> AssistantErrorCode.Server
        else -> AssistantErrorCode.InvalidResponse
    }

    private fun failure(code: AssistantErrorCode, reason: String?): ShoppingGenerateOutcome {
        Logger.e(TAG, "generate failed with $code: $reason")
        return ShoppingGenerateOutcome.Failure(code)
    }
}
