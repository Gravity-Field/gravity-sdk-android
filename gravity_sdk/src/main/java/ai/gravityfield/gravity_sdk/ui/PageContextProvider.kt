package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.models.PageContext

internal class PageContextProvider {
    private var pageContext: PageContext? = null
    private var _listener: ((PageContext) -> Unit)? = null

    fun setListener(listener: ((PageContext) -> Unit)) {
        _listener = listener
        pageContext?.let {
            listener(it)
        }
    }

    fun removeListener() {
        _listener = null
    }

    fun provide(pageContext: PageContext) {
        if (this.pageContext == pageContext) return
        this.pageContext = pageContext
        _listener?.invoke(pageContext)
    }

    fun dispose() {
        _listener = null
    }
}
