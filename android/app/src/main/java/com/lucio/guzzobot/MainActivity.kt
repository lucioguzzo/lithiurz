package com.lucio.guzzobot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucio.guzzobot.ui.GuzzoBotNavHost
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.crash.CrashDialog
import com.lucio.guzzobot.ui.onboarding.OnboardingScreen
import com.lucio.guzzobot.ui.splash.SplashScreen
import com.lucio.guzzobot.ui.theme.GuzzoBotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GuzzoBotApp
        val container = app.container
        val shared = sharedText(intent)
        val sharedFile = sharedStream(intent)

        setContent {
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val onboardingNeeded by viewModel.onboardingNeeded.collectAsStateWithLifecycle()
            // rememberSaveable: una rotazione non fa ricomparire lo splash.
            var splashVisible by rememberSaveable { mutableStateOf(true) }
            var openFree by rememberSaveable { mutableStateOf(false) }
            var crash by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                crash = withContext(Dispatchers.IO) { runCatching { app.crashes.read() }.getOrNull() }
            }
            GuzzoBotTheme(mode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GuzzoBotNavHost(
                        viewModel = viewModel,
                        sharedText = shared,
                        sharedFile = sharedFile,
                        openFree = openFree,
                    )
                    // Solo quando le impostazioni sono state lette: finché è
                    // null non si sa, e indovinare farebbe lampeggiare il
                    // primo avvio a chi l'ha già fatto.
                    AnimatedVisibility(
                        visible = onboardingNeeded == true,
                        exit = fadeOut(tween(300)),
                    ) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onOpenFree = {
                                openFree = true
                                viewModel.completeOnboarding()
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = splashVisible,
                        exit = fadeOut(tween(420)),
                    ) {
                        SplashScreen(onFinished = { splashVisible = false })
                    }
                    val report = crash
                    if (report != null && !splashVisible) {
                        CrashDialog(report = report, onDone = {
                            crash = null
                            app.crashes.clear()
                        })
                    }
                }
            }
        }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    /** Un file condiviso da un'altra app: immagine, documento o audio. */
    private fun sharedStream(intent: Intent?): Pair<Uri, String>? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (type == "text/plain") return null
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return uri to type
    }
}
