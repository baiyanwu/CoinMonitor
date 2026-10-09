package io.baiyanwu.coinmonitor.ui.wallet

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.wallet.WalletBiometricManager
import io.baiyanwu.coinmonitor.data.wallet.WalletMnemonicDictionary
import io.baiyanwu.coinmonitor.domain.model.WalletActivity
import io.baiyanwu.coinmonitor.domain.model.WalletActivityDirection
import io.baiyanwu.coinmonitor.domain.model.WalletActivityStatus
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletKind
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletPasswordPolicy
import io.baiyanwu.coinmonitor.domain.model.WalletPrivateKeyType
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.domain.model.WalletProviderMode
import io.baiyanwu.coinmonitor.domain.model.modeFor
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.format.AssetAmountFormatter
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorPillOption
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorRoundedCard
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.wallet.components.WalletHomeHeader
import io.baiyanwu.coinmonitor.ui.wallet.components.WalletSelectionCard
import io.baiyanwu.coinmonitor.ui.wallet.components.WalletBalanceCard
import io.baiyanwu.coinmonitor.ui.wallet.components.WalletHomeAssetRow
import io.baiyanwu.coinmonitor.ui.wallet.components.WalletGroupedListItem
import io.baiyanwu.coinmonitor.ui.wallet.components.MnemonicInput
import io.baiyanwu.coinmonitor.ui.wallet.components.findFragmentActivity
import io.baiyanwu.coinmonitor.ui.wallet.components.hasSupportedMnemonicWordCount
import io.baiyanwu.coinmonitor.ui.wallet.components.promptWalletBiometric
import io.baiyanwu.coinmonitor.ui.wallet.components.rememberWalletBiometricAuthorization
import io.baiyanwu.coinmonitor.ui.walletwatch.formatWalletPrice
import io.baiyanwu.coinmonitor.ui.walletwatch.formatWalletQuantity
import io.baiyanwu.coinmonitor.ui.walletwatch.formatWalletValue
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random
import kotlinx.coroutines.launch

@Composable
fun WalletRoute(
    container: AppContainer,
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
    onOpenWatchWallet: () -> Unit,
    onOpenAssetSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenDappDiscovery: () -> Unit,
    onOpenPage: (WalletPage) -> Unit
) {
    val viewModel = activityWalletViewModel(container)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }

    LaunchedEffect(state.message, state.errorMessage) {
        (state.errorMessage ?: state.message)?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val priceIndexFailureMessage = stringResource(R.string.wallet_price_index_failure)
    LaunchedEffect(state.portfolio?.updatedAtMillis) {
        if (state.portfolio?.refreshFailures?.aggregateAssetIndex == true) {
            Toast.makeText(context, priceIndexFailureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.page) {
        if (state.page != WalletPage.HOME) onOpenPage(state.page)
    }

    CoinMonitorDesignTheme {
        Scaffold(
            modifier = Modifier.padding(top = contentTopInset, bottom = contentBottomInset),
            containerColor = CoinMonitorThemeTokens.colors.pageBackground,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    !state.vault.secureStorageAvailable -> SecureStorageUnavailable()
                    !state.vault.initialized -> FirstWalletSetup(
                        onCreate = viewModel::createFirstWallet,
                        onImport = viewModel::importFirstWallet
                    )
                    !state.vault.unlocked -> WalletUnlockGate(
                        onUnlock = viewModel::unlock,
                        biometricManager = biometricManager,
                        onBiometricKey = viewModel::unlockWithDerivedKey
                    )
                    state.vault.wallets.isEmpty() -> EmptyUnlockedVault(
                        onCreate = viewModel::createAdditionalWallet,
                        onImport = viewModel::importAdditionalWallet
                    )
                    else -> WalletDashboard(
                        state = state,
                        viewModel = viewModel,
                        onOpenWatchWallet = onOpenWatchWallet,
                        onOpenAssetSettings = onOpenAssetSettings,
                        onOpenNetworkSettings = onOpenNetworkSettings,
                        onOpenDappDiscovery = onOpenDappDiscovery
                    )
                }
            }
        }
    }
}

@Composable
fun WalletAddRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.ADD, onFinished, onNavigate) { _, viewModel ->
    AddWalletScreen(viewModel)
}

@Composable
fun WalletManageRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.MANAGE, onFinished, onNavigate) { state, viewModel ->
    WalletManagementScreen(state, viewModel)
}

@Composable
fun WalletAssetDetailRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.ASSET_DETAIL, onFinished, onNavigate) { state, viewModel ->
    val asset = state.selectedAsset
    if (asset == null) {
        EmptyMessage(
            title = stringResource(R.string.wallet_asset_unavailable),
            body = stringResource(R.string.wallet_asset_unavailable_description)
        )
    } else {
        WalletAssetDetailScreen(state, asset, viewModel)
    }
}

@Composable
fun WalletReceiveRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.RECEIVE, onFinished, onNavigate) { state, viewModel ->
    ReceiveScreen(state, viewModel::goHome)
}

@Composable
fun WalletSendRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.SEND, onFinished, onNavigate) { state, viewModel ->
    val context = LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }
    SendScreen(state, viewModel, biometricManager)
}

@Composable
fun WalletSecurityRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.SECURITY, onFinished, onNavigate) { state, viewModel ->
    val context = LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }
    SecurityScreen(state, viewModel, biometricManager)
}

@Composable
private fun WalletStandaloneRoute(
    container: AppContainer,
    page: WalletPage,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit,
    content: @Composable (WalletUiState, WalletViewModel) -> Unit
) {
    val viewModel = activityWalletViewModel(container)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var retainedState by remember(page) { mutableStateOf(state) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }

    SideEffect {
        if (state.page == page) retainedState = state
    }

    LaunchedEffect(state.message, state.errorMessage) {
        (state.errorMessage ?: state.message)?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.page) {
        when {
            state.page == WalletPage.HOME -> onFinished()
            state.page != page -> onNavigate(state.page)
        }
    }
    BackHandler { viewModel.goHome() }

    Box(Modifier.fillMaxSize()) {
        when {
            !state.vault.secureStorageAvailable -> SecureStorageUnavailable()
            !state.vault.initialized -> FirstWalletSetup(
                onCreate = viewModel::createFirstWallet,
                onImport = viewModel::importFirstWallet
            )
            !state.vault.unlocked -> WalletUnlockGate(
                onUnlock = viewModel::unlock,
                biometricManager = biometricManager,
                onBiometricKey = viewModel::unlockWithDerivedKey
            )
            state.vault.wallets.isEmpty() -> EmptyUnlockedVault(
                onCreate = viewModel::createAdditionalWallet,
                onImport = viewModel::importAdditionalWallet
            )
            else -> content(if (state.page == page) state else retainedState, viewModel)
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
fun WalletBackupRoute(
    container: AppContainer,
    onFinished: () -> Unit,
    onNavigate: (WalletPage) -> Unit
) = WalletStandaloneRoute(container, WalletPage.BACKUP, onFinished, onNavigate) { state, viewModel ->
    BackupScreen(state, viewModel)
}

@Composable
fun WalletBackupSelectionRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateBackup: () -> Unit
) {
    val viewModel = activityWalletViewModel(container)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }

    LaunchedEffect(state.page) {
        if (state.page != WalletPage.BACKUP) return@LaunchedEffect
        onNavigateBackup()
    }

    DetailScreenScaffold(stringResource(R.string.wallet_backup_choose_title), onBack) {
        when {
            !state.vault.secureStorageAvailable -> SecureStorageUnavailable()
            !state.vault.initialized -> FirstWalletSetup(
                onCreate = viewModel::createFirstWallet,
                onImport = viewModel::importFirstWallet
            )
            !state.vault.unlocked -> WalletUnlockGate(
                onUnlock = viewModel::unlock,
                biometricManager = biometricManager,
                onBiometricKey = viewModel::unlockWithDerivedKey
            )
            state.vault.wallets.isEmpty() -> EmptyUnlockedVault(
                onCreate = viewModel::createAdditionalWallet,
                onImport = viewModel::importAdditionalWallet
            )
            else -> WalletBackupSelectionScreen(
                wallets = state.vault.wallets,
                onSelectWallet = viewModel::selectWalletForBackup
            )
        }
    }
}

@Composable
private fun WalletBackupSelectionScreen(
    wallets: List<WalletProfile>,
    onSelectWallet: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.wallet_backup_choose_description),
                color = CoinMonitorThemeTokens.colors.secondaryText,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            )
        }
        items(wallets, key = WalletProfile::id) { wallet ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWallet(wallet.id) },
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wallet.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(
                                if (wallet.kind == WalletKind.MNEMONIC) {
                                    R.string.wallet_mnemonic_wallet
                                } else {
                                    R.string.wallet_private_key_wallet
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
internal fun activityWalletViewModel(container: AppContainer): WalletViewModel {
    val owner = checkNotNull(LocalContext.current.findFragmentActivity()) {
        "Wallet screens require a FragmentActivity host"
    }
    return viewModel(
        viewModelStoreOwner = owner,
        factory = WalletViewModel.factory(container)
    )
}

@Composable
private fun SecureStorageUnavailable() {
    EmptyMessage(
        title = stringResource(R.string.wallet_secure_storage_unavailable_title),
        body = stringResource(R.string.wallet_secure_storage_unavailable_body)
    )
}

@Composable
internal fun WalletUnlockGate(
    onUnlock: (String) -> Unit,
    biometricManager: WalletBiometricManager,
    onBiometricKey: (ByteArray) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val activity = LocalContext.current.findFragmentActivity()
    val biometricAvailable = activity != null &&
        biometricManager.isEnabled() &&
        biometricManager.canAuthenticate()
    var showPasswordFallback by rememberSaveable(biometricAvailable) {
        mutableStateOf(!biometricAvailable)
    }
    var automaticPromptStarted by rememberSaveable(biometricAvailable) { mutableStateOf(false) }
    val biometricTitle = stringResource(R.string.wallet_biometric_unlock)
    val passwordFallbackLabel = stringResource(R.string.wallet_use_password)
    val launchBiometric: () -> Unit = {
        val hostActivity = activity
        if (hostActivity == null) {
            showPasswordFallback = true
        } else {
            runCatching { biometricManager.prepareUnlockCipher() }
                .onSuccess { cipher ->
                    promptWalletBiometric(
                        activity = hostActivity,
                        cipher = cipher,
                        title = biometricTitle,
                        negativeButtonText = passwordFallbackLabel,
                        confirmationRequired = false,
                        onAuthenticationError = { showPasswordFallback = true }
                    ) { authenticatedCipher ->
                        runCatching { biometricManager.unwrapDerivedKey(authenticatedCipher) }
                            .onSuccess(onBiometricKey)
                            .onFailure {
                                biometricManager.clear()
                                showPasswordFallback = true
                            }
                    }
                }
                .onFailure {
                    biometricManager.clear()
                    showPasswordFallback = true
                }
        }
    }

    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !automaticPromptStarted) {
            automaticPromptStarted = true
            launchBiometric()
        }
    }

    if (!showPasswordFallback) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CoinMonitorThemeTokens.colors.pageBackground)
        )
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = CoinMonitorThemeTokens.colors.accent)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.wallet_unlock_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.wallet_unlock_description),
            color = CoinMonitorThemeTokens.colors.secondaryText,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        SensitiveWalletField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.wallet_password)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onUnlock(password); password = "" },
            enabled = password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CoinMonitorComponentDefaults.primaryButtonColors()
        ) { Text(stringResource(R.string.wallet_unlock_action)) }
        if (biometricAvailable) {
            OutlinedButton(
                onClick = launchBiometric,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.wallet_biometric_unlock)) }
        }
    }
}

@Composable
private fun FirstWalletSetup(
    onCreate: (String, String, String?) -> Unit,
    onImport: (String, String, WalletImportMode, String, WalletPrivateKeyType, String?) -> Unit
) {
    WalletSetupForm(firstWallet = true, onCreate = { name, password, confirm ->
        onCreate(password, confirm, name)
    }, onImport = { name, password, confirm, mode, secret, privateKeyType ->
        onImport(password, confirm, mode, secret, privateKeyType, name)
    })
}

@Composable
private fun EmptyUnlockedVault(
    onCreate: (String?) -> Unit,
    onImport: (WalletImportMode, String, WalletPrivateKeyType, String?) -> Unit
) {
    WalletSetupForm(firstWallet = false, onCreate = { name, _, _ -> onCreate(name) }, onImport = { name, _, _, mode, secret, privateKeyType ->
        onImport(mode, secret, privateKeyType, name)
    })
}

@Composable
private fun WalletSetupForm(
    firstWallet: Boolean,
    showHeader: Boolean = true,
    onCreate: (String?, String, String) -> Unit,
    onImport: (String?, String, String, WalletImportMode, String, WalletPrivateKeyType) -> Unit
) {
    var createMode by rememberSaveable { mutableStateOf(true) }
    var importMode by rememberSaveable { mutableStateOf(WalletImportMode.MNEMONIC) }
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var privateKeyType by rememberSaveable { mutableStateOf(WalletPrivateKeyType.EVM) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    val passwordStrong = WalletPasswordPolicy.isStrong(password)
    val passwordsMatch = password == confirmation
    val importSecretValid = when (importMode) {
        WalletImportMode.MNEMONIC -> hasSupportedMnemonicWordCount(secret)
        WalletImportMode.PRIVATE_KEY -> secret.isNotBlank()
    }

    if (!createMode) {
        PreventScreenCaptureEffect()
    }

    val formPadding = PaddingValues(
        start = 20.dp,
        top = if (showHeader) 20.dp else 8.dp,
        end = 20.dp,
        bottom = 20.dp
    )

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(formPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showHeader) {
            Spacer(Modifier.height(8.dp))
            Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(48.dp), tint = CoinMonitorThemeTokens.colors.accent)
            Text(stringResource(R.string.wallet_setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.wallet_setup_description), color = CoinMonitorThemeTokens.colors.secondaryText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = createMode,
                onClick = { createMode = true },
                label = { Text(stringResource(R.string.wallet_create)) },
                colors = CoinMonitorComponentDefaults.filterChipColors()
            )
            FilterChip(
                selected = !createMode,
                onClick = { createMode = false },
                label = { Text(stringResource(R.string.wallet_import)) },
                colors = CoinMonitorComponentDefaults.filterChipColors()
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.wallet_name_optional)) },
            singleLine = true
        )
        if (!createMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = importMode == WalletImportMode.MNEMONIC,
                    onClick = {
                        if (importMode != WalletImportMode.MNEMONIC) {
                            importMode = WalletImportMode.MNEMONIC
                            secret = ""
                        }
                    },
                    label = { Text(stringResource(R.string.wallet_mnemonic)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
                FilterChip(
                    selected = importMode == WalletImportMode.PRIVATE_KEY,
                    onClick = {
                        if (importMode != WalletImportMode.PRIVATE_KEY) {
                            importMode = WalletImportMode.PRIVATE_KEY
                            secret = ""
                        }
                    },
                    label = { Text(stringResource(R.string.wallet_private_key)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
            }
            if (importMode == WalletImportMode.PRIVATE_KEY) {
                Text(
                    stringResource(R.string.wallet_private_key_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = privateKeyType == WalletPrivateKeyType.EVM,
                        onClick = { privateKeyType = WalletPrivateKeyType.EVM },
                        label = { Text(stringResource(R.string.wallet_private_key_evm)) },
                        colors = CoinMonitorComponentDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = privateKeyType == WalletPrivateKeyType.SOLANA,
                        onClick = { privateKeyType = WalletPrivateKeyType.SOLANA },
                        label = { Text(stringResource(R.string.wallet_private_key_solana)) },
                        colors = CoinMonitorComponentDefaults.filterChipColors()
                    )
                }
                Text(
                    if (privateKeyType == WalletPrivateKeyType.EVM) {
                        stringResource(R.string.wallet_private_key_evm_description)
                    } else {
                        stringResource(R.string.wallet_private_key_solana_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                SensitiveWalletField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.wallet_private_key)) }
                )
            } else {
                MnemonicInput(
                    value = secret,
                    onValueChange = { secret = it },
                    suggestionsFor = WalletMnemonicDictionary::suggestions,
                    isValidWord = WalletMnemonicDictionary::isValidWord,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (firstWallet) {
            PasswordSetupField(
                value = password,
                onValueChange = { password = it },
                visible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible },
                label = stringResource(R.string.wallet_password),
                supportingText = stringResource(R.string.wallet_password_requirements),
                isError = password.isNotEmpty() && !passwordStrong
            )
            PasswordSetupField(
                value = confirmation,
                onValueChange = { confirmation = it },
                visible = confirmationVisible,
                onToggleVisibility = { confirmationVisible = !confirmationVisible },
                label = stringResource(R.string.wallet_password_confirm),
                supportingText = if (confirmation.isNotEmpty() && !passwordsMatch) {
                    stringResource(R.string.wallet_password_mismatch)
                } else {
                    null
                },
                isError = confirmation.isNotEmpty() && !passwordsMatch
            )
        }
        Button(
            onClick = {
                if (createMode) onCreate(name.ifBlank { null }, password, confirmation)
                else onImport(name.ifBlank { null }, password, confirmation, importMode, secret, privateKeyType)
            },
            enabled = (!firstWallet || (passwordStrong && passwordsMatch && confirmation.isNotEmpty())) &&
                (createMode || importSecretValid),
            modifier = Modifier.fillMaxWidth(),
            colors = CoinMonitorComponentDefaults.primaryButtonColors()
        ) {
            Text(if (createMode) stringResource(R.string.wallet_create) else stringResource(R.string.wallet_import))
        }
    }
}

@Composable
private fun PasswordSetupField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    label: String,
    supportingText: String?,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.wallet_password_hide else R.string.wallet_password_show
                    )
                )
            }
        },
        supportingText = supportingText?.let { text -> { Text(text) } },
        isError = isError,
        singleLine = true
    )
}

@Composable
private fun SensitiveWalletField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.wallet_password_hide else R.string.wallet_password_show
                    )
                )
            }
        },
        minLines = minLines,
        singleLine = minLines == 1
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletDashboard(
    state: WalletUiState,
    viewModel: WalletViewModel,
    onOpenWatchWallet: () -> Unit,
    onOpenAssetSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenDappDiscovery: () -> Unit
) {
    val wallet = state.activeWallet ?: return
    val design = CoinMonitorThemeTokens.design
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.wallet_address_copied)
    var walletMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var receiveDialog by remember { mutableStateOf(false) }
    var networkPicker by remember { mutableStateOf(false) }
    var networksEditing by rememberSaveable { mutableStateOf(false) }
    var valuesVisible by rememberSaveable(wallet.id) { mutableStateOf(true) }
    val supportedNetworks = state.networkConfiguration.enabledNetworks.filter(wallet::supports)
    val pagerState = rememberPagerState(initialPage = state.contentTab.ordinal, pageCount = { WalletContentTab.entries.size })
    val pagerScope = rememberCoroutineScope()
    val assetsListState = rememberLazyListState()
    val activityListState = rememberLazyListState()

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectContentTab(WalletContentTab.entries[pagerState.currentPage])
    }

    Column(Modifier.fillMaxSize()) {
        WalletHomeHeader(onLock = viewModel::lock) {
            Box {
                CoinMonitorSmallIconButton(CoinMonitorIcons.More, stringResource(R.string.wallet_more), { moreMenu = true })
                DropdownMenu(moreMenu, { moreMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_refresh)) }, onClick = { moreMenu = false; viewModel.refresh() }, enabled = !state.refreshing)
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_dapp_browser)) }, onClick = { moreMenu = false; onOpenDappDiscovery() })
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_manage)) }, onClick = { moreMenu = false; viewModel.setPage(WalletPage.MANAGE) })
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_backup)) }, onClick = { moreMenu = false; viewModel.setPage(WalletPage.BACKUP) })
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_security)) }, onClick = { moreMenu = false; viewModel.setPage(WalletPage.SECURITY) })
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_network_settings_title)) }, onClick = { moreMenu = false; onOpenNetworkSettings() })
                    DropdownMenuItem({ Text(stringResource(R.string.wallet_watch_api_settings)) }, onClick = { moreMenu = false; onOpenAssetSettings() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.wallet_watch_hide_below_one_usd)) },
                        leadingIcon = { Checkbox(state.hideAssetsBelowOneUsd, onCheckedChange = null) },
                        onClick = { viewModel.setHideAssetsBelowOneUsd(!state.hideAssetsBelowOneUsd) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.wallet_hide_unverified_assets)) },
                        leadingIcon = { Checkbox(!state.includeUnverifiedAssets, onCheckedChange = null) },
                        onClick = { viewModel.setIncludeUnverifiedAssets(!state.includeUnverifiedAssets) }
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = design.spacing.pageHorizontal)) {
            WalletSelectionCard(
                wallet = wallet,
                address = wallet.homeAddress(state.selectedNetwork),
                onSelect = { walletMenu = true },
                onCopyAddress = { address -> copyText(context, address, copiedMessage) },
                menu = {
                    DropdownMenu(walletMenu, { walletMenu = false }) {
                        state.vault.wallets.forEach { item ->
                            DropdownMenuItem({ Text(item.name) }, onClick = { walletMenu = false; viewModel.selectWallet(item.id) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            { Text(stringResource(R.string.wallet_add)) },
                            onClick = { walletMenu = false; viewModel.setPage(WalletPage.ADD) },
                            leadingIcon = { Icon(CoinMonitorIcons.Add, null) }
                        )
                    }
                }
            )
        }
        Spacer(Modifier.height(design.spacing.group))
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!wallet.backedUp) Box(Modifier.padding(horizontal = design.spacing.pageHorizontal).padding(bottom = design.spacing.group)) {
                    BackupWarning { viewModel.setPage(WalletPage.BACKUP) }
                }
                Box(Modifier.padding(horizontal = design.spacing.pageHorizontal)) {
                    WalletBalanceCard(
                        summary = state.homeSummary(), valuesVisible = valuesVisible, refreshing = state.refreshing,
                        onToggleVisibility = { valuesVisible = !valuesVisible },
                        onSend = { viewModel.openSend() }, onReceive = { receiveDialog = true },
                        actionsEnabled = supportedNetworks.isNotEmpty()
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoinMonitorPillOption(
                        stringResource(R.string.wallet_assets), pagerState.currentPage == WalletContentTab.ASSETS.ordinal,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(WalletContentTab.ASSETS.ordinal) } },
                        modifier = Modifier.weight(1f)
                    )
                    CoinMonitorPillOption(
                        stringResource(R.string.wallet_activity), pagerState.currentPage == WalletContentTab.ACTIVITY.ordinal,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(WalletContentTab.ACTIVITY.ordinal) } },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { networkPicker = true }, modifier = Modifier.weight(1.5f).heightIn(min = design.sizes.minimumTouchTarget),
                        shape = design.shapes.pill
                    ) {
                        Text(
                            state.selectedNetwork?.displayName ?: stringResource(R.string.wallet_networks_all),
                            style = design.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(design.sizes.actionIcon))
                    }
                }
                HorizontalPager(
                    state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top
                ) { page ->
                    when (WalletContentTab.entries[page]) {
                        WalletContentTab.ASSETS -> WalletAssetsPage(state, assetsListState, onOpenAssetSettings, valuesVisible, viewModel::openAssetDetail)
                        WalletContentTab.ACTIVITY -> WalletActivityPage(state, activityListState, onOpenNetworkSettings, valuesVisible)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.inline)) {
            CoinMonitorRoundedCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                io.baiyanwu.coinmonitor.ui.components.CoinMonitorCompactListRow(
                    onClick = onOpenWatchWallet,
                    leading = { Icon(CoinMonitorIcons.Search, null, Modifier.size(design.sizes.coinIcon), tint = design.colors.accent) },
                    headline = { Text(stringResource(R.string.wallet_watch_entry), style = design.typography.rowTitle) },
                    supporting = { Text(stringResource(R.string.wallet_watch_entry_description), style = design.typography.caption, color = design.colors.secondaryText) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText) }
                )
            }
        }
    }
    if (networkPicker) ModalBottomSheet(onDismissRequest = { networkPicker = false }, containerColor = design.colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wallet_network_settings_title), Modifier.weight(1f), style = design.typography.rowTitle)
            TextButton(onClick = { networksEditing = !networksEditing }) {
                Text(stringResource(if (networksEditing) R.string.wallet_finish_network_edit else R.string.wallet_edit_networks))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = design.sizes.pickerMaximumHeight)) {
            item {
                TextButton(onClick = { viewModel.selectNetwork(null); networkPicker = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wallet_networks_all))
                    if (state.selectedNetwork == null) Icon(Icons.Rounded.Done, null)
                }
            }
            items(supportedNetworks, key = WalletNetwork::id) { network ->
                Row(Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.selectNetwork(network); networkPicker = false }, modifier = Modifier.weight(1f)) {
                        Text(network.displayName, Modifier.weight(1f))
                        if (state.selectedNetwork?.id == network.id) Icon(Icons.Rounded.Done, null)
                    }
                    if (networksEditing) CoinMonitorSmallIconButton(
                        CoinMonitorIcons.Close, stringResource(R.string.wallet_remove_network, network.displayName),
                        onClick = { viewModel.removeNetwork(network) }
                    )
                }
            }
        }
        TextButton(onClick = { networkPicker = false; onOpenNetworkSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wallet_network_manage))
        }
    }
    if (receiveDialog) ModalBottomSheet(onDismissRequest = { receiveDialog = false }, containerColor = design.colors.surface) {
        Text(stringResource(R.string.wallet_choose_receive_network), Modifier.padding(horizontal = design.spacing.pageHorizontal), style = design.typography.rowTitle)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = design.sizes.pickerMaximumHeight)) {
            items(supportedNetworks, key = WalletNetwork::id) { network ->
                TextButton(onClick = { receiveDialog = false; viewModel.openReceive(network) }, modifier = Modifier.fillMaxWidth()) { Text(network.displayName) }
            }
        }
    }
}

@Composable
private fun WalletAssetsPage(
    state: WalletUiState,
    listState: LazyListState,
    onOpenAssetSettings: () -> Unit,
    valuesVisible: Boolean,
    onOpenDetail: (SelfCustodyAsset) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CoinMonitorThemeTokens.design.spacing.pageHorizontal)
    ) {
        if (state.loading && state.portfolio == null) {
            item { WalletPageLoading() }
        } else {
            val failures = state.portfolio?.refreshFailures
            val failedAssets = failures?.assetIndex.orEmpty().filter { state.selectedNetwork == null || it.id == state.selectedNetwork.id }.toSet()
            val failedBalances = failures?.nativeBalance.orEmpty().filter { state.selectedNetwork == null || it.id == state.selectedNetwork.id }.toSet()
            if (failedAssets.isNotEmpty()) item { WalletRefreshWarning(R.string.wallet_asset_index_failure, failedAssets) }
            if (failedBalances.isNotEmpty()) item { WalletRefreshWarning(R.string.wallet_native_balance_failure, failedBalances) }
            if (state.visibleAssets.isEmpty()) item {
                if (state.okxCredentialsReady) {
                    EmptyInline(stringResource(R.string.wallet_no_assets))
                } else {
                    WalletConfigurationPrompt(
                        title = stringResource(R.string.wallet_asset_provider_not_configured),
                        description = stringResource(R.string.wallet_asset_provider_not_configured_description),
                        onClick = onOpenAssetSettings
                    )
                }
            }
            itemsIndexed(state.visibleAssets, key = { _, asset -> asset.id }) { index, asset ->
                WalletGroupedListItem(first = index == 0, last = index == state.visibleAssets.lastIndex) {
                    WalletHomeAssetRow(asset, valuesVisible) { onOpenDetail(asset) }
                }
            }
        }
    }
}

@Composable
private fun WalletActivityPage(
    state: WalletUiState,
    listState: LazyListState,
    onOpenNetworkSettings: () -> Unit,
    valuesVisible: Boolean
) {
    val activities = state.portfolio?.activities.orEmpty().filter {
        state.selectedNetwork == null || it.network.id == state.selectedNetwork.id
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CoinMonitorThemeTokens.design.spacing.pageHorizontal)
    ) {
        if (state.loading && state.portfolio == null) {
            item { WalletPageLoading() }
        } else {
            val failedActivities = state.portfolio?.refreshFailures?.activityIndex.orEmpty()
                .filter { state.selectedNetwork == null || it.id == state.selectedNetwork.id }.toSet()
            if (failedActivities.isNotEmpty()) item { WalletRefreshWarning(R.string.wallet_activity_index_failure, failedActivities) }
            if (!state.networkConfiguration.hasAlchemy) item {
                if (state.networkConfiguration.customRpcUrls.isEmpty()) {
                    WalletConfigurationPrompt(
                        title = stringResource(R.string.wallet_network_not_configured),
                        description = stringResource(R.string.wallet_network_not_configured_description),
                        onClick = onOpenNetworkSettings
                    )
                } else {
                    RpcActivityWarning(onOpenNetworkSettings)
                }
            }
            if (activities.isEmpty()) item { EmptyInline(stringResource(R.string.wallet_no_activity)) }
            itemsIndexed(activities, key = { _, activity -> activity.id }) { index, activity ->
                WalletGroupedListItem(first = index == 0, last = index == activities.lastIndex) {
                    ActivityRow(activity, valuesVisible = valuesVisible, compact = true)
                }
            }
        }
    }
}

@Composable
private fun WalletPageLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
    }
}

@Composable
private fun BackupWarning(onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(CoinMonitorThemeTokens.design.spacing.cardPadding)) {
            Text(stringResource(R.string.wallet_not_backed_up), style = CoinMonitorThemeTokens.design.typography.rowTitle, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.wallet_not_backed_up_description), style = CoinMonitorThemeTokens.design.typography.caption, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun WalletConfigurationPrompt(title: String, description: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Text(title, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
            }
            Text(description, color = CoinMonitorThemeTokens.colors.secondaryText)
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.wallet_configure_network))
            }
        }
    }
}

@Composable
private fun WalletAssetDetailScreen(
    state: WalletUiState,
    asset: SelfCustodyAsset,
    viewModel: WalletViewModel
) {
    val uriHandler = LocalUriHandler.current
    val swapUrl = remember(asset.id) { uniswapSwapUrl(asset) }
    val activities = remember(state.portfolio?.activities, asset.id) {
        state.portfolio?.activities.orEmpty().filter { it.belongsTo(asset) }
    }
    DetailScreenScaffold(asset.symbol, viewModel::goHome) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoilCoinSymbolIcon(symbol = asset.symbol, iconUrl = asset.logoUrl, size = 42.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            asset.network.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${formatWalletQuantity(asset.balance)} ${asset.symbol}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            asset.valueUsd?.let { stringResource(R.string.wallet_watch_usd_value, formatWalletValue(it)) }
                                ?: stringResource(R.string.wallet_watch_no_price),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WalletAssetAction(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.wallet_receive),
                        onClick = { viewModel.openReceive(asset.network) }
                    )
                    WalletAssetAction(
                        icon = Icons.AutoMirrored.Rounded.Send,
                        label = stringResource(R.string.wallet_send),
                        enabled = asset.transferable,
                        onClick = { viewModel.openSend(asset) }
                    )
                    WalletAssetAction(
                        icon = Icons.Rounded.SwapHoriz,
                        label = stringResource(R.string.wallet_swap),
                        enabled = swapUrl != null,
                        onClick = { swapUrl?.let(uriHandler::openUri) }
                    )
                    WalletAssetAction(
                        icon = Icons.AutoMirrored.Rounded.CompareArrows,
                        label = stringResource(R.string.wallet_bridge),
                        onClick = { uriHandler.openUri(OKX_BRIDGE_URL) }
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.wallet_asset_history),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (asset.network in state.portfolio?.refreshFailures?.activityIndex.orEmpty()) {
                item { WalletRefreshWarning(R.string.wallet_activity_index_failure, setOf(asset.network)) }
            }
            if (activities.isEmpty()) {
                item { EmptyInline(stringResource(R.string.wallet_no_asset_activity)) }
            } else {
                items(activities, key = WalletActivity::id) { activity -> ActivityRow(activity) }
            }
        }
    }
}

@Composable
private fun WalletAssetAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(50),
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else CoinMonitorThemeTokens.colors.secondaryText
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else CoinMonitorThemeTokens.colors.secondaryText
        )
    }
}

@Composable
private fun ActivityRow(activity: WalletActivity, valuesVisible: Boolean = true, compact: Boolean = false) {
    val design = CoinMonitorThemeTokens.design
    val titleStyle = if (compact) design.typography.compactRowTitle else MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp)
    val subtitleStyle = if (compact) design.typography.compactRowSubtitle else MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
    val uriHandler = LocalUriHandler.current
    val transactionUrl = activity.network.transactionUrl(activity.transactionHash)
    val direction = when (activity.direction) {
        WalletActivityDirection.INCOMING -> stringResource(R.string.wallet_activity_received)
        WalletActivityDirection.OUTGOING -> stringResource(R.string.wallet_activity_sent)
        WalletActivityDirection.SELF -> stringResource(R.string.wallet_activity_self)
        WalletActivityDirection.UNKNOWN -> stringResource(R.string.wallet_activity_unknown)
    }
    val status = when (activity.status) {
        WalletActivityStatus.PENDING -> stringResource(R.string.wallet_activity_pending)
        WalletActivityStatus.CONFIRMED -> stringResource(R.string.wallet_activity_confirmed)
        WalletActivityStatus.FAILED -> stringResource(R.string.wallet_activity_failed)
        WalletActivityStatus.STALE -> stringResource(R.string.wallet_activity_stale)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = transactionUrl != null) {
                transactionUrl?.let(uriHandler::openUri)
            }.heightIn(min = if (compact) design.sizes.listRowMinimumHeight else 0.dp)
                .padding(horizontal = if (compact) design.spacing.cardPadding else 4.dp, vertical = if (compact) design.spacing.inline else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "$direction · ${activity.symbol}",
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${activity.network.displayName} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(activity.timestampMillis))}",
                    style = subtitleStyle,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (valuesVisible) activity.amount?.let(AssetAmountFormatter::token) ?: "—" else "••••",
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    status,
                    style = subtitleStyle,
                    color = if (activity.status == WalletActivityStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        CoinMonitorThemeTokens.colors.secondaryText
                    }
                )
            }
        }
        if (!compact) HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.55f))
    }
}

@Composable
private fun RpcActivityWarning(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.wallet_rpc_activity_warning),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.wallet_configure_network))
        }
    }
}

@Composable
private fun WalletManagementScreen(state: WalletUiState, viewModel: WalletViewModel) {
    var renameTarget by remember { mutableStateOf<WalletProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<WalletProfile?>(null) }
    DetailColumn(stringResource(R.string.wallet_manage), viewModel::goHome) {
        items(state.vault.wallets, key = WalletProfile::id) { wallet ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { viewModel.selectWallet(wallet.id) }) {
                        Text(wallet.name, fontWeight = FontWeight.Bold)
                        Text(
                            if (wallet.kind == WalletKind.MNEMONIC) stringResource(R.string.wallet_mnemonic_wallet) else stringResource(R.string.wallet_private_key_wallet),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                    IconButton({ renameTarget = wallet }) { Icon(Icons.Rounded.Edit, stringResource(R.string.wallet_rename)) }
                    IconButton({ deleteTarget = wallet }) { Icon(Icons.Rounded.Delete, stringResource(R.string.wallet_delete), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            Button({ viewModel.setPage(WalletPage.ADD) }, Modifier.fillMaxWidth(), colors = CoinMonitorComponentDefaults.primaryButtonColors()) {
                Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.wallet_add), Modifier.padding(start = 6.dp))
            }
        }
    }
    renameTarget?.let { target -> RenameDialog(target, { renameTarget = null }) { viewModel.renameWallet(target.id, it); renameTarget = null } }
    deleteTarget?.let { target -> DeleteWalletDialog(target, { deleteTarget = null }) { viewModel.deleteWallet(target.id, it); deleteTarget = null } }
}

@Composable
private fun AddWalletScreen(viewModel: WalletViewModel) {
    DetailScreenScaffold(stringResource(R.string.wallet_add), viewModel::goHome) {
        WalletSetupForm(
            firstWallet = false,
            showHeader = false,
            onCreate = { name, _, _ -> viewModel.createAdditionalWallet(name) },
            onImport = { name, _, _, mode, secret, privateKeyType ->
                viewModel.importAdditionalWallet(mode, secret, privateKeyType, name)
            }
        )
    }
}

@Composable
private fun RenameDialog(wallet: WalletProfile, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(wallet.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_rename)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton({ onRename(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun DeleteWalletDialog(wallet: WalletProfile, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.wallet_delete_warning, wallet.name))
                SensitiveWalletField(password, { password = it }, label = { Text(stringResource(R.string.wallet_password)) })
            }
        },
        confirmButton = { TextButton({ onDelete(password) }, enabled = password.isNotBlank()) { Text(stringResource(R.string.wallet_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ReceiveScreen(state: WalletUiState, onBack: () -> Unit) {
    val wallet = state.activeWallet ?: return
    val network = state.receiveNetwork ?: return
    val address = wallet.addressFor(network) ?: return
    val context = LocalContext.current
    DetailScreenScaffold(stringResource(R.string.wallet_receive), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(network.displayName, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.wallet_receive_network_warning, network.displayName), color = MaterialTheme.colorScheme.error)
            QrCode(receiveQrPayload(network, address))
            Text(address, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { copyText(context, address, context.getString(R.string.wallet_address_copied)) }, colors = CoinMonitorComponentDefaults.primaryButtonColors()) {
                Icon(Icons.Rounded.ContentCopy, null); Text(stringResource(R.string.wallet_copy_address), Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun SendScreen(state: WalletUiState, viewModel: WalletViewModel, biometricManager: WalletBiometricManager) {
    val send = state.send
    val assets = state.portfolio?.assets.orEmpty().filter {
        it.transferable && state.activeWallet?.supports(it.network) == true
    }
    var assetMenu by remember { mutableStateOf(false) }
    DetailScreenScaffold(stringResource(R.string.wallet_send), viewModel::goHome) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                OutlinedButton({ assetMenu = true }, Modifier.fillMaxWidth()) {
                    Text(send.asset?.let { "${it.symbol} · ${it.network.displayName}" } ?: stringResource(R.string.wallet_choose_asset))
                    Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
                DropdownMenu(assetMenu, { assetMenu = false }) {
                    assets.forEach { asset -> DropdownMenuItem({ Text("${asset.symbol} · ${asset.network.displayName}") }, { assetMenu = false; viewModel.selectSendAsset(asset) }) }
                }
            }
            send.asset?.let { Text(stringResource(R.string.wallet_available_balance, AssetAmountFormatter.token(it.balance), it.symbol), color = CoinMonitorThemeTokens.colors.secondaryText) }
            OutlinedTextField(send.recipient, viewModel::updateRecipient, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_recipient)) }, singleLine = true)
            OutlinedTextField(
                send.amount,
                viewModel::updateAmount,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.wallet_amount)) },
                singleLine = true,
                trailingIcon = { TextButton(onClick = viewModel::setMaximum) { Text(stringResource(R.string.wallet_max)) } }
            )
            send.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            send.submittedHash?.let { hash ->
                val uriHandler = LocalUriHandler.current
                Card(Modifier.fillMaxWidth().clickable { send.asset?.network?.transactionUrl(hash)?.let(uriHandler::openUri) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.wallet_transaction_submitted), fontWeight = FontWeight.Bold)
                        Text(compactAddress(hash), color = CoinMonitorThemeTokens.colors.secondaryText)
                    }
                }
            }
            Button(
                onClick = viewModel::estimateTransfer,
                modifier = Modifier.fillMaxWidth(),
                enabled = send.asset != null && send.recipient.isNotBlank() && send.amount.isNotBlank() && !send.estimating,
                colors = CoinMonitorComponentDefaults.primaryButtonColors()
            ) {
                if (send.estimating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.wallet_review_transfer))
            }
        }
    }
    if (send.confirming && send.estimate != null) TransferConfirmation(send, viewModel, biometricManager)
}

@Composable
private fun TransferConfirmation(send: WalletSendState, viewModel: WalletViewModel, biometricManager: WalletBiometricManager) {
    var password by remember { mutableStateOf("") }
    val asset = send.asset ?: return
    val estimate = send.estimate ?: return
    val authorization = rememberWalletBiometricAuthorization(
        requestKey = estimate,
        biometricManager = biometricManager,
        enabled = !send.broadcasting,
        onAuthorized = viewModel::authorizeAndSendWithDerivedKey
    )
    AlertDialog(
        onDismissRequest = viewModel::dismissConfirmation,
        title = { Text(stringResource(R.string.wallet_confirm_transfer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfirmationLine(stringResource(R.string.wallet_network), asset.network.displayName)
                ConfirmationLine(stringResource(R.string.wallet_amount), "${send.amount} ${asset.symbol}")
                ConfirmationLine(stringResource(R.string.wallet_recipient), compactAddress(send.recipient))
                ConfirmationLine(
                    stringResource(R.string.wallet_network_fee),
                    "${AssetAmountFormatter.networkFee(estimate.fee)} ${asset.network.symbol}"
                )
                if (authorization.available) {
                    OutlinedButton(
                        onClick = authorization.authenticate,
                        enabled = !send.broadcasting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wallet_use_biometric))
                    }
                    TextButton(
                        onClick = authorization.revealPassword,
                        enabled = !send.broadcasting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wallet_tap_to_enter_password))
                    }
                }
                if (authorization.showPassword) {
                    SensitiveWalletField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.wallet_password_reauthorize)) }
                    )
                }
                authorization.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                send.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (authorization.showPassword) {
                TextButton(onClick = { viewModel.authorizeAndSend(password); password = "" }, enabled = password.isNotBlank() && !send.broadcasting) {
                    if (send.broadcasting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.wallet_sign_and_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissConfirmation, enabled = !send.broadcasting) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConfirmationLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, color = CoinMonitorThemeTokens.colors.secondaryText); Spacer(Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Medium) }
}

@Composable
private fun BackupScreen(state: WalletUiState, viewModel: WalletViewModel) {
    PreventScreenCaptureEffect()
    var password by remember { mutableStateOf("") }
    val secret = state.pendingMnemonic ?: state.revealedSecret
    val wallet = state.activeWallet ?: return
    var step by rememberSaveable(secret) { mutableStateOf(WalletBackupStep.RECORD) }
    var verificationIndices by rememberSaveable(secret) { mutableStateOf(intArrayOf()) }
    var firstAnswer by rememberSaveable(secret) { mutableStateOf("") }
    var secondAnswer by rememberSaveable(secret) { mutableStateOf("") }
    var thirdAnswer by rememberSaveable(secret) { mutableStateOf("") }
    val isMnemonic = wallet.kind == WalletKind.MNEMONIC
    val mnemonicWords = remember(secret) {
        secret?.split(' ')?.filter(String::isNotBlank).orEmpty()
    }
    val title = when {
        !isMnemonic -> stringResource(R.string.wallet_backup)
        step == WalletBackupStep.VERIFY -> stringResource(R.string.wallet_backup_verify_title)
        else -> stringResource(R.string.wallet_backup_record_title)
    }
    val onBack = {
        if (isMnemonic && step == WalletBackupStep.VERIFY) {
            step = WalletBackupStep.RECORD
        } else {
            viewModel.skipBackup()
        }
    }
    BackHandler(onBack = onBack)

    DetailScreenScaffold(title, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secret == null) {
                Text(stringResource(R.string.wallet_backup_warning), color = MaterialTheme.colorScheme.error)
                SensitiveWalletField(password, { password = it }, label = { Text(stringResource(R.string.wallet_password)) })
                Button({ viewModel.revealSecret(password); password = "" }, Modifier.fillMaxWidth(), enabled = password.isNotBlank(), colors = CoinMonitorComponentDefaults.primaryButtonColors()) {
                    Icon(Icons.Rounded.Visibility, null); Text(stringResource(R.string.wallet_reveal_secret), Modifier.padding(start = 6.dp))
                }
            } else if (isMnemonic) {
                if (step == WalletBackupStep.RECORD) {
                    Text(
                        stringResource(R.string.wallet_backup_step, 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = CoinMonitorThemeTokens.colors.accent
                    )
                    Text(stringResource(R.string.wallet_backup_warning), color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.wallet_backup_record_description),
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            mnemonicWords.chunked(2).forEachIndexed { row, pair ->
                                Row(Modifier.fillMaxWidth()) {
                                    pair.forEachIndexed { column, word ->
                                        val index = row * 2 + column + 1
                                        Text("$index. $word", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            verificationIndices = randomBackupVerificationIndices(mnemonicWords.size).toIntArray()
                            firstAnswer = ""
                            secondAnswer = ""
                            thirdAnswer = ""
                            step = WalletBackupStep.VERIFY
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mnemonicWords.size >= BACKUP_VERIFICATION_WORD_COUNT,
                        colors = CoinMonitorComponentDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.wallet_backup_recorded))
                    }
                    if (!wallet.backedUp) {
                        TextButton(onClick = viewModel::skipBackup, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.wallet_backup_later))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.wallet_backup_step, 2),
                        style = MaterialTheme.typography.labelLarge,
                        color = CoinMonitorThemeTokens.colors.accent
                    )
                    Text(stringResource(R.string.wallet_backup_verify_prompt), fontWeight = FontWeight.Bold)
                    val answers = listOf(firstAnswer, secondAnswer, thirdAnswer)
                    verificationIndices.forEachIndexed { answerIndex, wordIndex ->
                        OutlinedTextField(
                            value = answers[answerIndex],
                            onValueChange = { value ->
                                when (answerIndex) {
                                    0 -> firstAnswer = value
                                    1 -> secondAnswer = value
                                    2 -> thirdAnswer = value
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.wallet_backup_word, wordIndex + 1)) },
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.verifyBackup(verificationIndices.zip(answers).toMap())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = verificationIndices.size == BACKUP_VERIFICATION_WORD_COUNT &&
                            answers.all(String::isNotBlank),
                        colors = CoinMonitorComponentDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.wallet_backup_confirm))
                    }
                }
            } else {
                Text(stringResource(R.string.wallet_backup_warning), color = MaterialTheme.colorScheme.error)
                Text(secret, modifier = Modifier.background(CoinMonitorThemeTokens.colors.cardBackground, RoundedCornerShape(12.dp)).padding(14.dp))
                Button(
                    onClick = viewModel::skipBackup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoinMonitorComponentDefaults.primaryButtonColors()
                ) {
                    Text(stringResource(R.string.wallet_backup_done))
                }
            }
        }
    }
}

private enum class WalletBackupStep { RECORD, VERIFY }

private const val BACKUP_VERIFICATION_WORD_COUNT = 3

internal fun randomBackupVerificationIndices(
    wordCount: Int,
    random: Random = Random.Default
): List<Int> {
    require(wordCount >= BACKUP_VERIFICATION_WORD_COUNT) {
        "助记词数量不足，无法随机抽查。"
    }
    return (0 until wordCount)
        .shuffled(random)
        .take(BACKUP_VERIFICATION_WORD_COUNT)
        .sorted()
}

@Composable
private fun SecurityScreen(state: WalletUiState, viewModel: WalletViewModel, biometricManager: WalletBiometricManager) {
    var showReset by remember { mutableStateOf(false) }
    var showEnableBiometric by remember { mutableStateOf(false) }
    var showDisableBiometric by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findFragmentActivity()
    DetailScreenScaffold(stringResource(R.string.wallet_security), viewModel::goHome) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.wallet_auto_lock), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.wallet_auto_lock_description), color = CoinMonitorThemeTokens.colors.secondaryText)
                }
            }
            OutlinedButton(onClick = viewModel::lock, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Lock, null); Text(stringResource(R.string.wallet_lock_now), Modifier.padding(start = 6.dp)) }
            if (biometricManager.canAuthenticate()) {
                OutlinedButton(
                    onClick = {
                        if (biometricManager.isEnabled()) showDisableBiometric = true
                        else showEnableBiometric = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (biometricManager.isEnabled()) R.string.wallet_biometric_disable else R.string.wallet_biometric_enable))
                }
            }
            OutlinedButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallet_reset_vault), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showEnableBiometric) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEnableBiometric = false },
            title = { Text(stringResource(R.string.wallet_biometric_enable)) },
            text = { SensitiveWalletField(password, { password = it }, label = { Text(stringResource(R.string.wallet_password)) }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.verifyPassword(password) { key ->
                            if (activity == null) {
                                key.fill(0)
                            } else runCatching { biometricManager.prepareEnableCipher() }
                                .onSuccess { cipher ->
                                    promptWalletBiometric(
                                        activity = activity,
                                        cipher = cipher,
                                        title = activity.getString(R.string.wallet_biometric_enable),
                                        negativeButtonText = activity.getString(R.string.cancel)
                                    ) {
                                        try { biometricManager.finishEnable(it, key) } finally { key.fill(0) }
                                    }
                                }
                                .onFailure { key.fill(0) }
                        }
                        password = ""
                        showEnableBiometric = false
                    },
                    enabled = password.isNotBlank()
                ) { Text(stringResource(R.string.wallet_biometric_enable)) }
            },
            dismissButton = { TextButton({ showEnableBiometric = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showDisableBiometric) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableBiometric = false },
            title = { Text(stringResource(R.string.wallet_biometric_disable)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wallet_biometric_disable_warning))
                    SensitiveWalletField(
                        password,
                        { password = it },
                        label = { Text(stringResource(R.string.wallet_password)) }
                    )
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.verifyPassword(password) { key ->
                            try {
                                biometricManager.clear()
                                showDisableBiometric = false
                            } finally {
                                key.fill(0)
                            }
                        }
                        password = ""
                    },
                    enabled = password.isNotBlank()
                ) {
                    Text(stringResource(R.string.wallet_biometric_disable))
                }
            },
            dismissButton = {
                TextButton({ showDisableBiometric = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showReset) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.wallet_reset_vault)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wallet_reset_warning))
                    SensitiveWalletField(password, { password = it }, label = { Text(stringResource(R.string.wallet_password)) })
                }
            },
            confirmButton = { TextButton({ viewModel.resetVault(password, biometricManager::clear); showReset = false }, enabled = password.isNotBlank()) { Text(stringResource(R.string.wallet_reset_vault), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ showReset = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun NetworkDropdown(
    selected: WalletNetwork,
    networks: List<WalletNetwork>,
    onSelect: (WalletNetwork) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Text(selected.displayName); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            networks.forEach { network ->
                DropdownMenuItem({ Text(network.displayName) }, { expanded = false; onSelect(network) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreenScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = CoinMonitorThemeTokens.colors.pageBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CoinMonitorThemeTokens.colors.pageBackground)
            )
        }
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@Composable
private fun DetailColumn(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    DetailScreenScaffold(title, onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun QrCode(value: String) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
    Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.wallet_receive_qr), modifier = Modifier.size(260.dp))
}

@Composable
private fun PreventScreenCaptureEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasAlreadySecure = window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            if (!wasAlreadySecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun copyText(context: Context, value: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", value))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, color = CoinMonitorThemeTokens.colors.secondaryText, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun EmptyInline(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(28.dp), color = CoinMonitorThemeTokens.colors.secondaryText)
}

@Composable
private fun WalletRefreshWarning(messageRes: Int, networks: Set<WalletNetwork>) {
    Text(
        stringResource(
            messageRes,
            networks.sortedBy(WalletNetwork::displayName).joinToString { it.displayName }
        ),
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        style = MaterialTheme.typography.bodySmall
    )
}

private fun compactAddress(value: String): String = if (value.length <= 16) value else "${value.take(8)}…${value.takeLast(6)}"

private fun WalletActivity.belongsTo(asset: SelfCustodyAsset): Boolean {
    if (network.id != asset.network.id) return false
    if (asset.isNative) return tokenAddress == null && symbol.equals(asset.symbol, ignoreCase = true)
    val activityToken = tokenAddress ?: return false
    val assetToken = asset.tokenAddress ?: return false
    return if (network.isEvm) activityToken.equals(assetToken, ignoreCase = true) else activityToken == assetToken
}

private fun uniswapSwapUrl(asset: SelfCustodyAsset): String? {
    val chain = when (asset.network.chainId) {
        1L -> "mainnet"
        10L -> "optimism"
        56L -> "bnb"
        137L -> "polygon"
        8453L -> "base"
        42161L -> "arbitrum"
        else -> return null
    }
    val outputToken = asset.tokenAddress?.let { "&outputCurrency=$it" }.orEmpty()
    return "https://app.uniswap.org/swap?chain=$chain$outputToken"
}

private const val OKX_BRIDGE_URL = "https://web3.okx.com/dex-swap/bridge"

private fun receiveQrPayload(network: WalletNetwork, address: String): String = if (network.isEvm) {
    "ethereum:$address@${requireNotNull(network.chainId)}"
} else {
    "solana:$address?network=mainnet-beta"
}
