package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.utils.VisibleImpressionDelivery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EngagementTransportTest {
    @Test fun httpTransportRetries500And429AndAccepts204() = runBlocking {
        val count = AtomicInteger()
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("Bearer test-key", request.headers["Authorization"])
            val status = listOf(500, 429, 204)[count.getAndIncrement()]
            respond("", HttpStatusCode.fromValue(status))
        }) { defaultRequest { header("Authorization", "Bearer test-key") } }
        try {
            val transport = EngagementTransport(client)
            val delivery = VisibleImpressionDelivery(send = { transport.send(it) }, retryable = EngagementTransport::isRetryable, pause = {})
            val url = "https://tracking.test/engagement?event=WRIMP"
            assertTrue(delivery.deliver(listOf(url)))
            assertTrue(delivery.deliver(listOf(url)))
            assertEquals(3, count.get())
        } finally { client.close() }
    }

    @Test fun http400CannotAcknowledgeOrRetry() = runBlocking {
        val count = AtomicInteger()
        val client = HttpClient(MockEngine {
            count.incrementAndGet()
            respond("", HttpStatusCode.BadRequest)
        })
        try {
            val transport = EngagementTransport(client)
            val delivery = VisibleImpressionDelivery(send = { transport.send(it) }, retryable = EngagementTransport::isRetryable, pause = { fail("400 must not retry") })
            assertFalse(delivery.deliver(listOf("https://tracking.test/engagement")))
            assertEquals(1, count.get())
        } finally { client.close() }
    }

    @Test fun malformedUrlCannotAcknowledge() = runBlocking {
        val client = HttpClient(MockEngine { fail("Invalid URL must not reach transport"); respond("") })
        try {
            val transport = EngagementTransport(client)
            val delivery = VisibleImpressionDelivery(send = { transport.send(it) }, retryable = EngagementTransport::isRetryable, pause = { fail("Invalid URL must not retry") })
            assertFalse(delivery.deliver(listOf("relative-path")))
        } finally { client.close() }
    }
}
