package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.mockBottomSheetData
import ai.gravityfield.gravity_sdk.mockFullScreenData
import ai.gravityfield.gravity_sdk.mockModalData
import ai.gravityfield.gravity_sdk.mockSnackbarData
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GravitySDK.init()
        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 45.dp)
                                .align(Alignment.Center)
                        ) {
                            val context = LocalContext.current
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showModal(context, mockModalData)
                                },
                            ) {
                                Text(text = "Show modal")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showSnackbar(context, mockSnackbarData)
                                },
                            ) {
                                Text(text = "Show snackbar")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showFullScreen(context, mockFullScreenData)
                                },
                            ) {
                                Text(text = "Show FullScreen")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheet(
                                        context,
                                        mockBottomSheetData
                                    )
                                },
                            ) {
                                Text(text = "Show BottomSheet")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showModal1(context)
                                },
                            ) {
                                Text(text = "Show modal 1")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showModal2(context)
                                },
                            ) {
                                Text(text = "Show modal 2")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheet(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheetBanner(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet banner")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowContentButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF7F56D9)
        ),
    ) {
        content()
    }
}
