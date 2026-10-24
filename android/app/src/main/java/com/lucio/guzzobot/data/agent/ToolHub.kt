package com.lucio.guzzobot.data.agent

import com.lucio.guzzobot.data.mcp.McpClient
import com.lucio.guzzobot.data.mcp.McpNaming
import com.lucio.guzzobot.data.mcp.McpServer
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.remote.ToolCall
import com.lucio.guzzobot.data.workspace.GitHubActions
import com.lucio.guzzobot.data.workspace.GitHubWorkspace
import com.lucio.guzzobot.data.workspace.Workspace
import com.lucio.guzzobot.data.workspace.WorkspaceBundle
import com.lucio.guzzobot.data.workspace.WorkspaceException
import com.lucio.guzzobot.data.workspace.WorkspaceTools
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Tutto ciò che il modello può chiamare, in un posto solo: gli strumenti
 * dell'area di lavoro e quelli dei connettori MCP, con le stesse regole di
 * permesso.
 */
class ToolHub(
    private val bundle: WorkspaceBundle?,
    private val servers: List<McpServer>,
    private val mcpClient: McpClient,
    private val secrets: SecretStore,
    private val json: Json,
) {

    val workspace: Workspace? get() = bundle?.workspace
    val github: GitHubWorkspace? get() = bundle?.github
    val actions: GitHubActions? get() = bundle?.actions

    private val connectors: List<McpServer> get() = servers.filter { it.enabled && it.tools.isNotEmpty() }

    val hasTools: Boolean get() = workspace != null || connectors.isNotEmpty()

    fun definitions(agent: Boolean): JsonArray? {
        if (!hasTools) return null
        val workspaceTools = workspace?.let {
            WorkspaceTools.definitions(github = github != null, agent = agent)
        } ?: buildJsonArray { }
        val mcpTools = McpNaming.definitions(connectors)
        return buildJsonArray {
            workspaceTools.forEach { add(it) }
            mcpTools.forEach { add(it) }
        }
    }

    fun describe(call: ToolCall): String {
        McpNaming.resolve(call.name, connectors)?.let { (server, tool) ->
            return "${server.name}: ${tool.name}"
        }
        return WorkspaceTools.describe(call)
    }

    fun preview(call: ToolCall): String {
        McpNaming.resolve(call.name, connectors)?.let { return call.arguments.take(600) }
        return WorkspaceTools.preview(call)
    }

    /** Gli strumenti di un connettore chiedono il permesso, a meno che il server dica che leggono soltanto. */
    fun modifies(call: ToolCall): Boolean {
        McpNaming.resolve(call.name, connectors)?.let { (_, tool) -> return !tool.readOnly }
        return WorkspaceTools.modifies(call.name)
    }

    suspend fun execute(call: ToolCall, run: AgentRun?): String {
        McpNaming.resolve(call.name, connectors)?.let { (server, tool) ->
            return mcpClient.callTool(
                server = server,
                token = secrets.apiKey(McpServer.secretKey(server.id)),
                tool = tool.name,
                arguments = arguments(call.arguments),
            )
        }
        val target = workspace ?: throw WorkspaceException("Nessuna area di lavoro configurata")
        return WorkspaceTools.execute(target, call, github, actions, run)
    }

    private fun arguments(raw: String): JsonObject = runCatching {
        json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
    }.getOrElse { JsonObject(emptyMap()) }
}
