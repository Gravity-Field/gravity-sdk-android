package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContentCloseEngagement
import ai.gravityfield.gravity_sdk.models.ContentImpressionEngagement
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEngagement
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.GravityDataResponse
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The application owns rendering and decides when to report impressions. */
class HeadlessCampaignActivity : ComponentActivity() {
    private companion object {
        // Tracking must finish even if the screen stops or its composition is removed.
        // This scope owns network work only; the composition owns each rendering delay.
        val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GravitySDKTheme {
                val scope = rememberCoroutineScope()
                var response by remember { mutableStateOf<GravityDataResponse<ContentResponse>?>(null) }
                var delayJob by remember { mutableStateOf<Job?>(null) }
                var requestVersion by remember { mutableLongStateOf(0L) }
                var status by remember { mutableStateOf("Fetch content, then render it in your own card.") }
                val page = remember { PageContext(ContextType.OTHER, emptyList(), "/custom-campaign") }
                // Tracking is never cancelled by a new tap or a lifecycle transition.
                // Only the app-owned wait is cancelled; stale network results are dropped.
                val fetch: (Boolean) -> Unit = { useEvent ->
                    val version = ++requestVersion
                    delayJob?.cancel()
                    response = null
                    status = "Loading campaign…"
                    fun isCurrent() = version == requestVersion && !isDestroyed &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    trackingScope.launch {
                        try {
                            val loaded = if (useEvent) {
                                GravitySDK.instance.triggerEventNoShow(
                                    listOf(CustomEvent(type = "custom_card_requested", name = "Custom card requested")), page,
                                )
                            } else {
                                GravitySDK.instance.trackViewNoShow(page)
                            }
                            if (isCurrent()) {
                                if (loaded == null) {
                                    status = "No campaign returned."
                                } else {
                                    status = "Campaign loaded; app waiting ${loaded.delayTime} ms…"
                                    delayJob = scope.launch {
                                        delay(loaded.delayTime.toLong())
                                        if (isCurrent()) {
                                            response = loaded
                                            status = "Content ready after ${loaded.delayTime} ms."
                                        }
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            if (isCurrent()) status = "Fetch failed: ${failure.message}"
                        }
                    }
                }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            ++requestVersion
                            delayJob?.cancel()
                            response = null
                            status = "Stopped. Request a campaign to try again."
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        ++requestVersion
                        lifecycle.removeObserver(observer)
                        delayJob?.cancel()
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("App-rendered campaign")
                    Text(status)
                    Button(onClick = { fetch(false) }) { Text("Track visit and fetch") }
                    Button(onClick = { fetch(true) }) { Text("Track event and fetch") }
                    response?.let { result ->
                        val campaign = result.data.data.firstOrNull()
                        val content = campaign?.payload?.firstOrNull()?.contents?.firstOrNull { it.step == null }
                        if (campaign == null || content == null) {
                            Text("No root content returned.")
                            return@let
                        }
                        var reported by remember(result) { mutableStateOf(false) }
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(content.rawVariables["custom_title"] as? String ?: "Campaign ${content.contentId}")
                                Text("Delivery: ${content.deliveryMethod}")
                                // Deliberately explicit: the host decides that its content is visible.
                                Button(enabled = !reported, onClick = {
                                    GravitySDK.instance.sendContentEngagement(ContentImpressionEngagement(content, campaign))
                                    GravitySDK.instance.sendContentEngagement(ContentVisibleImpressionEngagement(content, campaign))
                                    reported = true
                                }) { Text("Report card impression") }
                                Text(JSONObject(result.json).toString(2))
                                Button(onClick = {
                                    GravitySDK.instance.sendContentEngagement(ContentCloseEngagement(content, campaign))
                                    response = null
                                    status = "Card closed."
                                }) { Text("Close card") }
                            }
                        }
                    }
                }
            }
        }
    }
}
