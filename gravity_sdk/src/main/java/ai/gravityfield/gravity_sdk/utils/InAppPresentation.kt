package ai.gravityfield.gravity_sdk.utils

import java.lang.ref.WeakReference

/** UI-thread state for one SDK-owned presentation. Completion follows physical dismissal. */
internal class InAppPresentation(
    private val performDismiss: (complete: (Boolean) -> Unit) -> Unit,
    private val didDismiss: (Boolean) -> Unit,
) {
    private var closing = false
    private var result: Boolean? = null

    fun close(completion: (Boolean) -> Unit = {}) {
        if (result != null || closing) { completion(false); return }
        closing = true
        performDismiss { success ->
            if (result == null) {
                result = success
                didDismiss(success)
                completion(success)
            }
        }
    }
}

/** Weak references keep externally removed UI from being retained by the SDK. */
internal class InAppPresentationRegistry {
    private val entries = mutableMapOf<String, WeakReference<InAppPresentation>>()
    fun register(presentation: InAppPresentation, contentId: String) {
        entries.entries.removeAll { it.value.get() == null }
        entries[contentId] = WeakReference(presentation)
    }
    fun remove(presentation: InAppPresentation?, contentId: String) {
        if (entries[contentId]?.get() === presentation) entries.remove(contentId)
    }
    fun dismiss(contentId: String, completion: (Boolean) -> Unit) {
        val presentation = entries[contentId]?.get()
        if (presentation == null) { entries.remove(contentId); completion(false) }
        else presentation.close(completion)
    }
}
