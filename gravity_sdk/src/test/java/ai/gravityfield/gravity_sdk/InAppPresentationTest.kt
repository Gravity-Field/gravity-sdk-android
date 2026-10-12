package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.utils.InAppPresentation
import ai.gravityfield.gravity_sdk.utils.InAppPresentationRegistry
import org.junit.Assert.*
import org.junit.Test

class InAppPresentationTest {
    @Test fun completionFollowsPhysicalCloseAndTracking() {
        val events = mutableListOf<String>()
        var finish: ((Boolean) -> Unit)? = null
        val presentation = InAppPresentation({ events += "animate"; finish = it }, { events += "close" })
        presentation.close { assertTrue(it); events += "navigate" }
        assertEquals(listOf("animate"), events)
        finish!!(true)
        assertEquals(listOf("animate", "close", "navigate"), events)
    }

    @Test fun duplicateRequestAndDuplicateCompletionDoNotCloseOrNavigateTwice() {
        var finishes = 0
        var navigations = 0
        var finish: ((Boolean) -> Unit)? = null
        val presentation = InAppPresentation({ finish = it }, { finishes++ })
        presentation.close { if (it) navigations++ }
        presentation.close { assertFalse(it) }
        finish!!(true)
        finish!!(true)
        presentation.close { assertFalse(it) }
        assertEquals(1, finishes)
        assertEquals(1, navigations)
    }

    @Test fun onlyRequestedContentIsDismissed() {
        val registry = InAppPresentationRegistry()
        var aClosed = false
        var bClosed = false
        val a = InAppPresentation({ aClosed = true; it(true) }, {})
        val b = InAppPresentation({ bClosed = true; it(true) }, {})
        registry.register(a, "A")
        registry.register(b, "B")
        registry.dismiss("A") { assertTrue(it) }
        assertTrue(aClosed)
        assertFalse(bClosed)
    }

    @Test fun unknownOrInlineContentReturnsFalse() {
        InAppPresentationRegistry().dismiss("inline") { assertFalse(it) }
    }

    @Test fun oldPresentationCannotRemoveNewerWithSameId() {
        val registry = InAppPresentationRegistry()
        val old = InAppPresentation({ it(true) }, {})
        val new = InAppPresentation({ it(true) }, {})
        registry.register(old, "same")
        registry.register(new, "same")
        registry.remove(old, "same")
        registry.dismiss("same") { assertTrue(it) }
    }

    @Test fun disappearedUiReportsFalseAndCleansRegistry() {
        val registry = InAppPresentationRegistry()
        lateinit var presentation: InAppPresentation
        presentation = InAppPresentation({ it(false) }, { registry.remove(presentation, "removed") })
        registry.register(presentation, "removed")
        registry.dismiss("removed") { assertFalse(it) }
        registry.dismiss("removed") { assertFalse(it) }
    }
}
