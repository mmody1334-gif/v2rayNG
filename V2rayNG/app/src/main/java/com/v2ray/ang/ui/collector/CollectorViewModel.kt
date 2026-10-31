package com.v2ray.ang.ui.collector

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AutoCleanSummary
import com.v2ray.ang.dto.CollectorData
import com.v2ray.ang.handler.AutoCleanManager
import com.v2ray.ang.handler.CollectorManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

enum class CollectorSection { Quick, Protocol, Country }

/** One selectable collector list. [file] is the stable identity (e.g. "country/DE.txt"). */
data class CollectorItemUi(
    val file: String,
    val section: CollectorSection,
    val icon: String,
    val title: String,
    val count: Int,
    val bestMs: Int?,
    val added: Boolean,
)

data class CollectorUiState(
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val alive: Int = 0,
    val countries: Int = 0,
    val items: List<CollectorItemUi> = emptyList(),
    val selected: Set<String> = emptySet(),
    val autoClean: Boolean = false,
    val lastClean: AutoCleanSummary? = null,
)

class CollectorViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(
        CollectorUiState(autoClean = AutoCleanManager.isEnabled(), lastClean = AutoCleanManager.lastSummary())
    )
    val state: StateFlow<CollectorUiState> = _state.asStateFlow()

    private var data: CollectorData? = null

    fun load() {
        launchLoading {
            val result: Pair<CollectorData?, Map<String, String>> = withContext(Dispatchers.IO) {
                try {
                    CollectorManager.fetchData() to CollectorManager.existingSubscriptions()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Collector: load failed", e)
                    Pair(null, emptyMap())
                }
            }
            data = result.first
            if (result.first == null) {
                _state.update { it.copy(loadFailed = true) }
                return@launchLoading
            }
            rebuild(result.second)
        }
    }

    fun refreshCleanStatus() {
        _state.update {
            it.copy(autoClean = AutoCleanManager.isEnabled(), lastClean = AutoCleanManager.lastSummary())
        }
    }

    fun toggle(file: String) {
        _state.update { s ->
            s.copy(selected = if (file in s.selected) s.selected - file else s.selected + file)
        }
    }

    fun selectAllCountries() {
        _state.update { s ->
            val countries = s.items.filter { it.section == CollectorSection.Country && !it.added }.map { it.file }
            val all = countries.all { it in s.selected }
            s.copy(selected = if (all) s.selected - countries.toSet() else s.selected + countries)
        }
    }

    fun addSelected() {
        val s = _state.value
        val chosen = s.items.filter { it.file in s.selected && !it.added }
            .map { it.file to "${it.icon} ${it.title}" }
        if (chosen.isEmpty()) return
        launchLoading {
            val count = withContext(Dispatchers.IO) {
                CollectorManager.addSubscriptions(app, chosen)
            }
            _state.update { it.copy(selected = emptySet()) }
            rebuild(withContext(Dispatchers.IO) { CollectorManager.existingSubscriptions() })
            toastSuccess(getString(R.string.collector_added_count, count))
        }
    }

    fun remove(file: String) {
        launchLoading {
            withContext(Dispatchers.IO) { CollectorManager.removeSubscription(app, file) }
            rebuild(withContext(Dispatchers.IO) { CollectorManager.existingSubscriptions() })
        }
    }

    fun setAutoClean(enabled: Boolean) {
        AutoCleanManager.setEnabled(app, enabled)
        refreshCleanStatus()
    }

    fun runCleanNow() {
        AutoCleanManager.runNow(app)
        toast(R.string.collector_run_now_started)
    }

    private suspend fun rebuild(existing: Map<String, String>) {
        val d = data ?: return
        val locale = localizedContext.resources.configuration.locales[0] ?: Locale.getDefault()
        val items = withContext(Dispatchers.Default) {
            buildList {
                d.files?.get("clean")?.takeIf { it > 0 }?.let {
                    add(CollectorItemUi("clean.txt", CollectorSection.Quick, "🏠",
                        getString(R.string.collector_clean), it, null, "clean.txt" in existing))
                }
                d.files?.get("gulf4")?.takeIf { it > 0 }?.let {
                    add(CollectorItemUi("gulf4.txt", CollectorSection.Quick, "🕓",
                        getString(R.string.collector_gulf4), it, null, "gulf4.txt" in existing))
                }
                d.files?.get("fast")?.let {
                    add(CollectorItemUi("fast.txt", CollectorSection.Quick, "⚡",
                        getString(R.string.collector_fastest), it, null, "fast.txt" in existing))
                }
                d.files?.get("all")?.let {
                    add(CollectorItemUi("all.txt", CollectorSection.Quick, "🌍",
                        getString(R.string.collector_all), it, null, "all.txt" in existing))
                }
                d.protocols.orEmpty().forEach { p ->
                    val file = "protocol/${p.name}.txt"
                    add(CollectorItemUi(file, CollectorSection.Protocol, "🔐",
                        protocolName(p.name), p.total, p.best, file in existing))
                }
                d.countries.orEmpty().forEach { c ->
                    val file = "country/${c.code}.txt"
                    add(CollectorItemUi(file, CollectorSection.Country, c.flag,
                        countryName(c.code, locale), c.total, c.best, file in existing))
                }
            }
        }
        _state.update {
            it.copy(
                loaded = true,
                loadFailed = false,
                alive = d.stats?.alive ?: 0,
                countries = d.stats?.countries ?: 0,
                items = items,
                selected = it.selected.filterTo(mutableSetOf()) { f -> items.any { i -> i.file == f && !i.added } },
            )
        }
    }

    private fun countryName(code: String, locale: Locale): String =
        if (code.length != 2 || code == "XX") getString(R.string.collector_unknown_country)
        else runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }
            .getOrNull().orEmpty().ifBlank { code }

    private fun protocolName(name: String): String = when (name) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "Shadowsocks"
        "hysteria2" -> "Hysteria2"
        else -> name
    }
}
