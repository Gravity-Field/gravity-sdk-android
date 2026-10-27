package ai.gravityfield.gravity_sdk.utils

/** Bridges /choose to the WRIMP acknowledgement; server frequency caps remain authoritative. */
internal class InAppPresentationGate(
    private val acknowledgementTimeoutMillis: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    data class Request(val sequence: Long, val generation: Long, val screen: Long)
    data class Reservation(val id: Long, val campaignId: String, val generation: Long, val screen: Long)
    private data class Pending(
        val reservation: Reservation,
        val contentIds: MutableSet<String> = mutableSetOf(),
        var visible: Boolean = false,
        var expiresAt: Long? = null,
    )

    private var sequence = 0L
    private var generation = 0L
    private var screen = 0L
    private var location: String? = null
    private val pending = mutableMapOf<String, Pending>()
    private val confirmedThrough = mutableMapOf<String, Long>()

    @Synchronized
    fun <T> scoped(body: () -> T): T = body()

    @Synchronized
    fun beginRequest(location: String, newScreen: Boolean): Request {
        sequence++
        if (newScreen || this.location != location) {
            screen++
            this.location = location
            pending.entries.removeAll { it.value.expiresAt == null }
        }
        return Request(sequence, generation, screen)
    }

    @Synchronized
    fun isCurrent(request: Request) = request.generation == generation && request.screen == screen

    @Synchronized
    fun reset() {
        generation++
        pending.clear()
        confirmedThrough.clear()
        location = null
    }

    @Synchronized
    fun reserve(campaignId: String, request: Request): Reservation? {
        val time = now()
        pending.entries.removeAll { it.value.expiresAt?.let { expiry -> expiry <= time } == true }
        if (!isCurrent(request) || pending.containsKey(campaignId) ||
            request.sequence <= (confirmedThrough[campaignId] ?: 0L)) return null
        val reservation = Reservation(++sequence, campaignId, generation, screen)
        pending[campaignId] = Pending(reservation)
        return reservation
    }

    /** Called on the UI thread so scope validation and presentation cannot interleave. */
    @Synchronized
    fun present(reservation: Reservation, contentIds: List<String>, show: () -> Boolean): Boolean {
        val entry = pending[reservation.campaignId] ?: return false
        if (reservation.generation != generation || reservation.screen != screen ||
            entry.reservation != reservation) return false
        entry.contentIds.addAll(contentIds)
        entry.expiresAt = now() + acknowledgementTimeoutMillis
        var shown = false
        try {
            shown = show()
            return shown
        } finally {
            if (!shown) cancel(reservation)
        }
    }

    @Synchronized
    fun cancel(reservation: Reservation) {
        if (pending[reservation.campaignId]?.reservation == reservation) pending.remove(reservation.campaignId)
    }

    @Synchronized
    fun visible(contentId: String) {
        campaign(contentId)?.let { pending[it]?.visible = true }
    }

    @Synchronized
    fun closed(contentId: String) {
        campaign(contentId)?.let { if (pending[it]?.visible == false) pending.remove(it) }
    }

    @Synchronized
    fun acknowledged(contentId: String) {
        val campaignId = campaign(contentId) ?: return
        // Reject responses already in flight before this acknowledgement.
        confirmedThrough[campaignId] = sequence
        pending.remove(campaignId)
        if (confirmedThrough.size > 512) {
            confirmedThrough.minByOrNull { it.value }?.key?.let { confirmedThrough.remove(it) }
        }
    }

    private fun campaign(contentId: String) = pending.entries.firstOrNull {
        contentId in it.value.contentIds
    }?.key
}
