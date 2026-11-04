package ai.gravityfield.gravity_sdk.network

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ErrorReportLimiterTest {
    @Test fun authorizationValuesUseTheSameSanitizedSuccessfulFingerprint() {
        val limiter = ErrorReportLimiter(now = { 0L })
        val first = requireNotNull(limiter.begin("Authorization: Bearer ABC123", ""))
        assertEquals("Authorization: [redacted]", first.message)
        limiter.complete(first, successfully = true)
        assertNull(limiter.begin("Authorization: Token XYZ789", ""))
        assertNull(limiter.begin("Authorization: [Bearer ZYX987]", ""))
    }

    private class Clock { var millis = 0L; fun limiter() = ErrorReportLimiter(now = { millis }) }

    @Test fun successfulDuplicateUsesSanitizedMessageAndStack() {
        val l = Clock().limiter()
        val p = requireNotNull(l.begin("GET https://u:FIRST@h/p?q=one", "https://h/s?q=one"))
        assertEquals("GET https://h/p", p.message)
        assertEquals("https://h/s", p.stacktrace)
        l.complete(p, true)
        assertNull(l.begin("GET https://u:SECOND@h/p?q=two", "https://h/s?q=two"))
    }
    @Test fun differentStacksRemainDifferentReports() {
        val l = Clock().limiter()
        l.complete(requireNotNull(l.begin("same", "first")), true)
        assertNotNull(l.begin("same", "second"))
    }
    @Test fun lengthPrefixKeepsNulContainingTuplesDistinct() {
        val l = Clock().limiter()
        l.complete(requireNotNull(l.begin("a\u0000b", "c")), true)
        assertNotNull(l.begin("a", "b\u0000c"))
    }
    @Test fun budgetCheckPrecedesSanitization() {
        var calls = 0
        val l = ErrorReportLimiter(now = { 0L }, sanitize = { text, _ -> calls++; text })
        repeat(10) { l.complete(requireNotNull(l.begin("message-$it", "")), false) }
        assertEquals(20, calls)
        assertNull(l.begin("not formatted", "not formatted"))
        assertEquals(20, calls)
    }
    @Test fun zeroBudgetNeverSanitizes() {
        val l = ErrorReportLimiter(limit = 0, sanitize = { _, _ -> error("No formatting allowed") })
        assertNull(l.begin("ignored", "ignored"))
    }
    @Test fun duplicatesDoNotSpendOtherAdmissionSlots() {
        val l = Clock().limiter()
        l.complete(requireNotNull(l.begin("same", "")), true)
        repeat(100) { assertNull(l.begin("same", "")) }
        repeat(9) { l.complete(requireNotNull(l.begin("distinct-$it", "")), true) }
        assertNull(l.begin("eleventh", ""))
    }
    @Test fun failedSendCanRetryWithinRemainingBudget() {
        val l = Clock().limiter()
        l.complete(requireNotNull(l.begin("same", "")), false)
        assertNotNull(l.begin("same", ""))
    }
    @Test fun failuresConsumeTenAttemptBudgetAndExpireAtExactlySixtySeconds() {
        val clock = Clock(); val l = clock.limiter()
        repeat(10) { l.complete(requireNotNull(l.begin("same", "")), false) }
        clock.millis = 59_999; assertNull(l.begin("same", ""))
        clock.millis = 60_000; assertNotNull(l.begin("same", ""))
    }
    @Test fun staggeredAdmissionsExpireIndividuallyRatherThanResettingWholeWindow() {
        val clock = Clock(); val l = clock.limiter()
        repeat(5) { l.complete(requireNotNull(l.begin("early-$it", "")), false) }
        clock.millis = 30_000
        repeat(5) { l.complete(requireNotNull(l.begin("later-$it", "")), false) }
        clock.millis = 59_999; assertNull(l.begin("blocked", ""))
        clock.millis = 60_000
        repeat(5) { l.complete(requireNotNull(l.begin("new-$it", "")), false) }
        assertNull(l.begin("sixth", ""))
        clock.millis = 89_999; assertNull(l.begin("still blocked", ""))
        clock.millis = 90_000
        repeat(5) { l.complete(requireNotNull(l.begin("next-$it", "")), false) }
        assertNull(l.begin("sixth again", ""))
    }
    @Test fun successfulDuplicateExpiresAtExactlySixtySeconds() {
        val clock = Clock(); val l = clock.limiter()
        l.complete(requireNotNull(l.begin("same", "")), true)
        clock.millis = 59_999; assertNull(l.begin("same", ""))
        clock.millis = 60_000; assertNotNull(l.begin("same", ""))
    }
    @Test fun successfulDuplicateWindowStartsAtCompletionNotAdmission() {
        val clock = Clock(); val l = clock.limiter()
        val p = requireNotNull(l.begin("same", ""))
        clock.millis = 70_000; l.complete(p, true)
        clock.millis = 129_999; assertNull(l.begin("same", ""))
        clock.millis = 130_000; assertNotNull(l.begin("same", ""))
    }
    @Test fun inflightCapSurvivesExpiryOfAttemptBudget() {
        val clock = Clock(); val l = clock.limiter()
        val held = List(10) { requireNotNull(l.begin("held-$it", "")) }
        clock.millis = 60_000
        assertNull(l.begin("new", ""))
        l.complete(held.first(), false)
        assertNotNull(l.begin("new", ""))
    }
    @Test fun staleCompletionCannotReleaseOrMarkNewRetryDelivered() {
        val l = Clock().limiter()
        val old = requireNotNull(l.begin("same", "")); l.complete(old, false)
        val retry = requireNotNull(l.begin("same", "")); l.complete(old, true)
        assertNull(l.begin("same", ""))
        l.complete(retry, false)
        assertNotNull(l.begin("same", ""))
    }
    @Test fun fingerprintUsesExactlyTheBoundedWireText() {
        val l = Clock().limiter()
        val first = requireNotNull(l.begin("m".repeat(2000) + "first", "s".repeat(6000) + "first"))
        assertEquals(1000, first.message.length); assertEquals(5000, first.stacktrace.length)
        l.complete(first, true)
        assertNull(l.begin("m".repeat(2000) + "second", "s".repeat(6000) + "second"))
    }
    @Test fun staggeredSuccessfulDuplicatesExpireIndividually() {
        val clock = Clock(); val l = clock.limiter()
        l.complete(requireNotNull(l.begin("early", "")), true)
        clock.millis = 30_000; l.complete(requireNotNull(l.begin("late", "")), true)
        clock.millis = 60_000
        assertNotNull(l.begin("early", "")); assertNull(l.begin("late", ""))
    }
    @Test fun concurrentDistinctReportsAdmitOnlyTen() = runBlocking {
        val l = ErrorReportLimiter(now = { 0L }); val start = CompletableDeferred<Unit>()
        val jobs = List(100) { n -> async(Dispatchers.Default) { start.await(); l.begin("message-$n", "") } }
        start.complete(Unit)
        assertEquals(10, jobs.awaitAll().count { it != null })
    }
    @Test fun concurrentDuplicatesAdmitOnlyOne() = runBlocking {
        val l = ErrorReportLimiter(now = { 0L }); val start = CompletableDeferred<Unit>()
        val jobs = List(100) { async(Dispatchers.Default) { start.await(); l.begin("same", "same") } }
        start.complete(Unit)
        assertEquals(1, jobs.awaitAll().count { it != null })
    }
}
