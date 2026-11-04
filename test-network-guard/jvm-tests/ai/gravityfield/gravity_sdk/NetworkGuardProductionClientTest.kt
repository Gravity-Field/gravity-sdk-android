package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import ai.gravityfield.testguard.NetworkGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkGuardProductionClientTest {
    @Test fun unparsedNetworkHostsCannotFallBackToADirectConnection() {
        for (url in listOf("https://user:not-a-port/path", "https://éxample.test/path")) {
            val denied = NetworkGuard.deniedCount()
            val proxies = java.net.ProxySelector.getDefault().select(java.net.URI.create(url))
            assertFalse("An unparsed host must still use the deny proxy", proxies.contains(java.net.Proxy.NO_PROXY))
            assertTrue(NetworkGuard.deniedCount() > denied)
        }
    }
    @Test fun actualProductionClientIsDeniedExternallyAndWorksOnLoopback() = runBlocking {
        assertEquals("true", System.getProperty("xreview.guard.installed"))
        assertFalse("Automatic reports must default off under the committed test agent", Logger.errorReportingEnabled)
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "guard-key", "guard-section", {})
        Logger.configure(ai.gravityfield.gravity_sdk.utils.LogLevel.DEBUG)
        val sdk = GravitySDK.instance
        try {
            val denied = NetworkGuard.deniedCount()
            GravityRepository.instance.sendErrorMessage("test-guard-selftest", "")
            assertTrue("The real production report transport must reach the deny boundary", NetworkGuard.deniedCount() > denied)
            val local = NetworkGuard.fixtureCount()
            sdk.setOptions(null, null, NetworkGuard.fixtureUrl())
            sdk.getContentBySelector("101", PageContext(ContextType.OTHER, emptyList(), "/guard"))
            assertTrue("The real production choose transport must still reach a loopback fixture: " + org.robolectric.shadows.ShadowLog.getLogs().joinToString { it.msg + (it.throwable?.stackTraceToString() ?: "") }, NetworkGuard.fixtureCount() > local)
        } finally { sdk.dispose() }
    }
}
