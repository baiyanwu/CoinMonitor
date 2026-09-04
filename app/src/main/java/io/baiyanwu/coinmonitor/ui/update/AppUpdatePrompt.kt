package io.baiyanwu.coinmonitor.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.baiyanwu.coinmonitor.R

@Composable
fun AppUpdatePrompt(
    versionName: String,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.app_update_available_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.app_update_available_message,
                    versionName
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.app_update_later))
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenRelease) {
                Text(text = stringResource(R.string.app_update_open_release))
            }
        }
    )
}
