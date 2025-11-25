package ai.gravityfield.gravity_sdk.ui

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun XmlLayoutCompose(
    layoutResId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = remember(layoutResId) {
        LayoutInflater.from(context).inflate(layoutResId, null)
    }

    AndroidView(
        factory = { view },
        modifier = modifier
    )
}
