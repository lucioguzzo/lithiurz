package com.lucio.lith.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lucio.lith.ui.chat.ChatScreen
import com.lucio.lith.ui.chat.ChatViewModel
import com.lucio.lith.ui.settings.ProviderScreen
import com.lucio.lith.ui.settings.SettingsScreen

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROVIDER = "provider"
}

@Composable
fun LithNavHost(viewModel: ChatViewModel, sharedText: String?) {
    val navController = rememberNavController()

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) viewModel.setInput(sharedText)
    }

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenProvider = { providerId ->
                    navController.navigate("${Routes.PROVIDER}/$providerId")
                },
            )
        }
        composable(
            route = "${Routes.PROVIDER}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ProviderScreen(
                viewModel = viewModel,
                providerId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
