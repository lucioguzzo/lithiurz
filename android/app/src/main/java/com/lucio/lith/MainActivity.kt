package com.lucio.lith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucio.lith.ui.LithNavHost
import com.lucio.lith.ui.chat.ChatViewModel
import com.lucio.lith.ui.theme.LithTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LithApp).container
        val shared = sharedText(intent)

        setContent {
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LithTheme(mode = settings.themeMode) {
                LithNavHost(viewModel = viewModel, sharedText = shared)
            }
        }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
}
