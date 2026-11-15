package com.v2ray.ang.ui.shroudly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem

@Composable
fun ShroudlyScreen(
    viewModel: ShroudlyViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_shroudly_settings),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!uiState.isSupported) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.shroudly_not_supported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            PreferenceGroupHeader(title = stringResource(R.string.shroudly_mode_title))

            SettingsListItem(
                icon = painterResource(R.drawable.ic_shroudly_shield_24dp),
                title = stringResource(R.string.shroudly_mode_title),
                entries = listOf(
                    stringResource(R.string.shroudly_mode_off),
                    stringResource(R.string.shroudly_mode_standalone),
                    stringResource(R.string.shroudly_mode_chain)
                ),
                values = listOf(
                    AppConfig.SHROUDLY_MODE_OFF,
                    AppConfig.SHROUDLY_MODE_STANDALONE,
                    AppConfig.SHROUDLY_MODE_CHAIN
                ),
                selectedValue = uiState.mode,
                onSelected = { viewModel.setMode(it) },
                enabled = uiState.isSupported
            )

            PreferenceGroupHeader(title = stringResource(R.string.shroudly_strategy_title))

            SettingsListItem(
                title = stringResource(R.string.shroudly_strategy_title),
                entries = listOf(
                    "auto",
                    "split(sni)",
                    "tlsrec(sni+1)",
                    "disorder(sni)",
                    "oob(sni+1)",
                    "split(1),tlsrec(sni+1)"
                ),
                values = listOf(
                    "auto",
                    "split(sni)",
                    "tlsrec(sni+1)",
                    "disorder(sni)",
                    "oob(sni+1)",
                    "split(1),tlsrec(sni+1)"
                ),
                selectedValue = uiState.strategy,
                onSelected = { viewModel.setStrategy(it) },
                enabled = uiState.isSupported && uiState.mode != AppConfig.SHROUDLY_MODE_OFF
            )

            SettingsSwitchItem(
                title = stringResource(R.string.shroudly_quic_title),
                summary = stringResource(R.string.shroudly_quic_summary),
                checked = uiState.quicOff,
                onCheckedChange = { viewModel.setQuicOff(it) },
                enabled = uiState.isSupported && uiState.mode != AppConfig.SHROUDLY_MODE_OFF
            )

            PreferenceGroupHeader(title = stringResource(R.string.shroudly_stats_title))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isRunning) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                        Text(
                            text = stringResource(
                                if (uiState.isRunning) R.string.shroudly_engine_running
                                else R.string.shroudly_engine_stopped
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shroudly_stats_bypass, uiState.bypassCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.shroudly_stats_blocked, uiState.blockedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!uiState.lastError.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.shroudly_last_error, uiState.lastError ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.shroudly_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }

            SettingsMenuItem(
                icon = painterResource(R.drawable.license_24px),
                title = stringResource(R.string.shroudly_license_title),
                subtitle = stringResource(R.string.shroudly_credits),
                onClick = { showLicenseDialog = true }
            )

            NavigationBarsSpacer()
        }
    }

    if (showLicenseDialog) {
        val licenseContent = remember {
            try {
                context.assets.open("licenses/shroudly-LICENSE.txt").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                "MIT License\nhttps://github.com/umutxyp/Shroudly"
            }
        }

        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.shroudly_license_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = licenseContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
