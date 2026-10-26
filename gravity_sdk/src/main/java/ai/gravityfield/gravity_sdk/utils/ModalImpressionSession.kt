package ai.gravityfield.gravity_sdk.utils

import java.util.concurrent.atomic.AtomicBoolean

/** One opening, shared by its first-frame detector, explicit dismissal and all OPEN_STEPs. */
internal class ModalImpressionSession(private val persistImpression: () -> Boolean) {
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
        // A failed disk write must never masquerade as a persisted event.
        if (!persistImpression()) fired.set(false)
    }
}
