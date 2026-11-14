package ai.gravityfield.gravity_sdk.ui

import androidx.compose.runtime.compositionLocalOf
import java.util.Collections
import java.util.IdentityHashMap

/**
 * UI-thread analytics memory of one SDK in-app presentation. It outlives the composition, so a
 * configuration recreation that remounts the same showing does not report it a second time.
 */
internal class PresentationAnalytics {
    private var impressionClaimed = false
    private val gridProducts = mutableMapOf<String, MutableSet<String>>()
    private val reportedRowProducts: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    private val restoredRowProducts: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    /** True only for the first content impression of this presentation. */
    fun claimImpression(): Boolean {
        if (impressionClaimed) return false
        impressionClaimed = true
        return true
    }

    /** Grid cells report once per presentation, across remounts. */
    fun gridProducts(contentId: String): MutableSet<String> = gridProducts.getOrPut(contentId) { mutableSetOf() }

    /** A remounted row repeats no report for a product it already reported; later re-entries still report. */
    fun remounted() {
        restoredRowProducts.clear()
        restoredRowProducts.addAll(reportedRowProducts)
    }

    fun shouldReportRowProduct(slot: Any): Boolean {
        reportedRowProducts.add(slot)
        return !restoredRowProducts.remove(slot)
    }
}

internal val LocalPresentationAnalytics = compositionLocalOf<PresentationAnalytics?> { null }
