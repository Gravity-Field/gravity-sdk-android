package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Save a server UID, reset to anonymous, then restore the saved identity. */
@Composable
fun UserIdentityExample() {
    val sdk = GravitySDK.instance
    val scope = rememberCoroutineScope()
    var uid by remember { mutableStateOf<String?>(null) }
    var savedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // The listener is process-wide. This screen owns it and explicitly unregisters on disposal.
    DisposableEffect(sdk) {
        sdk.setUserIdListener { uid = it }
        onDispose { sdk.setUserIdListener(null) }
    }
    LaunchedEffect(sdk) {
        try { uid = sdk.getUserId() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
    }

    Text("Server UID: ${uid ?: "none"}")
    error?.let { Text("Identity storage error: $it") }
    Row {
        Button(enabled = uid != null && !busy, onClick = { savedUid = uid }) {
            Text("Save UID")
        }
        Button(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                error = null
                try { sdk.resetUser(); uid = sdk.getUserId() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message }
                finally { busy = false }
            }
        }) { Text("Reset") }
        Button(enabled = savedUid != null && !busy, onClick = {
            val restore = savedUid ?: return@Button
            scope.launch {
                busy = true
                error = null
                try { sdk.restoreUserId(restore); uid = sdk.getUserId() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message }
                finally { busy = false }
            }
        }) { Text("Restore") }
    }
}
