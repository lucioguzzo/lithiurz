package com.lucio.guzzobot.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lucio.guzzobot.ui.chat.ChatScreen
import com.lucio.guzzobot.data.model.AttachmentKind
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.settings.ProviderScreen
import com.lucio.guzzobot.ui.settings.SettingsScreen
import com.lucio.guzzobot.ui.settings.AgentScreen
import com.lucio.guzzobot.ui.settings.ArtifactsScreen
import com.lucio.guzzobot.ui.settings.CodeScreen
import com.lucio.guzzobot.ui.settings.TasksScreen
import com.lucio.guzzobot.ui.settings.WorkspaceScreen

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROVIDER = "provider"
    const val WORKSPACE = "workspace"
    const val TASKS = "tasks"
    const val AGENT = "agent"
    const val CODE = "code"
    const val ARTIFACTS = "artifacts"
}

@Composable
fun GuzzoBotNavHost(
    viewModel: ChatViewModel,
    sharedText: String?,
    sharedFile: Pair<Uri, String>? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) viewModel.setInput(sharedText)
    }

    // Un file arrivato dal menu "Condividi" di un'altra app.
    LaunchedEffect(sharedFile) {
        val (uri, mime) = sharedFile ?: return@LaunchedEffect
        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            else -> AttachmentKind.DOCUMENT
        }
        viewModel.attach(uri, kind)
    }

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAgent = { navController.navigate(Routes.AGENT) },
                onOpenWorkspace = { navController.navigate(Routes.WORKSPACE) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenCode = { navController.navigate(Routes.CODE) },
                onOpenArtifacts = { navController.navigate(Routes.ARTIFACTS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenProvider = { providerId ->
                    navController.navigate("${Routes.PROVIDER}/$providerId")
                },
                onOpenWorkspace = { navController.navigate(Routes.WORKSPACE) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
            )
        }
        composable(Routes.CODE) {
            CodeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenWorkspace = { navController.navigate(Routes.WORKSPACE) },
                onAgentStarted = { navController.popBackStack(Routes.CHAT, inclusive = false) },
            )
        }
        composable(Routes.ARTIFACTS) {
            ArtifactsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.AGENT) {
            AgentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenWorkspace = { navController.navigate(Routes.WORKSPACE) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onStarted = { navController.popBackStack(Routes.CHAT, inclusive = false) },
            )
        }
        composable(Routes.TASKS) {
            TasksScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.WORKSPACE) {
            WorkspaceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
