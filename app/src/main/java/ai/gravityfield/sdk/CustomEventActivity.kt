package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

class CustomEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        CustomEventScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun CustomEventScreen() {
    val context = LocalContext.current
    var eventType by remember { mutableStateOf("add-to-cart-v1") }
    var eventName by remember { mutableStateOf("Add to cart") }
    var customProps by remember { mutableStateOf("") }

    var selectedContextType by remember { mutableStateOf(ContextType.HOMEPAGE) }
    var data by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("catalog/product/item1") }
    var lng by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Custom Event Configuration",
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = eventType,
            onValueChange = { eventType = it },
            label = { Text("Event Type") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = eventName,
            onValueChange = { eventName = it },
            label = { Text("Event Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = customProps,
            onValueChange = { customProps = it },
            label = { Text("Custom Properties (key:value pairs, comma separated)") },
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
            label = { Text("Page Ctx Data (comma separated)") },
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
            label = { Text("lng") },
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

        ShowContentButton(
            onClick = {
                val dataList = if (data.isNotBlank()) {
                    data.split(",").map { it.trim() }
                } else {
                    emptyList()
                }

                val pageAttributesMap = if (attributes.isNotBlank()) {
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

                val customPropsMap = if (customProps.isNotBlank()) {
                    customProps.split(",").associate {
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
                    attributes = pageAttributesMap
                )

                val customEvent = CustomEvent(
                    type = eventType,
                    name = eventName,
                    customProps = customPropsMap.takeIf { it.isNotEmpty() }
                )

                GravitySDK.instance.triggerEvent(
                    events = listOf(customEvent),
                    pageContext = pageContext,
                    activityContext = context
                )
            },
        ) {
            Text(text = "Send event")
        }
    }
}
