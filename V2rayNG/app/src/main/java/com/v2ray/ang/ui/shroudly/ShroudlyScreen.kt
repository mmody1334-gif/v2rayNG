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
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
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
    onBackClick: () -> Unit,
    onSettingChanged: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

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
            Text(
                text = stringResource(R.string.shroudly_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )

            PreferenceGroupHeader(title = stringResource(R.string.shroudly_choose_mode))

            ModeOption(
                selected = uiState.mode == AppConfig.SHROUDLY_MODE_OFF,
                title = stringResource(R.string.shroudly_option_off_title),
                body = stringResource(R.string.shroudly_option_off_body),
                onClick = { if (viewModel.setMode(AppConfig.SHROUDLY_MODE_OFF)) onSettingChanged() }
            )
            ModeOption(
                selected = uiState.mode == AppConfig.SHROUDLY_MODE_STANDALONE,
                title = stringResource(R.string.shroudly_option_standalone_title),
                body = stringResource(R.string.shroudly_option_standalone_body),
                onClick = { if (viewModel.setMode(AppConfig.SHROUDLY_MODE_STANDALONE)) onSettingChanged() }
            )
            ModeOption(
                selected = uiState.mode == AppConfig.SHROUDLY_MODE_CHAIN,
                title = stringResource(R.string.shroudly_option_chain_title),
                body = stringResource(R.string.shroudly_option_chain_body),
                onClick = { if (viewModel.setMode(AppConfig.SHROUDLY_MODE_CHAIN)) onSettingChanged() }
            )

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
                title = stringResource(R.string.shroudly_advanced_title),
                subtitle = stringResource(R.string.shroudly_advanced_summary),
                onClick = { showAdvanced = !showAdvanced }
            )

            if (showAdvanced) {
                SettingsListItem(
                    title = stringResource(R.string.shroudly_strategy_title),
                    entries = listOf(
                        stringResource(R.string.shroudly_strategy_auto),
                        "split(sni)",
                        "tlsrec(sni+1)",
                        "disorder(sni)",
                        "oob(sni+1)",
                        "split(1),tlsrec(sni+1)"
                    ),
                    values = listOf(
                        AppConfig.SHROUDLY_STRATEGY_AUTO,
                        "split(sni)",
                        "tlsrec(sni+1)",
                        "disorder(sni)",
                        "oob(sni+1)",
                        "split(1),tlsrec(sni+1)"
                    ),
                    selectedValue = uiState.strategy,
                    onSelected = { if (viewModel.setStrategy(it)) onSettingChanged() },
                    enabled = uiState.mode != AppConfig.SHROUDLY_MODE_OFF
                )

                SettingsSwitchItem(
                    title = stringResource(R.string.shroudly_quic_title),
                    summary = stringResource(R.string.shroudly_quic_summary),
                    checked = uiState.quicOff,
                    onCheckedChange = { if (viewModel.setQuicOff(it)) onSettingChanged() },
                    enabled = uiState.mode != AppConfig.SHROUDLY_MODE_OFF
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

@Composable
private fun ModeOption(
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
