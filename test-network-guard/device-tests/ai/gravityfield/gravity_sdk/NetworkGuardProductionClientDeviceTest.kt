package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import ai.gravityfield.testguard.NetworkGuard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkGuardProductionClientDeviceTest {
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
        assertFalse(Logger.errorReportingEnabled)
        GravitySDK.initialize(InstrumentationRegistry.getInstrumentation().targetContext, "guard-key", "guard-section", {})
        val sdk = GravitySDK.instance
        try {
            val denied = NetworkGuard.deniedCount()
            GravityRepository.instance.sendErrorMessage("test-guard-selftest", "")
            assertTrue(NetworkGuard.deniedCount() > denied)
            val local = NetworkGuard.fixtureCount()
            sdk.setOptions(null, null, NetworkGuard.fixtureUrl())
            sdk.getContentBySelector("101", PageContext(ContextType.OTHER, emptyList(), "/guard"))
            assertTrue(NetworkGuard.fixtureCount() > local)
        } finally { sdk.dispose() }
    }
}
