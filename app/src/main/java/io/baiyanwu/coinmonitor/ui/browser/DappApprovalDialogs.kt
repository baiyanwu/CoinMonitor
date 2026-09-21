package io.baiyanwu.coinmonitor.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.repository.DappSignatureRequest
import io.baiyanwu.coinmonitor.data.repository.DappWalletControlRequest
import io.baiyanwu.coinmonitor.data.wallet.WalletBiometricManager
import io.baiyanwu.coinmonitor.ui.wallet.components.rememberWalletBiometricAuthorization
import java.math.BigDecimal
import java.net.URI

@Composable
internal fun DappApprovalDialogs(
    state: DappBrowserUiState,
    viewModel: DappBrowserViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }

    state.pendingConnection?.let { approval ->
        val rejected = stringResource(R.string.browser_request_rejected)
        AlertDialog(
            onDismissRequest = { viewModel.rejectConnection(rejected) },
            title = { Text(stringResource(R.string.browser_connect_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.browser_connect_message,
                        approval.request.origin,
                        approval.walletName,
                        approval.address,
                        approval.network.displayName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::approveConnection) {
                    Text(stringResource(R.string.browser_connect_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectConnection(rejected) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    state.pendingTransaction?.let { approval ->
        DappTransactionConfirmation(
            approval = approval,
            onApprove = viewModel::approveTransaction,
            onApproveWithDerivedKey = viewModel::approveTransactionWithDerivedKey,
            biometricManager = biometricManager,
            onReject = viewModel::rejectTransaction
        )
    }

    state.pendingSignature?.let { approval ->
        DappSignatureConfirmation(
            approval = approval,
            onApprove = viewModel::approveSignature,
            onApproveWithDerivedKey = viewModel::approveSignatureWithDerivedKey,
            biometricManager = biometricManager,
            onReject = viewModel::rejectSignature
        )
    }

    state.pendingWalletControl?.let { approval ->
        DappWalletControlConfirmation(
            approval = approval,
            onApprove = viewModel::approveWalletControl,
            onReject = viewModel::rejectWalletControl
        )
    }
}

@Composable
private fun DappTransactionConfirmation(
    approval: DappTransactionApproval,
    onApprove: (String) -> Unit,
    onApproveWithDerivedKey: (ByteArray) -> Unit,
    biometricManager: WalletBiometricManager,
    onReject: (String) -> Unit
) {
    val transaction = approval.transaction
    val network = transaction.network
    val value = remember(transaction) {
        BigDecimal(transaction.valueAtomic).movePointLeft(network.decimals).stripTrailingZeros().toPlainString()
    }
    val maximumFee = remember(transaction) {
        BigDecimal(transaction.maximumFeeAtomic).movePointLeft(network.decimals).stripTrailingZeros().toPlainString()
    }
    SensitiveApprovalDialog(
        requestId = approval.request.id,
        title = stringResource(R.string.browser_transaction_title),
        rejectedMessage = stringResource(R.string.browser_transaction_rejected),
        busy = approval.broadcasting,
        errorMessage = approval.errorMessage,
        confirmLabel = stringResource(R.string.wallet_sign_and_send),
        onApprove = onApprove,
        onApproveWithDerivedKey = onApproveWithDerivedKey,
        biometricManager = biometricManager,
        onReject = onReject
    ) {
        ApprovalLine(stringResource(R.string.browser_transaction_site), approval.request.origin)
        ApprovalLine(stringResource(R.string.wallet_network), network.displayName)
        ApprovalLine(stringResource(R.string.browser_transaction_wallet), approval.walletName)
        ApprovalLine(stringResource(R.string.browser_transaction_to), compactBrowserValue(transaction.to))
        ApprovalLine(stringResource(R.string.browser_transaction_value), "$value ${network.symbol}")
        ApprovalLine(stringResource(R.string.browser_transaction_max_fee), "$maximumFee ${network.symbol}")
        ApprovalLine(
            stringResource(R.string.browser_transaction_call),
            transaction.data.take(10).ifBlank { "0x" }
        )
    }
}

@Composable
private fun DappSignatureConfirmation(
    approval: DappSignatureApproval,
    onApprove: (String) -> Unit,
    onApproveWithDerivedKey: (ByteArray) -> Unit,
    biometricManager: WalletBiometricManager,
    onReject: (String) -> Unit
) {
    val request = approval.signatureRequest
    val title = when (request) {
        is DappSignatureRequest.PersonalMessage -> stringResource(R.string.browser_personal_sign_title)
        is DappSignatureRequest.RawMessage -> stringResource(R.string.browser_raw_sign_title)
        is DappSignatureRequest.TypedData -> stringResource(R.string.browser_typed_sign_title)
    }
    SensitiveApprovalDialog(
        requestId = approval.request.id,
        title = title,
        rejectedMessage = stringResource(R.string.browser_signature_rejected),
        busy = approval.signing,
        errorMessage = approval.errorMessage,
        confirmLabel = stringResource(R.string.browser_sign_confirm),
        onApprove = onApprove,
        onApproveWithDerivedKey = onApproveWithDerivedKey,
        biometricManager = biometricManager,
        onReject = onReject
    ) {
        ApprovalLine(stringResource(R.string.browser_transaction_site), approval.request.origin)
        ApprovalLine(stringResource(R.string.wallet_network), request.network.displayName)
        ApprovalLine(stringResource(R.string.browser_transaction_wallet), approval.walletName)
        ApprovalLine(stringResource(R.string.browser_signature_method), request.originalMethod)
        when (request) {
            is DappSignatureRequest.PersonalMessage -> {
                RiskNotice(stringResource(R.string.browser_personal_sign_warning))
                SignaturePayload(stringResource(R.string.browser_signature_message), request.message)
            }
            is DappSignatureRequest.RawMessage -> {
                RiskNotice(stringResource(R.string.browser_raw_sign_warning))
                SignaturePayload(stringResource(R.string.browser_signature_data), request.dataHex)
            }
            is DappSignatureRequest.TypedData -> {
                RiskNotice(stringResource(R.string.browser_typed_sign_warning))
                ApprovalLine(stringResource(R.string.browser_typed_primary_type), request.primaryType)
                request.domainName?.let {
                    ApprovalLine(stringResource(R.string.browser_typed_domain), it)
                }
                request.verifyingContract?.let {
                    ApprovalLine(stringResource(R.string.browser_typed_contract), compactBrowserValue(it))
                }
                request.fields.forEach { field -> ApprovalLine(field.name, field.value) }
            }
        }
    }
}

@Composable
private fun DappWalletControlConfirmation(
    approval: DappWalletControlApproval,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    val rejected = stringResource(R.string.browser_wallet_control_rejected)
    val request = approval.controlRequest
    AlertDialog(
        onDismissRequest = { if (!approval.applying) onReject(rejected) },
        title = {
            Text(
                stringResource(
                    when (request) {
                        is DappWalletControlRequest.AddChain -> R.string.browser_add_chain_title
                        is DappWalletControlRequest.WatchAsset -> R.string.browser_watch_asset_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ApprovalLine(stringResource(R.string.browser_transaction_site), approval.request.origin)
                when (request) {
                    is DappWalletControlRequest.AddChain -> {
                        RiskNotice(stringResource(R.string.browser_add_chain_warning))
                        ApprovalLine(stringResource(R.string.wallet_network), request.name)
                        ApprovalLine(stringResource(R.string.browser_chain_id), request.chainId.toString())
                        ApprovalLine(stringResource(R.string.browser_native_symbol), request.symbol)
                        ApprovalLine(stringResource(R.string.browser_rpc_host), safeHost(request.rpcUrl))
                    }
                    is DappWalletControlRequest.WatchAsset -> {
                        RiskNotice(stringResource(R.string.browser_watch_asset_warning))
                        ApprovalLine(stringResource(R.string.wallet_network), request.network.displayName)
                        ApprovalLine(stringResource(R.string.browser_asset_symbol), request.symbol)
                        ApprovalLine(
                            stringResource(R.string.browser_asset_contract),
                            compactBrowserValue(request.contractAddress)
                        )
                        ApprovalLine(stringResource(R.string.browser_asset_decimals), request.decimals.toString())
                    }
                }
                approval.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove, enabled = !approval.applying) {
                if (approval.applying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onReject(rejected) }, enabled = !approval.applying) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun SensitiveApprovalDialog(
    requestId: Long,
    title: String,
    rejectedMessage: String,
    busy: Boolean,
    errorMessage: String?,
    confirmLabel: String,
    onApprove: (String) -> Unit,
    onApproveWithDerivedKey: (ByteArray) -> Unit,
    biometricManager: WalletBiometricManager,
    onReject: (String) -> Unit,
    content: @Composable () -> Unit
) {
    var password by remember(requestId) { mutableStateOf("") }
    var passwordVisible by remember(requestId) { mutableStateOf(false) }
    val authorization = rememberWalletBiometricAuthorization(
        requestKey = requestId,
        biometricManager = biometricManager,
        enabled = !busy,
        onAuthorized = onApproveWithDerivedKey
    )
    AlertDialog(
        onDismissRequest = { if (!busy) onReject(rejectedMessage) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
                if (authorization.available) {
                    OutlinedButton(
                        onClick = authorization.authenticate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wallet_use_biometric))
                    }
                    TextButton(
                        onClick = authorization.revealPassword,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wallet_tap_to_enter_password))
                    }
                }
                if (authorization.showPassword) {
                    WalletPasswordField(
                        password = password,
                        onPasswordChange = { password = it },
                        visible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        enabled = !busy
                    )
                }
                authorization.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (authorization.showPassword) {
                TextButton(
                    onClick = {
                        onApprove(password)
                        password = ""
                    },
                    enabled = password.isNotBlank() && !busy
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(confirmLabel)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onReject(rejectedMessage) }, enabled = !busy) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun WalletPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.wallet_password_reauthorize)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, enabled = enabled) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (visible) R.string.wallet_password_hide else R.string.wallet_password_show)
                )
            }
        },
        enabled = enabled
    )
}

@Composable
private fun ApprovalLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SignaturePayload(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RiskNotice(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun compactBrowserValue(value: String): String = if (value.length > 18) {
    "${value.take(8)}…${value.takeLast(6)}"
} else {
    value
}

private fun safeHost(value: String): String = runCatching { URI(value).host }.getOrNull().orEmpty()
