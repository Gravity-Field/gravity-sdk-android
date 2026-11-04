package ai.gravityfield.gravity_sdk.ui

import android.view.View
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun <T> GravitySDK.loadInlineContent(
    awaitActive: suspend () -> Unit = {},
    load: suspend (SessionManager.Request) -> T,
    loaded: (T) -> Unit,
    failed: (SessionManager.Request, Throwable) -> Unit,
) {
    while (true) {
        currentCoroutineContext().ensureActive()
        awaitActive()
        val identity = userIdentitySnapshot()
        val result = try {
            Result.success(load(identity))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
        val accepted = withContext(Dispatchers.Main) {
            var applied = false
            withCurrentUser(identity, stale = {}) {
                val failure = result.exceptionOrNull() ?: try {
                    loaded(result.getOrThrow())
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    error
                }
                if (failure != null) {
                    try {
                        failed(identity, failure)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // Failure cleanup is best-effort; do not crash the host or retry it.
                    }
                }
                applied = true
            }
            applied
        }
        if (accepted) return
        // An old user's result (including an error) says nothing about the current user.
        // Retry without touching the loading state or any content already on screen.
    }
}

/** Keep the original request on detach, but never issue a stale retry for a removed placement. */
internal suspend fun View.awaitInlineAttachment() = withContext(Dispatchers.Main.immediate) {
    if (isAttachedToWindow) return@withContext
    var listener: View.OnAttachStateChangeListener? = null
    try {
        suspendCancellableCoroutine<Unit> { continuation ->
            listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onViewDetachedFromWindow(view: View) = Unit
            }
            addOnAttachStateChangeListener(listener)
        }
    } finally {
        listener?.let { removeOnAttachStateChangeListener(it) }
    }
}
