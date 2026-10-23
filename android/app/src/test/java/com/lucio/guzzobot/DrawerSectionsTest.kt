package com.lucio.guzzobot

import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.ProjectEntity
import com.lucio.guzzobot.ui.chat.DrawerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSectionsTest {

    private fun chat(
        id: Long,
        title: String,
        pinned: Boolean = false,
        projectId: Long? = null,
    ) = ConversationEntity(
        id = id,
        title = title,
        providerId = "deepseek",
        model = "deepseek-flash",
        createdAt = id,
        updatedAt = id,
        pinned = pinned,
        projectId = projectId,
    )

    private val studio = ProjectEntity(id = 1, name = "Studio legale", createdAt = 0)
    private val musica = ProjectEntity(id = 2, name = "Musica", createdAt = 0)

    private val chats = listOf(
        chat(1, "Contratto di affitto", projectId = 1),
        chat(2, "Mixaggio del brano", pinned = true, projectId = 2),
        chat(3, "Lista della spesa"),
        chat(4, "Contratto di lavoro", pinned = true),
        chat(5, "Chat orfana", projectId = 99),
    )

    @Test
    fun `le fissate stanno in cima e non si ripetono altrove`() {
        val sections = DrawerBuilder.build(chats, listOf(studio, musica))
        assertEquals(listOf(2L, 4L), sections.pinned.map { it.id })
        assertTrue(sections.recents.none { it.pinned })
        assertTrue(sections.projects.flatMap { it.conversations }.none { it.pinned })
    }

    @Test
    fun `ogni progetto raccoglie le sue conversazioni`() {
        val sections = DrawerBuilder.build(chats, listOf(studio, musica))
        val byName = sections.projects.associateBy { it.project.name }
        assertEquals(listOf(1L), byName.getValue("Studio legale").conversations.map { it.id })
        // La 2 è fissata, quindi qui non compare.
        assertTrue(byName.getValue("Musica").conversations.isEmpty())
    }

    @Test
    fun `una conversazione di un progetto cancellato torna fra le recenti`() {
        val sections = DrawerBuilder.build(chats, listOf(studio, musica))
        assertEquals(listOf(3L, 5L), sections.recents.map { it.id })
    }

    @Test
    fun `la ricerca filtra dentro tutte le sezioni`() {
        val sections = DrawerBuilder.build(chats, listOf(studio, musica), query = "contratto")
        assertEquals(listOf(4L), sections.pinned.map { it.id })
        assertEquals(listOf(1L), sections.projects.first().conversations.map { it.id })
        assertTrue(sections.recents.isEmpty())
    }

    @Test
    fun `senza conversazioni il pannello si dichiara vuoto`() {
        assertTrue(DrawerBuilder.build(emptyList(), listOf(studio)).isEmpty)
    }
}
