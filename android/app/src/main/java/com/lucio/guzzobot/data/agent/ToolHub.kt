package com.lucio.guzzobot.data.agent

import com.lucio.guzzobot.data.mcp.McpClient
import com.lucio.guzzobot.data.mcp.McpNaming
import com.lucio.guzzobot.data.mcp.McpServer
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.skills.Skill
import com.lucio.guzzobot.data.skills.SkillCatalog
import com.lucio.guzzobot.data.skills.SkillTools
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
    /** Se configurato un modello per le immagini, il modello può crearne. */
    private val images: ImageTools.Maker? = null,
    /** Le competenze che il modello può caricarsi da sé. */
    private val skills: List<Skill> = emptyList(),
    private val skillLoader: SkillTools.Loader? = null,
) {

    val workspace: Workspace? get() = bundle?.workspace
    val github: GitHubWorkspace? get() = bundle?.github
    val actions: GitHubActions? get() = bundle?.actions

    private val connectors: List<McpServer> get() = servers.filter { it.enabled && it.tools.isNotEmpty() }

    val hasTools: Boolean
        get() = workspace != null || connectors.isNotEmpty() || images != null ||
            skillTool != null

    private val skillTool: JsonObject?
        get() = if (skillLoader == null) null else SkillTools.definition(skills)

    fun definitions(agent: Boolean): JsonArray? {
        if (!hasTools) return null
        val workspaceTools = workspace?.let {
            WorkspaceTools.definitions(github = github != null, agent = agent)
        } ?: buildJsonArray { }
        val mcpTools = McpNaming.definitions(connectors)
        return buildJsonArray {
            workspaceTools.forEach { add(it) }
            mcpTools.forEach { add(it) }
            if (images != null) add(ImageTools.definition())
            skillTool?.let { add(it) }
        }
    }

    fun describe(call: ToolCall): String {
        if (call.name == ImageTools.CREATE) return ImageTools.describe(prompt(call))
        if (call.name == SkillTools.LOAD) return SkillTools.describe(argument(call, "name"), skills)
        McpNaming.resolve(call.name, connectors)?.let { (server, tool) ->
            return "${server.name}: ${tool.name}"
        }
        return WorkspaceTools.describe(call)
    }

    fun preview(call: ToolCall): String {
        if (call.name == ImageTools.CREATE) return prompt(call)
        if (call.name == SkillTools.LOAD) return argument(call, "name")
        McpNaming.resolve(call.name, connectors)?.let { return call.arguments.take(600) }
        return WorkspaceTools.preview(call)
    }

    /** Gli strumenti di un connettore chiedono il permesso, a meno che il server dica che leggono soltanto. */
    fun modifies(call: ToolCall): Boolean {
        // Creare un'immagine non tocca i file dell'utente né il repository:
        // non passa dalla conferma. Quanto può costare lo decide il tetto di
        // sicurezza dell'agente, che conta i passi.
        // Caricare un modo di lavorare non cambia niente di nessuno.
        if (call.name == SkillTools.LOAD) return false
        if (call.name == ImageTools.CREATE) return false
        McpNaming.resolve(call.name, connectors)?.let { (_, tool) -> return !tool.readOnly }
        return WorkspaceTools.modifies(call.name)
    }

    suspend fun execute(call: ToolCall, run: AgentRun?): String {
        if (call.name == SkillTools.LOAD) {
            val loader = skillLoader ?: throw WorkspaceException("Nessuna competenza disponibile")
            val wanted = argument(call, "name")
            return loader.load(wanted) ?: throw WorkspaceException(
                "La competenza «$wanted» non esiste. Disponibili: " +
                    SkillCatalog.active(skills).joinToString { it.id },
            )
        }
        if (call.name == ImageTools.CREATE) {
            val maker = images ?: throw WorkspaceException("Nessun modello per le immagini")
            val wanted = prompt(call)
            if (wanted.isBlank()) throw WorkspaceException("Serve la descrizione dell'immagine")
            return maker.create(wanted)
        }
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

    private fun prompt(call: ToolCall): String = argument(call, "prompt")

    private fun argument(call: ToolCall, key: String): String =
        arguments(call.arguments)[key]?.let { value ->
            (value as? kotlinx.serialization.json.JsonPrimitive)?.content
        }.orEmpty().trim()

    private fun arguments(raw: String): JsonObject = runCatching {
        json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
    }.getOrElse { JsonObject(emptyMap()) }
}
