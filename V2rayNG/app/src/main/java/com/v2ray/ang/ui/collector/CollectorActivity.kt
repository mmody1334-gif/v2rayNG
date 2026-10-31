package com.v2ray.ang.ui.collector

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AutoCleanSummary
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem

class CollectorActivity : BaseComponentActivity() {

    private val viewModel: CollectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCleanStatus()
    }

    @Composable
    override fun ScreenContent() {
        CollectorScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
fun CollectorScreen(viewModel: CollectorViewModel, onBackClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val toAdd = state.selected.size

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.collector_title),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        },
        bottomBar = {
            if (toAdd > 0) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.addSelected() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.collector_add_selected, toAdd))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item(key = "auto_clean") {
                SettingsSwitchItem(
                    icon = painterResource(R.drawable.ic_delete_24dp),
                    title = stringResource(R.string.collector_auto_clean),
                    summary = cleanSummaryText(state.lastClean),
                    checked = state.autoClean,
                    onCheckedChange = { viewModel.setAutoClean(it) }
                )
            }
            item(key = "run_now") {
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_flash_on_24dp),
                    title = stringResource(R.string.collector_run_now),
                    subtitle = stringResource(R.string.collector_run_now_summary),
                    onClick = { viewModel.runCleanNow() }
                )
            }

            when {
                state.loadFailed && !state.loaded -> item(key = "error") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.collector_load_failed))
                        TextButton(onClick = { viewModel.load() }) {
                            Text(stringResource(R.string.collector_retry))
                        }
                    }
                }

                state.loaded -> {
                    item(key = "stats") {
                        Text(
                            text = stringResource(R.string.collector_stats, state.alive, state.countries),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    CollectorSection.entries.forEach { section ->
                        val rows = state.items.filter { it.section == section }
                        if (rows.isEmpty()) return@forEach
                        item(key = "h_${section.name}") {
                            SectionHeader(section, onSelectAll = { viewModel.selectAllCountries() })
                        }
                        items(rows, key = { it.file }) { row ->
                            CollectorRow(
                                item = row,
                                selected = row.file in state.selected,
                                onToggle = { viewModel.toggle(row.file) },
                                onRemove = { viewModel.remove(row.file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun cleanSummaryText(summary: AutoCleanSummary?): String {
    if (summary == null || summary.time == 0L) return stringResource(R.string.collector_auto_clean_never)
    val ago = DateUtils.getRelativeTimeSpanString(summary.time).toString()
    return if (summary.skippedOffline) {
        stringResource(R.string.collector_auto_clean_offline, ago)
    } else {
        stringResource(R.string.collector_auto_clean_last, ago, summary.ok, summary.removed)
    }
}

@Composable
private fun SectionHeader(section: CollectorSection, onSelectAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                when (section) {
                    CollectorSection.Quick -> R.string.collector_section_quick
                    CollectorSection.Protocol -> R.string.collector_section_protocols
                    CollectorSection.Country -> R.string.collector_section_countries
                }
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (section == CollectorSection.Country) {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.collector_select_all_countries))
            }
        }
    }
}

@Composable
private fun CollectorRow(
    item: CollectorItemUi,
    selected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected || item.added,
                enabled = !item.added,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked = selected || item.added, onCheckedChange = null, enabled = !item.added)
        Text(text = item.icon, fontSize = 26.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = if (item.added) stringResource(R.string.collector_added_badge, item.count)
                else stringResource(R.string.collector_servers_count, item.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item.bestMs?.let { ms ->
            Text(
                text = "${ms}ms",
                fontWeight = FontWeight.Bold,
                color = when {
                    ms < 300 -> Color(0xFF2E9E6B)
                    ms < 800 -> Color(0xFFC99A1E)
                    else -> Color(0xFFD9534F)
                }
            )
        }
        if (item.added) {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = stringResource(R.string.collector_remove, item.title)
                )
            }
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}
