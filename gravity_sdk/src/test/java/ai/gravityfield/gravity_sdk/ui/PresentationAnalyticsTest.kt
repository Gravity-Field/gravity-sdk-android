package ai.gravityfield.gravity_sdk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationAnalyticsTest {
    @Test fun impressionIsClaimedOncePerPresentation() {
        val analytics = PresentationAnalytics()
        assertTrue(analytics.claimImpression())
        analytics.remounted()
        assertFalse(analytics.claimImpression())
    }

    @Test fun gridProductsSurviveRemount() {
        val analytics = PresentationAnalytics()
        val first = analytics.gridProducts("content").apply { add("slot-1") }
        analytics.remounted()
        assertSame(first, analytics.gridProducts("content"))
        assertEquals(setOf("slot-1"), analytics.gridProducts("content"))
        assertTrue(analytics.gridProducts("other").isEmpty())
    }

    @Test fun remountedRowSkipsOnlyTheRepeatOfAlreadyReportedProducts() {
        val analytics = PresentationAnalytics()
        val reported = Any()
        val unseen = Any()
        assertTrue(analytics.shouldReportRowProduct(reported))
        assertTrue("Scrolling back in the same mount reports again", analytics.shouldReportRowProduct(reported))
        analytics.remounted()
        assertFalse("A rotation is not a new view of the product", analytics.shouldReportRowProduct(reported))
        assertTrue(analytics.shouldReportRowProduct(reported))
        assertTrue(analytics.shouldReportRowProduct(unseen))
    }
}
