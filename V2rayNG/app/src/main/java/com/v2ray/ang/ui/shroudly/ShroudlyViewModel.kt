package com.v2ray.ang.ui.shroudly

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * Settings only. The engine runs in the :daemon process, so nothing about whether it runs is
 * read here (a UI-process singleton is never proof of daemon state, see service/AGENTS.md).
 */
data class ShroudlyUiState(
    val mode: String = AppConfig.SHROUDLY_MODE_OFF,
    val strategy: String = AppConfig.SHROUDLY_STRATEGY_AUTO,
    val quicOff: Boolean = true,
)

class ShroudlyViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        ShroudlyUiState(
            mode = SettingsManager.getShroudlyMode(),
            strategy = SettingsManager.getShroudlyStrategy(),
            quicOff = SettingsManager.getShroudlyQuicOff(),
        )
    )
    val uiState: StateFlow<ShroudlyUiState> = _uiState.asStateFlow()

    /** @return true when the mode changed, so a running service should restart with it. */
    fun setMode(mode: String): Boolean {
        if (mode == _uiState.value.mode) return false
        SettingsManager.setShroudlyMode(mode)
        _uiState.update { it.copy(mode = mode) }
        return true
    }

    fun setStrategy(strategy: String): Boolean {
        if (strategy == _uiState.value.strategy) return false
        SettingsManager.setShroudlyStrategy(strategy)
        _uiState.update { it.copy(strategy = strategy) }
        return true
    }

    fun setQuicOff(quicOff: Boolean): Boolean {
        if (quicOff == _uiState.value.quicOff) return false
        SettingsManager.setShroudlyQuicOff(quicOff)
        _uiState.update { it.copy(quicOff = quicOff) }
        return true
    }
}
