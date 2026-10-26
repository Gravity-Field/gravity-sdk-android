package ai.gravityfield.gravity_sdk.utils

import java.util.concurrent.atomic.AtomicBoolean

/** One opening, shared by its first-frame detector, explicit dismissal and all OPEN_STEPs. */
internal class ModalImpressionSession(private val enqueueImpression: () -> Boolean) {
    private val fired = AtomicBoolean(false)
    private var appeared = false
    private var attachments = 0
    private var abandoned = false

    @Synchronized fun attach() { attachments++ }
    @Synchronized fun detach(abandon: () -> Unit) {
        attachments--
        if (attachments == 0 && !hasFired) {
            abandoned = true
            abandon()
        }
    }

    @Synchronized fun frameDrawn(active: Boolean) {
        if (active && !abandoned) appeared = true
    }
    val hasFired: Boolean get() = fired.get()
    val isAbandoned: Boolean get() = synchronized(this) { abandoned }

    /** Keep counted windows across backgrounding. Cancel unconfirmed windows without a view fallback. */
    @Synchronized fun background(closeUnconfirmed: () -> Unit) {
        if (hasFired || abandoned) return
        abandoned = true
        closeUnconfirmed()
    }

    @Synchronized fun contentFrame(active: Boolean) {
        if (!active || abandoned) return
        appeared = true
        fire()
    }

    @Synchronized fun explicitClose() {
        if (appeared && !abandoned) fire()
    }

    private fun fire() {
        if (!fired.compareAndSet(false, true)) return
        // Acceptance stages memory protection; the SDK persists asynchronously before sending.
        if (!enqueueImpression()) fired.set(false)
    }
}
