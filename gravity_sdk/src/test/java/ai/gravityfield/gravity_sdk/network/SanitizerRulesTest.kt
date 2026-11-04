package ai.gravityfield.gravity_sdk.network

import ai.gravityfield.gravity_sdk.models.internal.*
import org.junit.Assert.*
import org.junit.Test

class SanitizerRulesTest {
    @Test fun freeTextAuthorizationRedactsTheWholeLineCaseInsensitively() {
        for ((input, expected) in listOf(
            "Authorization: Bearer ABC123, Token SECRET; more value" to "Authorization: [redacted]",
            "Proxy-AUTHORIZATION = [Digest SECRET]\nGET https://host/p?q=SECRET" to
                "Proxy-AUTHORIZATION: [redacted]\nGET https://host/p",
            "authorization=bearer ABC123" to "authorization: [redacted]",
            "aUtHoRiZaTiOn: bEaReR ABC123" to "aUtHoRiZaTiOn: [redacted]",
            "failed (Authorization: Bearer ABC123), POST https://host/p?q=SECRET" to
                "failed (Authorization: [redacted]",
            "Authorization: Bearer ABC123\nauthorization=bearer XYZ789\nrequest failed" to
                "Authorization: [redacted]\nauthorization: [redacted]\nrequest failed",
        )) {
            assertEquals(expected, ErrorReportSanitizer.sanitizeUrls(input))
            assertEquals(expected, ErrorReportSanitizer.sanitizeText(input, 1000))
        }
        assertEquals("Authorization: [redacted]\ntail", ErrorReportSanitizer.sanitizeText(
            "Authorization: Bearer " + "A".repeat(20_000) + "\ntail", 1000))
        assertEquals("Bearer ABC123 is not a header", ErrorReportSanitizer.sanitizeUrls("Bearer ABC123 is not a header"))
    }

    @Test fun nonNetworkPrefixesCannotHideNestedCredentials() {
        for (prefix in listOf("content:", "data:", "file:", "package:", "metadata:", "profile:", "url=file:", "jar:file:")) {
            val actual = ErrorReportSanitizer.sanitizeUrls("${prefix}https://user:SECRET@host/p?q=SECRET")
            assertFalse(actual, actual.contains("SECRET"))
            assertEquals("${prefix}https://host/p", actual)
        }
    }
    @Test fun arbitrarySchemesScrubCredentialsAndQueries() {
        for (scheme in listOf("redis", "mongodb", "amqp", "mysql", "smtp", "git+ssh", "file")) {
            assertEquals("$scheme://host/p", ErrorReportSanitizer.sanitizeUrls("$scheme://user:SECRET@host/p?q=SECRET#SECRET"))
        }
    }
    @Test fun genericAndRepeatedlyEncodedNetworkUrlsCannotLeakCredentials() {
        assertEquals("redis://host/p", ErrorReportSanitizer.sanitizeUrls("redis:\\/\\/user:SECRET@host\\/p?q=SECRET"))
        assertEquals("https://host/p", ErrorReportSanitizer.sanitizeUrls("https%253A%252F%252Fuser%253ASECRET%2540host%252Fp%253Fq%253DSECRET"))
    }
    @Test fun retainsAtSignsInLegitimatePathsAndDropsQueriesWithEmails() {
        for (host in listOf("host.test", "[::1]:8443")) {
            assertEquals("https://$host/users/@alice/profile", ErrorReportSanitizer.sanitizeUrls("https://$host/users/@alice/profile?email=a@b.com#private"))
        }
    }
    @Test fun adjacentUrlCredentialsCannotRedactAnEarlierUnambiguousPort() {
        assertEquals("https://host:8443/plain/path,https://private.test/p", ErrorReportSanitizer.sanitizeUrls("https://host:8443/plain/path,https://u:SECRET@private.test/p?q=x"))
    }
    @Test fun malformedIpv6AuthorityCannotBypassAmbiguousUserinfoRedaction() {
        assertEquals("https://[redacted]", ErrorReportSanitizer.sanitizeUrls("https://[::::]:123/SE/CRET@host/p"))
    }
    @Test fun quoteDelimitedUserinfoIsRedactedWithoutLosingEndpointOrProse() {
        for (separator in listOf("\"", "'", " ", "  ")) {
            assertEquals("before https://[redacted]host/p after", ErrorReportSanitizer.sanitizeUrls("before https://u:pa${separator}ss@host/p?token=SECRET after"))
        }
    }
    @Test fun nonNetworkSchemesKeepTheirPathsAndOnlyLoseQueryOrFragment() {
        for (input in listOf("file:///a/b.dart:1:2", "package:app/a.dart:1", "dart:core", "content://app/path", "data:text/plain,hello")) {
            assertEquals(input, ErrorReportSanitizer.sanitizeUrls(input))
            assertEquals(input, ErrorReportSanitizer.sanitizeUrls("$input?token=SECRET#fragment"))
        }
    }
    @Test fun handlesWsAndRepeatedEscapingWithoutLosingEndpoint() {
        assertEquals("ws://host/p", ErrorReportSanitizer.sanitizeUrls("ws://u:SECRET@host/p?q=1"))
        assertEquals("https://host/p", ErrorReportSanitizer.sanitizeUrls("https:\\\\/\\\\/u:SECRET@host\\\\/p"))
    }
    @Test fun inputBoundDropsOnlyTheCutUrlAndKeepsSurroundingText() {
        val message = LogMessage("before https://u:SECRET@host/" + "x".repeat(20000) + " after", LogMessageLevel.ERROR,
            "sec", "uid", "ses", "1", "Android")
        assertEquals("before  after", ErrorReportSanitizer.sanitize(message).message)
    }
    @Test fun repeatedPrefixesAndBackslashRunsDoNotCauseBacktracking() {
        val paths = "data:plain ".repeat(25000)
        assertEquals(paths, ErrorReportSanitizer.sanitizeUrls(paths))
        val word = "a".repeat(1_000_000)
        assertEquals(word, ErrorReportSanitizer.sanitizeUrls(word))
        val urls = "https://host/p,".repeat(25000)
        assertEquals(urls, ErrorReportSanitizer.sanitizeUrls(urls))
        val slashes = "\\".repeat(1_000_000)
        assertEquals(slashes, ErrorReportSanitizer.sanitizeUrls(slashes))
    }
    @Test fun millionCharacterInputCompletesWithoutRecursiveMatching() {
        val input = "https://u:SECRET@host/" + "x".repeat(1_000_000) + "?SECRET"
        val output = ErrorReportSanitizer.sanitizeUrls(input)
        assertTrue(output.startsWith("https://host/"))
        assertFalse(output.contains("SECRET"))
    }
}
