package ai.gravityfield.gravity_sdk.network

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Admits diagnostics before identity/payload work; failures consume budget but may retry. */
internal class ErrorReportLimiter(
    limit: Int = 10,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sanitize: (String, Int) -> String = ErrorReportSanitizer::sanitizeText,
) {
    internal class Permit internal constructor(
        internal val fingerprint: String,
        val message: String,
        val stacktrace: String,
    )

    private val limit = limit.coerceAtLeast(0)
    private val windowMillis = 60_000L
    private val attempts = mutableListOf<Long>()
    private val delivered = mutableMapOf<String, Long>()
    private val inFlight = mutableMapOf<String, Permit>()

    @Synchronized fun begin(message: String, stacktrace: String): Permit? {
        val time = now()
        attempts.removeAll { time - it >= windowMillis }
        delivered.entries.removeAll { time - it.value >= windowMillis }
        // Bound hung requests as well, and reject spent budgets before sanitizing or hashing.
        if (attempts.size >= limit || inFlight.size >= limit) return null
        val cleanMessage = sanitize(message, 1000)
        val cleanStacktrace = sanitize(stacktrace, 5000)
        val messageBytes = cleanMessage.toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256")
        hash.update(ByteBuffer.allocate(8).putLong(messageBytes.size.toLong()).array())
        hash.update(messageBytes)
        hash.update(cleanStacktrace.toByteArray(Charsets.UTF_8))
        val fingerprint = hash.digest().joinToString("") { "%02x".format(it) }
        if (fingerprint in delivered || fingerprint in inFlight) return null
        val permit = Permit(fingerprint, cleanMessage, cleanStacktrace)
        attempts += time
        inFlight[fingerprint] = permit
        return permit
    }

    @Synchronized fun complete(permit: Permit, successfully: Boolean) {
        // A stale/double completion must not release a newer retry of the same report.
        if (inFlight[permit.fingerprint] !== permit) return
        inFlight.remove(permit.fingerprint)
        if (successfully) delivered[permit.fingerprint] = now()
    }
}
