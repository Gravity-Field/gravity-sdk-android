package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.AddToCartEvent
import ai.gravityfield.gravity_sdk.models.CancelEvent
import ai.gravityfield.gravity_sdk.models.ContentCloseEvent
import ai.gravityfield.gravity_sdk.models.ContentImpressionEvent
import ai.gravityfield.gravity_sdk.models.ContentLoadEvent
import ai.gravityfield.gravity_sdk.models.ContentVisibleImpressionEvent
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.CopyEvent
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.FollowDeeplinkEvent
import ai.gravityfield.gravity_sdk.models.FollowUrlEvent
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.ProductImpressionEvent
import ai.gravityfield.gravity_sdk.models.RequestPushEvent
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.models.TrackingEvent
import ai.gravityfield.gravity_sdk.ui.product_view_builder.LegacyProductViewBuilder
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.content.Context
import android.os.Bundle
import android.view.View
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GravitySDK.initialize(
            this,
            apiKey = "api_key",
            section = "section",
            gravityEventCallback = ::handleTrackingEvent,
            productViewBuilder = object : LegacyProductViewBuilder {
                override fun createView(context: Context, slot: Slot): View {
                    return ProductView(context, slot.item)
                }
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
                                    lifecycleScope.launch {
                                        try {
                                            GravitySDK.instance.trackView(
                                                pageContext = PageContext(
                                                    type = ContextType.PRODUCT,
                                                    data = emptyList(),
                                                    location = ""
                                                ),
                                                activityContext = context
                                            )
                                        } catch (_: Throwable) {
                                        }
                                    }
                                },
                            ) {
                                Text(text = "Track view")
                            }

                            ShowContentButton(
                                onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            GravitySDK.instance.triggerEvent(
                                                events = listOf(
                                                    AddToCartEvent(
                                                        value = 118.26,
                                                        currency = "any supported currency code",
                                                        productId = "item-34454",
                                                        quantity = 2,
                                                        cart = emptyList()
                                                    ),
                                                    CustomEvent(
                                                        type = "new_type",
                                                        name = "New name",
                                                    )
                                                ),
                                                pageContext = PageContext(
                                                    type = ContextType.CART,
                                                    data = emptyList(),
                                                    location = ""
                                                ),
                                                activityContext = context
                                            )
                                        } catch (_: Throwable) {
                                        }
                                    }
                                },
                            ) {
                                Text(text = "Trigger event")
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

    private fun handleTrackingEvent(event: TrackingEvent) {
        when (event) {
            is CancelEvent -> {
                event.content
                event.campaign
            }

            is ContentCloseEvent -> {
                event.content
                event.campaign
            }

            is ContentImpressionEvent -> {
                event.content
                event.campaign
            }

            is ContentLoadEvent -> {
                event.content
                event.campaign
            }

            is ContentVisibleImpressionEvent -> {
                event.content
                event.campaign
            }

            is CopyEvent -> {
                event.copiedValue
                event.content
                event.campaign
            }

            is FollowDeeplinkEvent -> {
                event.deeplink
                event.content
                event.campaign
            }

            is FollowUrlEvent -> {
                event.url
                event.content
                event.campaign
            }

            is ProductImpressionEvent -> {
                event.slot
                event.content
                event.campaign
            }

            is RequestPushEvent -> {
                event.content
                event.campaign
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
