package ai.gravityfield.gravity_sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import java.net.URI

internal class EngagementTransport(private val client: HttpClient) {
    suspend fun send(url: String, apiKey: String? = null) {
        val uri = URI(url)
        require(uri.scheme?.lowercase() in listOf("https", "http") && uri.host != null) { "Invalid engagement URL" }
        val status = client.get(url) {
            if (apiKey != null) header("Authorization", "Bearer $apiKey")
        }.status.value
        if (status !in 200..299) throw EngagementHttpException(status)
    }

    companion object {
        fun isRetryable(error: Throwable): Boolean = when (error) {
            is EngagementHttpException -> error.status == 408 || error.status == 429 || error.status >= 500
            is IllegalArgumentException, is java.net.URISyntaxException -> false
            else -> true
        }
    }
}
