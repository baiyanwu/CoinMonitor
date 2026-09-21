package io.baiyanwu.coinmonitor.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.repository.DappAddressParser

@Composable
internal fun ThirdPartyWebsiteWarningDialog(
    address: String,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val displayOrigin = DappAddressParser.origin(address) ?: address
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dapp_third_party_warning_title)) },
        text = {
            Column {
                Text(
                    text = displayOrigin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.dapp_third_party_warning_message))
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.dapp_third_party_warning_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
