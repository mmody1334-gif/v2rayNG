package com.v2ray.ang.ui.shroudly

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.v2ray.ang.ui.base.BaseComponentActivity

class ShroudlyActivity : BaseComponentActivity() {

    private val viewModel: ShroudlyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        ShroudlyScreen(
            viewModel = viewModel,
            onBackClick = { finish() }
        )
    }
}
