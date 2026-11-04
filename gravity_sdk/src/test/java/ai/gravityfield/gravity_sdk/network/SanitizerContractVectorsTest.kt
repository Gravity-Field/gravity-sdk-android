package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.internal.LogMessage
import ai.gravityfield.gravity_sdk.models.internal.LogMessageLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SanitizerContractVectorsTest(private val id: String, private val input: String, private val expected: String) {
    @Test fun retainsEndpointsAndRemovesSecrets() {
        val actual = ErrorReportSanitizer.sanitizeUrls(input)
        assertFalse(id, actual.contains("SECRET"))
        if (id == "V37") assertFalse(id, actual.contains("ABC123"))
        if (id in listOf("V2", "V3", "V4", "V11")) {
            assertFalse(id, actual.substringAfter("//").contains("SE"))
            assertFalse(id, actual.contains("CRET"))
        }
        if (id == "V18") { assertFalse(actual.contains("123")); assertFalse(actual.contains("456")) }
        if (id == "V35") assertFalse(id, actual.contains("123"))
        if (id == "V12") { assertFalse(actual.contains("pa")); assertFalse(actual.contains("ss@")) }
        assertEquals(id, expected, actual)
        val report = ErrorReportSanitizer.sanitize(LogMessage(
            input, LogMessageLevel.ERROR, "section", "uid", "session", "1", "Android",
            stacktrace = input, tags = mapOf("url" to input), extra = mapOf("url" to input)))
        assertEquals(id, expected, report.message)
        assertEquals(id, expected, report.stacktrace)
        assertEquals(id, expected, report.tags.getValue("url"))
        assertEquals(id, expected, report.extra.getValue("url"))
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun vectors() = listOf(
            arrayOf("V1", "https://public.test/,https://user:SECRET@private.test/path?token=x", "https://public.test/,https://private.test/path"),
            arrayOf("V2", "https://user:SE/CRET@host/p", "https://[redacted]"),
            arrayOf("V3", "https://user:SE?CRET@host/p", "https://[redacted]"),
            arrayOf("V4", "https://user:SE#CRET@host/p", "https://[redacted]"),
            arrayOf("V5", "wss://user:SECRET@socket.test/x", "wss://socket.test/x"),
            arrayOf("V6", "https://proxy.test/redirect/ftp://user:SECRET@private.test/x", "https://proxy.test/redirect/ftp://private.test/x"),
            arrayOf("V7", "//user:SECRET@host/p", "//host/p"),
            arrayOf("V8", "POST https://api.test/v1/choose?email=a@b.com failed for user@example.com", "POST https://api.test/v1/choose failed for user@example.com"),
            arrayOf("V9", "https://host.test/users/@alice/profile", "https://host.test/users/@alice/profile"),
            arrayOf("V10", "#0 main (file:///Users/me/app/lib/main.dart:10:5)", "#0 main (file:///Users/me/app/lib/main.dart:10:5)"),
            arrayOf("V11", "Illegal character in authority at index 8: https://user:SE CRET@host/p", "Illegal character in authority at index 8: https://[redacted]host/p"),
            arrayOf("V12", "https://u:pa ss@h", "https://[redacted]h"),
            arrayOf("V13", "https:\\/\\/user:SECRET@host\\/p", "https://host/p"),
            arrayOf("V14", "https%3A%2F%2Fuser%3ASECRET%40host", "https://host"),
            arrayOf("V15", "HTTPS://User:SECRET@Host.test/P?q=1#f", "HTTPS://Host.test/P"),
            arrayOf("V16", "https://host:8443/users/@alice/profile", "https://[redacted]"),
            arrayOf("V17", "https://public.test:443/p?email=user@X", "https://[redacted]"),
            arrayOf("V18", "https://user:123/456@host/p", "https://[redacted]"),
            arrayOf("V19", "https://[::1]:8443/users/@alice/profile?x=1", "https://[::1]:8443/users/@alice/profile"),
            arrayOf("V20", "https://host:8443/plain/path?token=x", "https://host:8443/plain/path"),
            arrayOf("V21", "content:https://user:SECRET@host/p", "content:https://host/p"),
            arrayOf("V22", "redis://user:SECRET@host:6379/0", "redis://host:6379/0"),
            arrayOf("V23", "POST https://api.test:8443 failed for user@example.com", "POST https://api.test:8443 failed for user@example.com"),
            arrayOf("V24", "connect https://api.test:8443: timeout; admin@corp.test notified", "connect https://api.test:8443: timeout; admin@corp.test notified"),
            arrayOf("V25", "url:https://user:SECRET@host.test/p?token=x", "url:https://host.test/p"),
            arrayOf("V26", "REQUEST:https://user:SECRET@host.test/p", "REQUEST:https://host.test/p"),
            arrayOf("V27", "failed:reason:https://user:SECRET@host.test/p tail", "failed:reason:https://host.test/p tail"),
            arrayOf("V28", "jdbc:postgresql://user:SECRET@host.test/db", "jdbc:postgresql://host.test/db"),
            arrayOf("V29", "blob:https://user:SECRET@host.test/x", "blob:https://host.test/x"),
            arrayOf("V30", "https://user:SECRET@host.test/p", "https://host.test/p"),
            arrayOf("V31", "https:\\\\/\\\\/user:SECRET@host\\\\/p", "https://host/p"),
            arrayOf("V32", "https:%2F%2Fuser:SECRET@host.test/p", "https://host.test/p"),
            arrayOf("V33", "https://user:SECRET/path", "https://[redacted]"),
            arrayOf("V34", "https://api.test:", "https://api.test:"),
            arrayOf("V35", "https://[user:123]/SECRET@host/p", "https://[redacted]"),
            arrayOf("V36", "retry-2.https://user:SECRET@host/p", "retry-2.https://host/p"),
            arrayOf("V37", "Authorization: Bearer ABC123", "Authorization: [redacted]"),
            arrayOf("V38-brackets", "authorization: [Bearer SECRET]", "authorization: [redacted]"),
            arrayOf("V38-token", "Authorization: Token SECRET", "Authorization: [redacted]"),
            arrayOf("V38-proxy", "Proxy-Authorization: Bearer SECRET", "Proxy-Authorization: [redacted]"),
            arrayOf("V38-x", "x-authorization: SECRET", "x-authorization: [redacted]"),
            arrayOf("V38-newline", "Authorization: Bearer SECRET\nnext line", "Authorization: [redacted]\nnext line"),
            arrayOf("V38-equals", "x-AUTHORIZATION=Token SECRET\r\nnext line", "x-AUTHORIZATION: [redacted]\r\nnext line")
        )
    }
}
