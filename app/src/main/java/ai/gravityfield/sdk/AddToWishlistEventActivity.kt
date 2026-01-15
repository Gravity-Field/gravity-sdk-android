package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.AddToWishlistEvent
import ai.gravityfield.gravity_sdk.models.ContextType
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

class AddToWishlistEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AddToWishlistEventScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToWishlistEventScreen() {
    val context = LocalContext.current
    var value by remember { mutableStateOf("") }
    var productId by remember { mutableStateOf("") }

    var selectedContextType by remember { mutableStateOf(ContextType.HOMEPAGE) }
    var data by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
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
            text = "Add To Wishlist Event Configuration",
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // AddToWishlistEvent fields
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Value") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = productId,
            onValueChange = { productId = it },
            label = { Text("Product ID") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Divider for PageContext configuration
        Text(
            text = "Page Context Configuration",
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            color = Color.Gray
        )

        // Context Type Dropdown
        ContextTypeDropdown(
            selectedType = selectedContextType,
            onTypeSelected = { selectedContextType = it }
        )

        // Data field
        OutlinedTextField(
            value = data,
            onValueChange = { data = it },
            label = { Text("Data (comma separated)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Location field
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Language field
        OutlinedTextField(
            value = lng,
            onValueChange = { lng = it },
            label = { Text("Language (lng)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Attributes field
        OutlinedTextField(
            value = attributes,
            onValueChange = { attributes = it },
            label = { Text("Attributes (key:value pairs, comma separated)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Send Event button
        ShowContentButton(
            onClick = {
                // Parse value
                val parsedValue = value.toDoubleOrNull() ?: 0.0

                // Parse data list
                val dataList = if (data.isNotBlank()) {
                    data.split(",").map { it.trim() }
                } else {
                    emptyList()
                }

                // Parse attributes map
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

                // Create PageContext with configured values
                val pageContext = PageContext(
                    type = selectedContextType,
                    data = dataList,
                    location = location,
                    lng = if (lng.isNotBlank()) lng else null,
                    attributes = pageAttributesMap
                )

                // Create AddToWishlistEvent with configured values
                val addToWishlistEvent = AddToWishlistEvent(
                    value = parsedValue,
                    productId = productId
                )

                // Send event
                GravitySDK.instance.triggerEvent(
                    events = listOf(addToWishlistEvent),
                    pageContext = pageContext,
                    activityContext = context
                )
            },
        ) {
            Text(text = "Send event")
        }
    }
}
