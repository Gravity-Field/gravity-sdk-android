package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class ChooseSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        ChooseSelectorScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseSelectorScreen() {
    val context = LocalContext.current
    var selector by remember { mutableStateOf("") }

    var selectedContextType by remember { mutableStateOf(ContextType.HOMEPAGE) }
    var data by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Selector Configuration",
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = selector,
            onValueChange = { selector = it },
            label = { Text("Selector") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            text = "Page Context Configuration",
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            color = Color.Gray
        )

        ContextTypeDropdown(
            selectedType = selectedContextType,
            onTypeSelected = { selectedContextType = it }
        )

        OutlinedTextField(
            value = data,
            onValueChange = { data = it },
            label = { Text("Data (comma separated)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = lng,
            onValueChange = { lng = it },
            label = { Text("Language (lng)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = attributes,
            onValueChange = { attributes = it },
            label = { Text("Attributes (key:value pairs, comma separated)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF7F56D9)
            )
        }

        if (result.isNotBlank()) {
            Text(
                text = "Result:",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                color = Color.Gray
            )
            Text(
                text = result,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = Color(0xFF7F56D9)
            )
        }

        ShowContentButton(
            onClick = {
                if (selector.isBlank()) {
                    result = "Error: Selector is required"
                    return@ShowContentButton
                }

                isLoading = true
                result = ""

                val dataList = if (data.isNotBlank()) {
                    data.split(",").map { it.trim() }
                } else {
                    emptyList()
                }

                val attributesMap = if (attributes.isNotBlank()) {
                    attributes.split(",").associate {
                        val pair = it.split(":").map { part -> part.trim() }
                        if (pair.size >= 2) {
                            pair[0] to pair[1]
                        } else {
                            pair[0] to ""
                        }
                    }
                } else {
                    emptyMap()
                }

                val pageContext = PageContext(
                    type = selectedContextType,
                    data = dataList,
                    location = location,
                    lng = lng.ifBlank { null },
                    attributes = attributesMap
                )

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = GravitySDK.instance.getContentBySelector(
                            selector = selector,
                            pageContext = pageContext
                        )
                        with(Dispatchers.Main) {
                            isLoading = false
                            result = "Campaigns count: ${response.data.size}\n" +
                                    response.data.joinToString("\n") { campaign ->
                                        "Campaign: ${campaign.selector ?: "N/A"}"
                                    }
                        }
                    } catch (e: Exception) {
                        with(Dispatchers.Main) {
                            isLoading = false
                            result = "Error: ${e.message}"
                        }
                    }
                }
            },
        ) {
            Text(text = "Send Choose Request")
        }
    }
}
