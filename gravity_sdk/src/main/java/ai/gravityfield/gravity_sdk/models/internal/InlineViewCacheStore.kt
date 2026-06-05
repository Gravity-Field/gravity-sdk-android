package ai.gravityfield.gravity_sdk.models.internal

import ai.gravityfield.gravity_sdk.models.PageContext
import java.util.concurrent.ConcurrentHashMap

internal class InlineViewCacheStore {

    private val cache = ConcurrentHashMap<InlineViewCacheKey, InlineViewCache>()

    fun get(selector: String, pageContext: PageContext): InlineViewCache? {
        return cache[key(selector, pageContext)]
    }

    fun put(selector: String, pageContext: PageContext, value: InlineViewCache) {
        cache[key(selector, pageContext)] = value
    }

    fun remove(selector: String, pageContext: PageContext) {
        cache.remove(key(selector, pageContext))
    }

    fun updateScrollPosition(
        selector: String,
        pageContext: PageContext,
        scrollPosition: ScrollPosition,
    ) {
        cache.computeIfPresent(key(selector, pageContext)) { _, current ->
            current.copy(scrollPosition = scrollPosition)
        }
    }

    private fun key(selector: String, pageContext: PageContext): InlineViewCacheKey {
        return InlineViewCacheKey(selector, pageContext)
    }
}
