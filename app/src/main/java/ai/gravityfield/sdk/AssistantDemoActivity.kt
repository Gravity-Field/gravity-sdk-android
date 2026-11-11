package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.assistant.AssistantPrepareResult
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private const val TAG = "GravitySDK"

private val SnackbarBackground = Color(0xFF99E2B4)
private val SnackbarContentColor = Color(0xFF054F31)

private const val ASSISTANT_ENTRY_SELECTOR = "assistant_sdk_entry"
private const val ASSISTANT_SELECTOR = "assistant_sdk_gate"

@Composable
private fun AssistantSnackbar(message: String) {
    Snackbar(
        containerColor = SnackbarBackground,
        contentColor = SnackbarContentColor,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SnackbarContentColor,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(text = message)
        }
    }
}

/** Demo screen that prepares the assistant on start and shows the entry button once it is ready. */
class AssistantDemoActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GravitySDKTheme {
                var assistantEntryTitle by remember { mutableStateOf<String?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    assistantEntryTitle = prepareAssistantEntry()
                }

                LaunchedEffect(Unit) {
                    AssistantActionHandlerImpl.messages.collect { message ->
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = {
                        SnackbarHost(snackbarHostState) { data ->
                            AssistantSnackbar(message = data.visuals.message)
                        }
                    },
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Assistant demo") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = "Ваш экран с ассистентом",
                        )

                        assistantEntryTitle?.let { title ->
                            val assistantContext = LocalContext.current
                            ExtendedFloatingActionButton(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                onClick = {
                                    GravitySDK.instance.openAssistant(assistantContext)
                                },
                                containerColor = Color(0xFF7F56D9),
                                contentColor = Color.White,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Face,
                                        contentDescription = null,
                                    )
                                },
                                text = { Text(text = title) },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads the entry button campaign and prepares the assistant.
     * Returns the button title if preparation is Ready, otherwise null.
     */
    private suspend fun prepareAssistantEntry(): String? {
        val pageContext = PageContext(
            type = ContextType.HOMEPAGE,
            data = emptyList(),
            location = "homepage",
        )

        val entryResponse = GravitySDK.instance.getContentBySelector(
            selector = ASSISTANT_ENTRY_SELECTOR,
            pageContext = pageContext,
        )

        val entry = parseAssistantEntry(entryResponse)
        if (entry == null || !entry.enabled) {
            Log.d(TAG, "Assistant entry is disabled")
            return null
        }

        val result = GravitySDK.instance.prepareAssistant(
            assistantSelector = ASSISTANT_SELECTOR,
            pageContext = pageContext,
        )
        Log.d(TAG, "prepareAssistant: $result")

        return if (result is AssistantPrepareResult.Ready) entry.title else null
    }

    private fun parseAssistantEntry(response: ContentResponse?): AssistantEntry? {
        val raw = response?.data
            ?.firstOrNull()
            ?.payload
            ?.firstOrNull()
            ?.contents
            ?.firstOrNull()
            ?.custom
            ?.json
            ?: return null

        return try {
            val json = JSONObject(raw)
            AssistantEntry(
                enabled = json.optBoolean("enabled", false),
                title = json.optString("title"),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse assistant entry", e)
            null
        }
    }

    private data class AssistantEntry(
        val enabled: Boolean,
        val title: String,
    )
}
