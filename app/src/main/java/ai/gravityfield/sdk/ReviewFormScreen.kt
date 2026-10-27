package ai.gravityfield.sdk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The client app's own review screen — opened by [ReviewPromptCoordinator] after `dismissInApp`
 * succeeds. Submitting calls [onSubmit], which is responsible for updating the client's review
 * status so the SKU is excluded from future shows; dismissing without submitting must NOT count
 * as a submitted review.
 */
@Composable
fun ReviewFormDialog(
    sku: String,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String) -> Unit,
) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оставить отзыв") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(sku, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (value in 1..5) {
                        Text(
                            text = if (value <= rating) "★" else "☆",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.clickable { rating = value },
                        )
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Что понравилось или не понравилось?") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, comment) }) { Text("Отправить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
