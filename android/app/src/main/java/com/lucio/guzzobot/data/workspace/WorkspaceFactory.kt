package com.lucio.guzzobot.data.workspace

import android.content.Context
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.WorkspaceConfig
import com.lucio.guzzobot.data.model.WorkspaceMode
import com.lucio.guzzobot.data.prefs.SecretStore
import kotlinx.serialization.json.Json

/** Area di lavoro, più repository e Actions quando si lavora su GitHub. */
data class WorkspaceBundle(
    val workspace: Workspace,
    val github: GitHubWorkspace?,
    val actions: GitHubActions?,
)

/** Costruisce l'area di lavoro dalle impostazioni: la usano la chat e i compiti pianificati. */
object WorkspaceFactory {

    fun create(
        context: Context,
        settings: AppSettings,
        secrets: SecretStore,
        json: Json,
    ): WorkspaceBundle? {
        val config = settings.workspace
        if (!config.isConfigured) return null
        return runCatching {
            when (config.mode) {
                WorkspaceMode.LOCAL -> WorkspaceBundle(
                    workspace = LocalWorkspace(
                        context = context,
                        treeUri = config.localTreeUri!!,
                        label = config.label,
                    ),
                    github = null,
                    actions = null,
                )

                WorkspaceMode.GITHUB -> {
                    val client = GitHubClient(
                        owner = config.owner!!,
                        repo = config.repo!!,
                        token = secrets.apiKey(WorkspaceConfig.GITHUB_SECRET),
                        json = json,
                    )
                    val workspace = GitHubWorkspace(client, config.branch)
                    WorkspaceBundle(workspace, workspace, GitHubActions(client))
                }

                WorkspaceMode.NONE -> null
            }
        }.getOrNull()
    }
}
