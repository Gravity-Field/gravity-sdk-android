package ai.gravityfield.sdk

import android.content.Context

/** The client's real, durable source of truth for "has this SKU already been reviewed" —
 *  SharedPreferences-backed so it survives relaunches, unlike [ReviewPromptIntegration]'s own
 *  in-memory submitted-SKU cache (which only guards against a stale same-session result while
 *  this store is the one actually consulted by the demo's [ReviewEligibilityAPI]). */
class ReviewStatusStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("gravity_demo_reviews", Context.MODE_PRIVATE)
    private val key = "reviewed_skus"
    private val lock = Any()

    fun reviewedSkus(): Set<String> = synchronized(lock) {
        prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    }

    fun markReviewed(sku: String) = synchronized(lock) {
        prefs.edit().putStringSet(key, reviewedSkus() + sku).apply()
    }

    /** Demo-only affordance so the real flow can be re-tested without reinstalling the app. */
    fun reset() = synchronized(lock) {
        prefs.edit().remove(key).apply()
    }
}
