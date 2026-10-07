package com.cardioresp.monitor.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cardioresp.monitor.presentation.ui.MainScreen
import com.cardioresp.monitor.presentation.ui.theme.CardioRespTheme
import com.cardioresp.monitor.service.AcquisitionService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Durante la registrazione: schermo acceso + Sustained Performance Mode (dove supportato)
        // per evitare throttling termico di CPU che allungherebbe i tempi di drain.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.map { it.isRecording }.distinctUntilChanged().collect { rec ->
                    window.setSustainedPerformanceModeIfSupported(rec)
                    if (rec) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            CardioRespTheme {
                MainScreen(
                    viewModel = viewModel,
                    notificationPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
                    onStart = { AcquisitionService.start(this, viewModel.currentConfig()) },
                    onStop = { AcquisitionService.stop(this) }
                )
            }
        }
    }

    private fun android.view.Window.setSustainedPerformanceModeIfSupported(enable: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isSustainedPerformanceModeSupported) setSustainedPerformanceMode(enable)
    }
}
