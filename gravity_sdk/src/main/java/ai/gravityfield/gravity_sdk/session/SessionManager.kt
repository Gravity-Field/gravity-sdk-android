package ai.gravityfield.gravity_sdk.session

import ai.gravityfield.gravity_sdk.models.User
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

internal interface UserIdStorage {
    val isReady: Boolean get() = true
    suspend fun awaitReady() = Unit
    fun read(): String?
    fun write(uid: String)
    fun remove()
}

/** State transitions never wait for disk; an independent IO queue orders all storage access. */
internal class SessionManager(
    private val storage: UserIdStorage,
    private val onStorageFailure: (Exception) -> Unit = {},
) {
    data class Request internal constructor(
        val generation: Long,
        val customUser: User?,
        val identityVersion: Long,
        val knownUser: User?,
        internal val storedUid: Deferred<String?>? = null,
    )
    class Change internal constructor(internal val completion: Deferred<Unit>) {
        suspend fun await() = completion.await()
    }
    private data class Lease(val user: User, val generation: Long, val gate: CompletableDeferred<Unit>? = null)
    private val lock = Any()
    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var storageTail: Job? = null
    private var loading: Pair<Long, Deferred<String?>>? = null
    private var generation = 0L
    private var identityVersion = 0L
    private var identityChanged = CompletableDeferred<Unit>()
    private var uid: String? = null
    private var loaded = false
    private var ses: String? = null
    private var onDisk = false
    private var custom: User? = null
    private var initialization: CompletableDeferred<Unit>? = null
    private var listener: ((String?) -> Unit)? = null
    private var notifiedUid: String? = null
    private var hasPublishedIdentity = false

    val customUser: User? get() = synchronized(lock) { custom }
    fun snapshot(): Request = synchronized(lock) {
        Request(generation, custom, identityVersion, custom ?: uid?.let { User(uid = it, ses = ses) },
            if (!loaded && custom == null && initialization == null) storedRead(generation) else null)
    }
    fun isCurrent(request: Request): Boolean = synchronized(lock) { current(request) }
    private fun current(request: Request) = request.identityVersion == identityVersion
    fun setUserIdListener(listener: ((String?) -> Unit)?) = synchronized(lock) { this.listener = listener }
    fun cachedUser(): User = synchronized(lock) { User(uid = uid, ses = ses) }

    // Tracking must never block on disk or a session round trip. Requests/getUserId load storage first.
    // Engagement and WRIMP protection use this only after a content request has loaded the UID;
    // before that point a cold process deliberately has no observed identity.
    fun userForRequest(customerUser: User?): User = customerUser ?: cachedUser()

    // Called under lock so IO operations retain the order in which transitions were raised.
    private fun <T> enqueue(operation: suspend () -> T): Deferred<T> {
        val previous = storageTail
        val task = storageScope.async(start = CoroutineStart.LAZY) { previous?.join(); storage.awaitReady(); operation() }
        storageTail = task
        task.start()
        return task
    }

    // Queue the read when the event is raised, before any subsequent reset/remove operation.
    // Its result belongs to that request even if the live generation changes before IO finishes.
    private fun storedRead(captured: Long): Deferred<String?> =
        loading?.takeIf { it.first == captured && !it.second.isCompleted }?.second ?: enqueue {
            val stored = storage.read()
            synchronized(lock) {
                if (generation == captured && !loaded) {
                    uid = stored
                    loaded = true
                    onDisk = stored != null
                }
            }
            stored
        }.also { loading = captured to it }

    private suspend fun loadStored(captured: Long) {
        val task = synchronized(lock) {
            if (generation != captured || loaded) return
            storedRead(captured)
        }
        task.await()
    }

    suspend fun getUserId(): String? {
        while (true) {
            val (gate, captured) = synchronized(lock) {
                if (initialization == null && loaded) return uid
                initialization to generation
            }
            if (gate != null) gate.await() else loadStored(captured)
        }
    }

    fun setUser(userId: String, sessionId: String) = synchronized(lock) {
        val next = User(custom = userId, ses = sessionId)
        if (custom != next) { custom = next; changeContentIdentity().complete(Unit) }
    }

    /** Every SDK initialization replaces content ownership, including anonymous sessions. */
    fun initialize() = synchronized(lock) {
        custom = null
        changeContentIdentity().complete(Unit)
        if (!loaded) storedRead(generation)
        Unit
    }

    private fun changeContentIdentity(): CompletableDeferred<Unit> {
        identityVersion++
        val previous = identityChanged
        identityChanged = CompletableDeferred()
        return previous
    }

    fun beginReset(): Change = beginChange(null)
    fun beginRestore(value: String): Change {
        val restored = value.trim()
        require(restored.isNotEmpty()) { "uid must not be blank" }
        return beginChange(restored)
    }
    suspend fun resetUser() = beginReset().await()
    suspend fun restoreUserId(restored: String) = beginRestore(restored).await()

    // Consecutive pending resets share disk evidence even when an older reset is superseded.
    private class ResetState(var previousUid: String? = null, var announced: Boolean = false)
    private var pendingReset: ResetState? = null

    private fun beginChange(restored: String?): Change = synchronized(lock) {
        val reset = if (restored == null) {
            pendingReset ?: ResetState().also { pendingReset = it }
        } else {
            pendingReset = null
            null
        }
        val known = uid ?: notifiedUid
        val deferredStorage = !storage.isReady
        generation++
        val captured = generation
        val changed = changeContentIdentity()
        val previousGate = initialization
        val gate = CompletableDeferred<Unit>()
        initialization = gate
        custom = null
        uid = if (deferredStorage) restored else null
        ses = null
        loaded = true
        onDisk = false
        // Context may not exist yet. Accept the explicit identity command now;
        // only the serial persistence task waits for storage initialization.
        // An unknown reset stays silent until storage confirms a previous UID.
        val announced = if (deferredStorage) noteUid(restored) else false
        if (announced) reset?.announced = true
        val completion = enqueue {
            var previous = known
            var readSucceeded = false
            var failure: Exception? = null
            try {
                previous = storage.read()
                readSucceeded = true
                if (restored == null) storage.remove() else storage.write(restored)
            } catch (e: Exception) {
                failure = e
                throw e
            } finally {
                synchronized(lock) {
                    if (previous != null) reset?.previousUid = previous
                    if (generation == captured) {
                        // A refused commit may already have changed SharedPreferences' memory cache.
                        // Restore keeps the previous UID; reset forgets it in memory even if disk refuses.
                        uid = if (restored == null || failure == null) restored else previous
                        // Failed restore reads retry storage; reset must never reload the forgotten UID.
                        loaded = restored == null || readSucceeded
                        onDisk = failure == null && restored != null
                        if (restored == null) {
                            val previousUid = previous ?: reset?.previousUid
                            if (reset?.announced == false && previousUid != null && notifiedUid == null) {
                                notifiedUid = previousUid
                            }
                            noteUid(null)
                            pendingReset = null
                        } else if (failure == null) noteUid(restored)
                        // A newer restore may supersede an optimistic pre-Context command.
                        // Reconcile its published identity even if this operation began after Context.
                        else if (hasPublishedIdentity) noteUid(previous)
                    }
                    if (initialization === gate) initialization = null
                    gate.complete(Unit)
                }
            }
        }
        previousGate?.complete(Unit)
        changed.complete(Unit)
        Change(completion)
    }

    private fun noteUid(value: String?): Boolean {
        if (value == notifiedUid) return false
        notifiedUid = value
        hasPublishedIdentity = true
        // Invariant: the SDK wrapper only enqueues on Main (never Main.immediate).
        // It must not execute host code synchronously while this state lock is held.
        try { listener?.invoke(value) } catch (_: Exception) { }
        return true
    }

    private suspend fun acquire(request: Request, deliverAlways: Boolean): Lease? {
        val originalUser = try {
            request.storedUid?.await()?.let { User(uid = it) } ?: request.knownUser
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // After an identity change, an unreadable old snapshot must not drop raised analytics.
            // Current requests still report storage errors instead of bypassing identity storage.
            if (!deliverAlways || synchronized(lock) { request.generation == generation }) throw failure
            null
        }
        while (true) {
            var lease: Lease? = null
            var readGeneration: Long? = null
            var wait: CompletableDeferred<Unit>? = null
            lateinit var changed: CompletableDeferred<Unit>
            synchronized(lock) {
                val stale = !current(request)
                if (stale && !deliverAlways) return null
                // setUser changes content ownership, not the anonymous session this event awaited.
                // Freeze an anonymous owner only across reset/restore; otherwise the server resolves it.
                val frozen = originalUser?.takeIf {
                    request.generation != generation && (!it.uid.isNullOrEmpty() || !it.custom.isNullOrEmpty())
                }
                if (request.customUser != null || frozen != null) {
                    var owner = request.customUser ?: frozen!!
                    if (owner.ses == null && owner.uid != null && owner.uid == uid) {
                        owner = owner.copy(ses = ses)
                    }
                    lease = Lease(owner, request.generation)
                } else {
                    wait = initialization
                    if (wait == null) {
                        if (!loaded) readGeneration = generation else {
                            val gate = if (ses == null) CompletableDeferred<Unit>() else null
                            if (gate != null) initialization = gate
                            lease = Lease(User(uid = uid, ses = ses), generation, gate)
                        }
                    }
                }
                changed = identityChanged
            }
            lease?.let { return it }
            readGeneration?.let { loadStored(it) }
            wait?.let { gate -> select<Unit> { gate.onAwait { }; changed.onAwait { } } }
        }
    }

    private suspend fun saveUser(lease: Lease, user: User?) {
        if (lease.user.custom != null || user == null) return
        val task = synchronized(lock) {
            if (lease.generation != generation) return
            enqueue {
                if (synchronized(lock) { lease.generation != generation }) return@enqueue
                val received = user.uid?.takeIf { it.isNotEmpty() }
                val write = received != null && synchronized(lock) { received != uid || !onDisk }
                var written = true
                if (write) {
                    try { storage.write(received!!) }
                    catch (e: Exception) {
                        written = false
                        try { onStorageFailure(e) } catch (_: Exception) { }
                    }
                }
                synchronized(lock) {
                    if (lease.generation == generation) {
                        ses = user.ses ?: ses
                        if (received != null) {
                            uid = received
                            loaded = true
                            if (write) onDisk = written
                            noteUid(received)
                        }
                    }
                }
            }
        }
        // The independent queue must finish publishing the accepted response before its gate lifts.
        withContext(NonCancellable) { task.await() }
    }

    /** Analytics survive identity changes; only current results can reach campaign/content consumers. */
    suspend fun <T : Any> execute(
        request: Request,
        serverUser: (T) -> User?,
        deliverAlways: Boolean = false,
        send: suspend (User) -> T?,
    ): T? {
        val lease = acquire(request, deliverAlways) ?: return null
        try {
            val response = send(lease.user) ?: return null
            saveUser(lease, serverUser(response))
            return synchronized(lock) { if (current(request)) response else null }
        } finally {
            synchronized(lock) {
                if (initialization === lease.gate) initialization = null
                lease.gate?.complete(Unit)
            }
        }
    }
}
