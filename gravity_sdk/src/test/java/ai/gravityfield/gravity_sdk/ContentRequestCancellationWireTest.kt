package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentRequestCancellationWireTest {
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val page = PageContext(ContextType.OTHER, emptyList(), "/cancellation")
    private val repository get() = GravityRepository.instance
    private lateinit var server: HeadlessRecordingServer

    @Before fun setUp() {
        Logger.errorReportingEnabled = false
        server = HeadlessRecordingServer { _, _ ->
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            200 to """{"data":[],"campaigns":[]}"""
        }
        server.start()
        val context = RuntimeEnvironment.getApplication()
        GravitySDK.initialize(context, "cancellation-key", "section", {})
        GravitySDK.instance.setOptions(null, null, server.url)
    }

    @After fun tearDown() {
        release.countDown()
        GravitySDK.instance.dispose()
        server.stop()
        Logger.errorReportingEnabled = true
    }

    // Observe the method's actual exit inside the cancelled coroutine. Awaiting a
    // cancelled Job alone would also pass if the repository swallowed cancellation.
    private fun assertPropagatesCancellation(fetch: suspend () -> Any?) = runBlocking {
        val propagated = CompletableDeferred<Boolean>()
        val request = launch(Dispatchers.IO) {
            try {
                fetch()
                propagated.complete(false)
            } catch (error: CancellationException) {
                propagated.complete(true)
                throw error
            } catch (error: Throwable) {
                propagated.completeExceptionally(error)
                throw error
            }
        }
        assertTrue("Request must reach the server before cancellation", started.await(5, TimeUnit.SECONDS))
        request.cancel()
        release.countDown()
        assertTrue("Repository must throw cancellation instead of returning null", withTimeout(5000) { propagated.await() })
        request.join()
    }

    @Test fun visitPropagatesCancellation() = assertPropagatesCancellation {
        repository.visit(page, Options(), GravityRepository.sessionManager.snapshot())
    }

    @Test fun eventPropagatesCancellation() = assertPropagatesCancellation {
        repository.event(listOf(CustomEvent(type = "cancel", name = "Cancel")), Date(), page, Options(), GravityRepository.sessionManager.snapshot())
    }

    @Test fun chooseByCampaignIdPropagatesCancellation() = assertPropagatesCancellation {
        repository.chooseByCampaignIdWithDetails("101", Options(), ContentSettings(), pageContext = page)
    }

    @Test fun chooseBySelectorPropagatesCancellation() = assertPropagatesCancellation {
        repository.chooseBySelectorWithDetails("101", Options(), ContentSettings(), pageContext = page)
    }

    @Test fun chooseByGroupPropagatesCancellation() = assertPropagatesCancellation {
        repository.chooseByGroup("101", Options(), ContentSettings(), pageContext = page)
    }
}
