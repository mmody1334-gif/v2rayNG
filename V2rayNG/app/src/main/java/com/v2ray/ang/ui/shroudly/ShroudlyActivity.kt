package com.v2ray.ang.ui.shroudly

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.base.BaseComponentActivity

class ShroudlyActivity : BaseComponentActivity() {

    private val viewModel: ShroudlyViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        ShroudlyScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            // A change applies at once: a running service restarts with it (MSG_STATE_RESTART is
            // answered only by a running daemon, so nothing starts when it is stopped).
            onSettingChanged = { LauncherManager.restartService(this) }
        )
    }
}
