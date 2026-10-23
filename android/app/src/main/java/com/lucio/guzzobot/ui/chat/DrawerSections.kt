package com.lucio.guzzobot.ui.chat

import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.ProjectEntity

data class ProjectGroup(
    val project: ProjectEntity,
    val conversations: List<ConversationEntity>,
)

data class DrawerSections(
    val pinned: List<ConversationEntity>,
    val projects: List<ProjectGroup>,
    val recents: List<ConversationEntity>,
) {
    val isEmpty: Boolean
        get() = pinned.isEmpty() && recents.isEmpty() && projects.all { it.conversations.isEmpty() }
}

/**
 * Come si divide il pannello: le conversazioni fissate in cima, poi i progetti
 * con le loro, infine le recenti. La ricerca filtra dentro ogni sezione.
 */
object DrawerBuilder {

    fun build(
        conversations: List<ConversationEntity>,
        projects: List<ProjectEntity>,
        query: String = "",
    ): DrawerSections {
        val needle = query.trim()
        val matching = if (needle.isEmpty()) {
            conversations
        } else {
            conversations.filter { it.title.contains(needle, ignoreCase = true) }
        }
        val pinned = matching.filter { it.pinned }
        val loose = matching.filterNot { it.pinned }
        val groups = projects.map { project ->
            ProjectGroup(project, loose.filter { it.projectId == project.id })
        }
        val known = projects.map { it.id }.toSet()
        val recents = loose.filter { it.projectId == null || it.projectId !in known }
        return DrawerSections(pinned = pinned, projects = groups, recents = recents)
    }
}
