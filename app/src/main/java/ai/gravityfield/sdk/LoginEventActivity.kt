package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.LoginEvent
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

class LoginEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        LoginEventScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginEventScreen() {
    val context = LocalContext.current
    var hashedEmail by remember { mutableStateOf("") }
    var cuid by remember { mutableStateOf("") }
    var cuidType by remember { mutableStateOf("") }

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
            text = "Login Event Configuration",
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = hashedEmail,
            onValueChange = { hashedEmail = it },
            label = { Text("Hashed Email (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = cuid,
            onValueChange = { cuid = it },
            label = { Text("CUID (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = cuidType,
            onValueChange = { cuidType = it },
            label = { Text("CUID Type (optional)") },
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

                val pageContext = PageContext(
                    type = selectedContextType,
                    data = dataList,
                    location = location,
                    lng = if (lng.isNotBlank()) lng else null,
                    attributes = pageAttributesMap
                )


                val loginEvent = LoginEvent(
                    hashedEmail = if (hashedEmail.isNotBlank()) hashedEmail else null,
                    cuid = if (cuid.isNotBlank()) cuid else null,
                    cuidType = if (cuidType.isNotBlank()) cuidType else null
                )


                GravitySDK.instance.triggerEvent(
                    events = listOf(loginEvent),
                    pageContext = pageContext,
                    activityContext = context
                )
            },
        ) {
            Text(text = "Send event")
        }
    }
}
