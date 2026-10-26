package com.lucio.guzzobot.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.ProjectEntity
import com.lucio.guzzobot.ui.components.GuzzoBotLogo

/**
 * Il pannello: in alto il nome e le voci principali — fra cui Agente Bot, da
 * cui si comandano le funzioni agentiche — poi le conversazioni fissate, i
 * progetti e le recenti.
 */
@Composable
fun ChatDrawer(
    conversations: List<ConversationEntity>,
    projects: List<ProjectEntity>,
    currentId: Long?,
    workspaceLabel: String,
    onNewChat: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
    onShare: (ConversationEntity) -> Unit,
    onTogglePin: (ConversationEntity) -> Unit,
    onAssignProject: (Long, Long?) -> Unit,
    onCreateProject: (String, Long?) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onOpenAgent: () -> Unit,
    onOpenCode: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenVoice: () -> Unit,
    activeSkill: String? = null,
    onOpenWorkspace: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var newProjectFor by remember { mutableStateOf<Pair<Boolean, Long?>?>(null) }
    val sections = remember(conversations, projects, query) {
        DrawerBuilder.build(conversations, projects, query)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            // Le voci fisse scorrono insieme alle chat: su un telefono piccolo,
            // o coi caratteri grandi, altrimenti schiaccerebbero l'elenco a
            // zero e spingerebbero Impostazioni fuori dallo schermo.
            item("menu") {
                Column {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuzzoBotLogo(size = 32.dp)
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    DrawerRow(Icons.Outlined.Add, stringResource(R.string.new_chat), onNewChat, accent = true)
                    DrawerRow(
                        icon = Icons.Outlined.AutoAwesome,
                        label = stringResource(R.string.agent_bot),
                        onClick = onOpenAgent,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.Code,
                        label = stringResource(R.string.code),
                        onClick = onOpenCode,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.GraphicEq,
                        label = stringResource(R.string.voice_conversation),
                        onClick = onOpenVoice,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.School,
                        label = stringResource(R.string.skills),
                        detail = activeSkill,
                        onClick = onOpenSkills,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.Inventory2,
                        label = stringResource(R.string.artifacts),
                        onClick = onOpenArtifacts,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.FolderOpen,
                        label = stringResource(R.string.workspace),
                        detail = workspaceLabel.ifBlank { stringResource(R.string.workspace_none) },
                        onClick = onOpenWorkspace,
                    )
                    DrawerRow(
                        icon = Icons.Outlined.Schedule,
                        label = stringResource(R.string.tasks),
                        onClick = onOpenTasks,
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_chats)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            if (sections.pinned.isNotEmpty()) {
                item("pinned-title") { SectionLabel(stringResource(R.string.pinned)) }
                items(sections.pinned.size, key = { "p-${sections.pinned[it].id}" }) { index ->
                    val conversation = sections.pinned[index]
                    ConversationRow(
                        conversation = conversation,
                        projects = projects,
                        selected = conversation.id == currentId,
                        onClick = { onOpen(conversation.id) },
                        onRename = { onRename(conversation) },
                        onDelete = { onDelete(conversation) },
                        onShare = { onShare(conversation) },
                        onTogglePin = { onTogglePin(conversation) },
                        onAssignProject = { onAssignProject(conversation.id, it) },
                        onNewProject = { newProjectFor = true to conversation.id },
                    )
                }
            }

            item("projects-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.projects), Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.project_new),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { newProjectFor = false to null }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            sections.projects.forEach { group ->
                item("project-${group.project.id}") {
                    ProjectRow(
                        name = group.project.name,
                        count = group.conversations.size,
                        expanded = expanded == group.project.id,
                        onClick = {
                            expanded = if (expanded == group.project.id) null else group.project.id
                        },
                        onDelete = { onDeleteProject(group.project.id) },
                    )
                }
                if (expanded == group.project.id) {
                    items(
                        group.conversations.size,
                        key = { "c-${group.conversations[it].id}" },
                    ) { index ->
                        val conversation = group.conversations[index]
                        ConversationRow(
                            conversation = conversation,
                            projects = projects,
                            selected = conversation.id == currentId,
                            indented = true,
                            onClick = { onOpen(conversation.id) },
                            onRename = { onRename(conversation) },
                            onDelete = { onDelete(conversation) },
                            onShare = { onShare(conversation) },
                            onTogglePin = { onTogglePin(conversation) },
                            onAssignProject = { onAssignProject(conversation.id, it) },
                            onNewProject = { newProjectFor = true to conversation.id },
                        )
                    }
                }
            }

            item("recents-title") { SectionLabel(stringResource(R.string.recents)) }
            if (sections.recents.isEmpty()) {
                item("recents-empty") {
                    Text(
                        text = stringResource(R.string.no_chats),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
            items(sections.recents.size, key = { "r-${sections.recents[it].id}" }) { index ->
                val conversation = sections.recents[index]
                ConversationRow(
                    conversation = conversation,
                    projects = projects,
                    selected = conversation.id == currentId,
                    onClick = { onOpen(conversation.id) },
                    onRename = { onRename(conversation) },
                    onDelete = { onDelete(conversation) },
                    onShare = { onShare(conversation) },
                    onTogglePin = { onTogglePin(conversation) },
                    onAssignProject = { onAssignProject(conversation.id, it) },
                    onNewProject = { newProjectFor = true to conversation.id },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DrawerRow(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.settings),
            onClick = onOpenSettings,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    newProjectFor?.let { (_, conversationId) ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newProjectFor = null },
            title = { Text(stringResource(R.string.project_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.project_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateProject(name, conversationId)
                        newProjectFor = null
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { newProjectFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (accent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    name: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { menu = true }
                    .padding(6.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    projects: List<ProjectEntity>,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onAssignProject: (Long?) -> Unit,
    onNewProject: () -> Unit,
    indented: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 28.dp else 12.dp, end = 12.dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conversation.pinned) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(13.dp)
                    .padding(end = 0.dp),
            )
        }
        Text(
            text = conversation.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (conversation.pinned) 8.dp else 0.dp),
        )
        Box {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { menu = true }
                    .padding(8.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (conversation.pinned) R.string.unpin else R.string.pin,
                            ),
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = {
                        menu = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_project)) },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    onClick = {
                        menu = false
                        moving = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = {
                        menu = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
            DropdownMenu(expanded = moving, onDismissRequest = { moving = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_none)) },
                    onClick = {
                        moving = false
                        onAssignProject(null)
                    },
                )
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            moving = false
                            onAssignProject(project.id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_new)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = {
                        moving = false
                        onNewProject()
                    },
                )
            }
        }
    }
}
