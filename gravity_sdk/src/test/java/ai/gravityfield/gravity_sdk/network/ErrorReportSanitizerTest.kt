package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.internal.LogMessage
import ai.gravityfield.gravity_sdk.models.internal.LogMessageLevel
import ai.gravityfield.gravity_sdk.network.ErrorReportSanitizer.sanitizeUrls
import org.junit.Assert.*
import org.junit.Test

class ErrorReportSanitizerTest {
    @Test fun removesCredentialsFromEveryEmbeddedAuthority() {
        assertEquals("https://public.test/,https://private.test/path",
            sanitizeUrls("https://public.test/,https://user:SECRET@private.test/path?token=x"))
        assertEquals("https://public.test/", sanitizeUrls("https://public.test/?token=secret,https://user:SECRET@private.test/path"))
        assertEquals("https://public.test,https://private.test/path",
            sanitizeUrls("https://public.test,https://user:SECRET@private.test/path?token=x"))
        assertEquals("https://host/path", sanitizeUrls("https://host/path?redirect=https://u:SECRET@private.test/"))
    }

    @Test fun rawSpacesInUserinfoCannotLeakPasswordFragments() {
        for (scheme in listOf("https://", "wss://", "ftp://", "//")) {
            for (password in listOf("pa ss", "pa  ss")) {
                val cleaned = sanitizeUrls("before ${scheme}u:$password@h/path?token=SECRET after")
                assertEquals("before ${scheme}[redacted]h/path after", cleaned)
                assertFalse(cleaned, cleaned.contains("u:"))
                assertFalse(cleaned, cleaned.contains(password))
                assertFalse(cleaned, cleaned.contains("SECRET"))
            }
        }
    }

    @Test fun dropsMalformedUserinfoContainingAnUnencodedSlash() = assertNoUserinfo("https://user:SE/CRET@host/p")
    @Test fun dropsMalformedUserinfoContainingAnUnencodedQuery() = assertNoUserinfo("https://user:SE?CRET@host/p")
    @Test fun dropsMalformedUserinfoContainingAnUnencodedFragment() = assertNoUserinfo("https://user:SE#CRET@host/p")
    @Test fun stripsWebsocketUserinfoAndQuery() = assertEquals("wss://host/p", sanitizeUrls("wss://user:SECRET@host/p?token=SECRET"))
    @Test fun stripsFtpUserinfoAndQuery() = assertEquals("ftp://host/p", sanitizeUrls("ftp://user:SECRET@host/p?token=SECRET"))
    @Test fun stripsSchemeRelativeUserinfoAndQuery() = assertEquals("//host/p", sanitizeUrls("//user:SECRET@host/p?token=SECRET"))
    @Test fun malformedCredentialsInNestedAndAdjacentUrlsCannotSurvive() {
        for (prefix in listOf("https://public.test/,", "https://public.test/path/")) {
            for (scheme in listOf("https://", "wss://", "ftp://", "//")) {
                for (secret in listOf("SE/CRET", "SE//CRET", "SE?CRET", "SE#CRET")) assertNoUserinfo(prefix + scheme + "user:" + secret + "@host/p?token=SECRET")
            }
        }
    }
    private fun assertNoUserinfo(input: String) {
        val result = sanitizeUrls(input)
        assertFalse(result, result.contains("SE"))
        assertFalse(result, result.contains("CRET"))
        assertFalse(result, result.contains("user:"))
    }

    @Test fun stripsQueriesAfterParentheses() = assertSafe("https://host/p(1)?uid=SECRET", "https://host/p(1)")
    @Test fun stripsQueriesAfterCommas() = assertSafe("https://host/a,b?uid=SECRET", "https://host/a,b")
    @Test fun stripsQueriesAfterBraces() = assertSafe("https://host/{id}?token=SECRET", "https://host/{id}")
    @Test fun stripsQueriesAfterUnmatchedBrackets() = assertSafe("https://host/a[b?uid=SECRET", "https://host/a[b")
    @Test fun stripsMalformedIpv6Credentials() = assertSafe("https://u:p@[::1/p?uid=1", "https://[::1/p")
    @Test fun stripsThroughLastAtSign() = assertSafe("https://user:p@ss@host.com/p", "https://host.com/p")
    @Test fun keepsClosingPunctuationWithoutQuery() {
        assertEquals("See (https://host/p).", sanitizeUrls("See (https://u:p@host/p)."))
    }
    @Test fun handlesMillionCharacterUrlWithoutRecursion() {
        val result = sanitizeUrls("https://host/" + "a".repeat(1_000_000) + "?uid=SECRET")
        assertFalse(result.contains("SECRET"))
        assertFalse(result.contains("uid="))
    }
    @Test fun boundsReportTextBeforeAndAfterCleaning() {
        val report = ErrorReportSanitizer.sanitize(LogMessage(
            message = "https://host/" + "a".repeat(1_000_000) + "?uid=SECRET",
            stacktrace = "b".repeat(60_000), level = LogMessageLevel.ERROR,
            sec = "section", uid = "device", ses = "session", sdkVersion = "1", platform = "Android",
        ))
        assertEquals("", report.message)
        assertEquals(5000, report.stacktrace.length)
        assertTrue(report.stacktrace.endsWith("..."))
        assertFalse(report.toString().contains("SECRET"))
    }
    @Test fun dropsMessageUrlCutInsideCredentials() = assertDroppedTruncatedUrl(1000, "https://user:SEC")
    @Test fun dropsStacktraceUrlCutInsideCredentials() = assertDroppedTruncatedUrl(5000, "https://user:SEC")
    @Test fun dropsMessageUrlCutInsideQuery() = assertDroppedTruncatedUrl(1000, "https://user:SECRETPASS@host/p?uid=SEC")
    @Test fun dropsStacktraceUrlCutInsideQuery() = assertDroppedTruncatedUrl(5000, "https://user:SECRETPASS@host/p?uid=SEC")

    @Test fun preservesCompleteTokensAtThePreliminaryBoundaryAndBoundsPlainText() {
        val url = "https://user:SECRETPASS@host/p?uid=SECRET"
        val padding = "https://padding/?" + "x".repeat(10000 - url.length - 18) + " "
        for (suffix in listOf("", " trailing", "\"trailing", "<trailing")) {
            val report = ErrorReportSanitizer.sanitize(LogMessage(
                message = padding + url + suffix,
                stacktrace = "x".repeat(60000), level = LogMessageLevel.ERROR,
                sec = "section", uid = "device", ses = "session", sdkVersion = "1", platform = "Android",
            ))
            assertEquals("https://padding/ https://host/p", report.message)
            assertEquals(5000, report.stacktrace.length)
        }
        val report = ErrorReportSanitizer.sanitize(LogMessage(
            message = "x".repeat(20000), level = LogMessageLevel.ERROR,
            sec = "section", uid = "device", ses = "session", sdkVersion = "1", platform = "Android",
        ))
        assertEquals(1000, report.message.length)
        assertTrue(report.message.endsWith("..."))
    }

    private fun assertDroppedTruncatedUrl(limit: Int, partialUrl: String) {
        // The padding query disappears during sanitization, exposing the final token
        // to the output limit; plain padding would hide this bug behind truncation.
        val padding = "https://padding/?" + "x".repeat(limit * 10 - partialUrl.length - 18) + " "
        val text = padding + "https://user:SECRETPASS@host/p?uid=SECRET"
        assertEquals(partialUrl, text.take(limit * 10).substringAfterLast(' '))
        val report = ErrorReportSanitizer.sanitize(LogMessage(
            message = if (limit == 1000) text else "",
            stacktrace = if (limit == 5000) text else "", level = LogMessageLevel.ERROR,
            sec = "section", uid = "device", ses = "session", sdkVersion = "1", platform = "Android",
        ))
        val clean = if (limit == 1000) report.message else report.stacktrace
        assertEquals("https://padding/ ", clean)
        assertFalse(clean.contains("SEC"))
        assertFalse(clean.contains("user:"))
    }

    private fun assertSafe(input: String, expected: String) {
        assertEquals(expected, sanitizeUrls(input))
    }

    @Test fun stripsQueryFragmentAndCredentialsKeepingPort() {
        assertEquals("GET https://ev.example.com:8443/v2/engagement failed",
            sanitizeUrls("GET https://user:pass@ev.example.com:8443/v2/engagement?uid=abc&token=t#frag failed"))
        assertEquals("https://ev.example.com/x", sanitizeUrls("https://ev.example.com/x#frag"))
    }

    @Test fun dropsTheClosingBracketWithTheQueryInStacktraces() {
        // A query may hold brackets of its own, so nothing after `?` is trusted to end it.
        assertEquals("#0 fetch (see http://ev.example.com/pixel",
            sanitizeUrls("#0 fetch (see http://ev.example.com/pixel?cuid=secret)"))
    }

    @Test fun keepsBracketedIpv6Host() {
        assertEquals("http://[::1]:8080/pixel", sanitizeUrls("http://[::1]:8080/pixel?uid=ipv6-query-uid"))
    }

    @Test fun dropsTheWholeQueryEvenWhereItHoldsCommasOrBrackets() {
        assertEquals("GET http://[::1]:8080/pixel failed; see https://ev.example.com/p",
            sanitizeUrls("GET http://[::1]:8080/pixel?uid=,comma-secret failed; " +
                "see https://ev.example.com/p?a=(x),y&uid=bracket-secret#f,frag-secret."))
    }

    @Test fun cutsUnparseableUrlsByHand() {
        assertEquals("https://ev.example.com/a%zz b", sanitizeUrls("https://user:pass@ev.example.com/a%zz?uid=1 b"))
        assertEquals("https://my_host.example.com/p", sanitizeUrls("https://user:pass@my_host.example.com/p?uid=1#f"))
    }

    @Test fun leavesTextWithoutUrlsUnchanged() {
        val text = "java.lang.IllegalStateException: uid=abc?x#y\n\tat a.b.C(C.kt:1)"
        assertEquals(text, sanitizeUrls(text))
    }

    @Test fun sanitizesEveryStringOfAReportButLeavesIdentifiers() {
        val report = ErrorReportSanitizer.sanitize(LogMessage(
            message = "GET https://ev.example.com/v2?uid=abc failed",
            level = LogMessageLevel.ERROR, sec = "section", uid = "device-uid", ses = "session",
            sdkVersion = "1.2.0", platform = "Android",
            stacktrace = "at https://ev.example.com/s?token=t",
            tags = mapOf("endpoint" to "https://ev.example.com/x?y=1"),
            extra = mapOf("url" to "http://a.example.com/p?q=1", "note" to "plain text"),
        ))
        assertEquals("GET https://ev.example.com/v2 failed", report.message)
        assertEquals("at https://ev.example.com/s", report.stacktrace)
        assertEquals(mapOf("endpoint" to "https://ev.example.com/x"), report.tags)
        assertEquals(mapOf("url" to "http://a.example.com/p", "note" to "plain text"), report.extra)
        assertEquals("device-uid", report.uid)
        assertEquals("session", report.ses)
    }
}
