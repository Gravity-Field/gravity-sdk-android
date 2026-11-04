package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import android.os.Looper
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class InlineContentLoaderTest {
    private lateinit var sdk: GravitySDK

    @Before fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        GravitySDK.initialize(context, "test-key", "test-section", {})
        sdk = GravitySDK.instance
        runBlocking { sdk.resetUser() }
    }

    @After fun dispose() { sdk.dispose() }

    @Test fun staleSuccessAndFailureReloadForEachIdentityTransition() = runBlocking<Unit> {
        for (oldRequestFails in listOf(false, true)) {
            for (transition in listOf("set", "reset", "restore")) {
                sdk.restoreUserId("previous")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val rendered = mutableListOf<String>()
                val failures = mutableListOf<Throwable>()
                var calls = 0
                var loading = true
                val task = async(Dispatchers.IO) {
                    sdk.loadInlineContent(
                        load = { identity ->
                            if (++calls == 1) {
                                entered.complete(Unit)
                                release.await()
                                if (oldRequestFails) error("old user's failure")
                                "old user's content"
                            } else {
                                when (transition) {
                                    "set" -> assertEquals("custom", identity.customUser?.custom)
                                    "reset" -> assertNull(identity.knownUser)
                                    "restore" -> assertEquals("restored", identity.knownUser?.uid)
                                }
                                "current user's content"
                            }
                        },
                        loaded = { rendered.add(it); loading = false },
                        failed = { _, error -> failures.add(error); loading = false },
                    )
                }
                withTimeout(5000) { entered.await() }
                when (transition) {
                    "set" -> sdk.setUser("custom", "custom-session")
                    "reset" -> sdk.resetUser()
                    "restore" -> sdk.restoreUserId("restored")
                }
                release.complete(Unit)
                finishOnMain(task)
                assertEquals("$transition, failure=$oldRequestFails", 2, calls)
                assertEquals(listOf("current user's content"), rendered)
                assertTrue(failures.isEmpty())
                assertFalse(loading)
            }
        }
    }

    @Test fun identityChangeWhileResultWaitsForMainThreadAlsoReloads() = runBlocking<Unit> {
        val received = CompletableDeferred<Unit>()
        var calls = 0
        val rendered = mutableListOf<String>()
        val task = async(Dispatchers.IO) {
            sdk.loadInlineContent(
                load = {
                    if (++calls == 1) { received.complete(Unit); "stale" } else "current"
                },
                loaded = {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    rendered.add(it)
                },
                failed = { _, error -> throw AssertionError(error) },
            )
        }
        withTimeout(5000) { received.await() }
        sdk.restoreUserId("restored")
        finishOnMain(task)
        assertEquals(listOf("current"), rendered)
        assertEquals(2, calls)
    }

    @Test fun shownContentStaysUntilANormalLoadCompletes() = runBlocking<Unit> {
        var shown = ""
        var calls = 0
        suspend fun load(value: String) {
            val task = async(Dispatchers.IO) {
                sdk.loadInlineContent(
                    load = { calls++; value },
                    loaded = { shown = it },
                    failed = { _, error -> throw AssertionError(error) },
                )
            }
            finishOnMain(task)
        }
        load("already shown")
        sdk.setUser("custom", "session")
        sdk.resetUser()
        sdk.restoreUserId("restored")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("already shown", shown)
        assertEquals(1, calls)
        load("normal reload")
        assertEquals("normal reload", shown)
    }

    @Test fun currentFailureIsDeliveredOnceAndCancellationDoesNotRetry() = runBlocking<Unit> {
        val expected = IllegalStateException("current failure")
        val failures = mutableListOf<Throwable>()
        var calls = 0
        val failure = async(Dispatchers.IO) {
            sdk.loadInlineContent<String>(
                load = { calls++; throw expected },
                loaded = { fail("unexpected content") },
                failed = { _, error -> failures.add(error) },
            )
        }
        finishOnMain(failure)
        assertEquals(1, calls)
        assertEquals(listOf(expected), failures)

        val entered = CompletableDeferred<Unit>()
        val cancelled = async(Dispatchers.IO) {
            sdk.loadInlineContent<String>(
                load = { calls++; entered.complete(Unit); awaitCancellation() },
                loaded = { fail("cancelled content") },
                failed = { _, _ -> fail("cancellation became a failure") },
            )
        }
        withTimeout(5000) { entered.await() }
        sdk.restoreUserId("changed")
        cancelled.cancelAndJoin()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(cancelled.isCancelled)
        assertEquals(2, calls)
    }

    @Test fun exceptionWhileApplyingContentCollapsesPlacementWithoutRetry() = runBlocking<Unit> {
        val expected = IllegalStateException("cannot apply content")
        var loading = true
        var height = 100.0
        var calls = 0
        val failures = mutableListOf<Throwable>()
        val task = async(Dispatchers.IO) {
            sdk.loadInlineContent(
                load = { calls++; "content" },
                loaded = { throw expected },
                failed = { _, error ->
                    failures.add(error)
                    loading = false
                    height = 0.0
                },
            )
        }
        finishOnMain(task)
        assertFalse(loading)
        assertEquals(0.0, height, 0.0)
        assertEquals(listOf(expected), failures)
        assertEquals(1, calls)
    }

    @Test fun exceptionInFailureCleanupDoesNotEscapeOrRetry() = runBlocking<Unit> {
        for (loadFails in listOf(false, true)) {
            val expected = IllegalStateException("content failure")
            val cleanupFailure = IllegalStateException("cannot finish cleanup")
            var loading = true
            var height = 100.0
            var calls = 0
            val failures = mutableListOf<Throwable>()
            val task = async(Dispatchers.IO) {
                sdk.loadInlineContent(
                    load = { calls++; if (loadFails) throw expected else "content" },
                    loaded = { throw expected },
                    failed = { _, error ->
                        failures.add(error)
                        loading = false
                        height = 0.0
                        throw cleanupFailure
                    },
                )
            }
            finishOnMain(task)
            assertFalse(loading)
            assertEquals(0.0, height, 0.0)
            assertEquals(listOf(expected), failures)
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationFromDeliveryCallbacksStillPropagates() = runBlocking<Unit> {
        for (loadFails in listOf(false, true)) {
            var calls = 0
            var failureCalls = 0
            val task = async(Dispatchers.IO) {
                sdk.loadInlineContent(
                    load = { calls++; if (loadFails) error("load failed") else "content" },
                    loaded = { throw CancellationException("cancel loaded") },
                    failed = { _, _ -> failureCalls++; throw CancellationException("cancel failed") },
                )
            }
            try {
                finishOnMain(task)
                fail("callback cancellation was swallowed")
            } catch (_: CancellationException) { }
            assertTrue(task.isCancelled)
            assertEquals(1, calls)
            assertEquals(if (loadFails) 1 else 0, failureCalls)
        }
    }

    private suspend fun finishOnMain(task: Deferred<Unit>) = withTimeout(5000) {
        while (!task.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(1)
        }
        task.await()
    }
}
