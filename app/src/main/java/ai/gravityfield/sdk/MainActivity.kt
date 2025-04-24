package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.mockSnackbarData
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
        GravitySDK.init(
            productViewBuilder = { context, slot ->
                ProductView(context, slot.item)
            },
            productFilter = ::checkSlotValid,
        )

        enableEdgeToEdge()
        setContent {
            GravitySDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column(
                            modifier = Modifier
                                .padding(top = 32.dp)
                                .align(Alignment.Center)
                        ) {
                            val context = LocalContext.current
                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showSnackbar(context, mockSnackbarData)
                                },
                            ) {
                                Text(text = "Show snackbar")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showModal1(context)
                                },
                            ) {
                                Text(text = "Show modal 1")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showModal2(context)
                                },
                            ) {
                                Text(text = "Show modal 2")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheet(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheetBanner(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet banner")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheetProductsGrid(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet products grid")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showBottomSheetProductsRow(context)
                                },
                            ) {
                                Text(text = "Show BottomSheet products row")
                            }

                            ShowContentButton(
                                onClick = {
                                    GravitySDK.instance.showFullScreen(context)
                                },
                            ) {
                                Text(text = "Show FullScreen")
                            }

                            ShowContentButton(
                                onClick = {
                                    val intent = Intent(context, InlineBannerActivity::class.java)
                                    context.startActivity(intent)
                                },
                            ) {
                                Text(text = "Show inline banner")
                            }

                            ShowContentButton(
                                onClick = {
                                    val intent = Intent(context, InlineProductsActivity::class.java)
                                    context.startActivity(intent)
                                },
                            ) {
                                Text(text = "Show inline products")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkSlotValid(slot: Slot): Boolean {
        return slot.item.price != "82990"
    }
}

@Composable
fun ShowContentButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = Modifier
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
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
