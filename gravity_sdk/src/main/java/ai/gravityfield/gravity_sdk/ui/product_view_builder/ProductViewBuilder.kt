package ai.gravityfield.gravity_sdk.ui.product_view_builder

import ai.gravityfield.gravity_sdk.models.Slot
import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

interface ProductViewBuilder {

    @Composable
    fun Build(slot: Slot)
}

interface LegacyProductViewBuilder : ProductViewBuilder {

    @Composable
    override fun Build(slot: Slot) {
        AndroidView(factory = {
            createView(it, slot)
        })
    }

    fun createView(context: Context, slot: Slot): View
}
