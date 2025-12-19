package ai.gravityfield.gravity_sdk.models.internal

internal class ScrollProvider(
    scrollPosition: ScrollPosition?,
    private val onScrollChanged: (scrollPosition: ScrollPosition) -> Unit,
) {
    var scrollPosition: ScrollPosition? = null
        private set

    init {
        this.scrollPosition = scrollPosition
    }

    fun changeScroll(position: Int, offset: Int) {
        scrollPosition = ScrollPosition(position, offset)
        onScrollChanged(scrollPosition!!)
    }
}
