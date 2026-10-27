package ai.gravityfield.gravity_sdk.utils

import java.util.concurrent.atomic.AtomicLong

internal class PresentationRevision {
    private val revision = AtomicLong()
    val current: Long get() = revision.get()
    fun invalidate() { revision.incrementAndGet() }
    fun canPresent(expected: Long, active: Boolean, locked: Boolean): Boolean =
        current == expected && active && !locked
}
