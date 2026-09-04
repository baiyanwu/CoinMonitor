package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.BuildConfig
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AboutScreen(
        onBack = onBack,
        onOpenAuthor = { runCatching { uriHandler.openUri(AUTHOR_URL) } },
        onOpenRepository = { runCatching { uriHandler.openUri(PROJECT_URL) } },
        onOpenLicense = { runCatching { uriHandler.openUri(LICENSE_URL) } },
        onOpenIssues = { runCatching { uriHandler.openUri(ISSUES_URL) } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AboutScreen(
    onBack: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenIssues: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.pageBackground
                ),
                title = { Text(text = stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AboutHeaderCard()
            }

            item {
                AboutSectionCard(title = stringResource(R.string.about_project_section)) {
                    Text(
                        text = stringResource(R.string.about_project_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }
            }

            item {
                AboutSectionCard(title = stringResource(R.string.about_open_source_section)) {
                    AboutInfoRow(
                        label = stringResource(R.string.about_author),
                        value = stringResource(R.string.about_author_value),
                        onClick = onOpenAuthor
                    )
                    AboutDivider()
                    AboutInfoRow(
                        label = stringResource(R.string.about_repository),
                        value = stringResource(R.string.about_repository_value),
                        onClick = onOpenRepository
                    )
                    AboutDivider()
                    AboutInfoRow(
                        label = stringResource(R.string.about_license),
                        value = stringResource(R.string.about_license_value),
                        onClick = onOpenLicense
                    )
                    AboutDivider()
                    AboutInfoRow(
                        label = stringResource(R.string.about_feedback),
                        value = stringResource(R.string.about_feedback_value),
                        onClick = onOpenIssues
                    )
                }
            }

            item {
                AboutSectionCard(title = stringResource(R.string.about_disclaimer_section)) {
                    Text(
                        text = stringResource(R.string.about_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeaderCard() {
    val colors = CoinMonitorThemeTokens.colors

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.about_version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText
            )
            Text(
                text = stringResource(R.string.about_intro),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.tertiaryText
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.accent
        )
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.5f))
}

private const val AUTHOR_URL = "https://github.com/baiyanwu"
private const val PROJECT_URL = "https://github.com/baiyanwu/CoinMonitor"
private const val LICENSE_URL = "$PROJECT_URL/blob/main/LICENSE"
private const val ISSUES_URL = "$PROJECT_URL/issues"
