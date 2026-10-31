package com.v2ray.ang.ui.connectioncheck

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.ConnectionCheckManager
import com.v2ray.ang.handler.ConnectionCheckResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ConnectionCheckUiState(
    val result: ConnectionCheckResult? = null,
    val failed: Boolean = false,
    val blockIpv6Leak: Boolean = true,
)

class ConnectionCheckViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(
        ConnectionCheckUiState(
            blockIpv6Leak = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLOCK_IPV6_LEAK, true)
        )
    )
    val state: StateFlow<ConnectionCheckUiState> = _state.asStateFlow()

    fun runCheck() {
        launchLoading {
            val result = withContext(Dispatchers.IO) {
                try {
                    ConnectionCheckManager.check()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "ConnectionCheck: check failed", e)
                    null
                }
            }
            _state.value = _state.value.copy(result = result, failed = result?.visible == null)
        }
    }

    fun setBlockIpv6Leak(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_BLOCK_IPV6_LEAK, enabled)
        // Takes effect when the VPN interface is rebuilt.
        SettingsChangeManager.makeRestartService()
        _state.value = _state.value.copy(blockIpv6Leak = enabled)
    }
}
