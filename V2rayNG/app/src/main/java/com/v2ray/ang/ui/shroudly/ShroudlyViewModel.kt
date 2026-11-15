package com.v2ray.ang.ui.shroudly

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.shroudly.ShroudlyAvailability
import com.v2ray.ang.shroudly.ShroudlyEngine
import com.v2ray.ang.shroudly.ShroudlyEvent
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShroudlyUiState(
    val mode: String = AppConfig.SHROUDLY_MODE_OFF,
    val strategy: String = "auto",
    val quicOff: Boolean = true,
    val isSupported: Boolean = true,
    val isRunning: Boolean = false,
    val bypassCount: Int = 0,
    val blockedCount: Int = 0,
    val lastError: String? = null,
)

class ShroudlyViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        ShroudlyUiState(
            mode = SettingsManager.getShroudlyMode(),
            strategy = SettingsManager.getShroudlyStrategy(),
            quicOff = SettingsManager.getShroudlyQuicOff(),
            isSupported = ShroudlyAvailability.isAvailable,
            isRunning = ShroudlyEngine.isRunning,
        )
    )
    val uiState: StateFlow<ShroudlyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ShroudlyEngine.events.collect { event ->
                when (event) {
                    is ShroudlyEvent.Ready -> _uiState.update { it.copy(isRunning = true) }
                    is ShroudlyEvent.Host -> {
                        when (event.kind) {
                            "bypass" -> _uiState.update { it.copy(bypassCount = it.bypassCount + 1) }
                            "blocked" -> _uiState.update { it.copy(blockedCount = it.blockedCount + 1) }
                        }
                    }
                    is ShroudlyEvent.Stats -> {
                        _uiState.update {
                            it.copy(
                                bypassCount = if (event.desync > 0) event.desync.toInt() else it.bypassCount,
                                blockedCount = if (event.blocked > 0) event.blocked.toInt() else it.blockedCount
                            )
                        }
                    }
                    is ShroudlyEvent.Error -> _uiState.update { it.copy(lastError = event.message) }
                    else -> {}
                }
            }
        }
    }

    fun setMode(mode: String) {
        SettingsManager.setShroudlyMode(mode)
        _uiState.update { it.copy(mode = mode) }
    }

    fun setStrategy(strategy: String) {
        SettingsManager.setShroudlyStrategy(strategy)
        _uiState.update { it.copy(strategy = strategy) }
    }

    fun setQuicOff(quicOff: Boolean) {
        SettingsManager.setShroudlyQuicOff(quicOff)
        _uiState.update { it.copy(quicOff = quicOff) }
    }
}
