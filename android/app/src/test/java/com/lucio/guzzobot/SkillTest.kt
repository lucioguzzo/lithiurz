package com.lucio.guzzobot

import com.lucio.guzzobot.data.skills.Skill
import com.lucio.guzzobot.data.skills.SkillCatalog
import com.lucio.guzzobot.data.skills.SkillComposer
import com.lucio.guzzobot.data.skills.Skills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTest {

    private val mine = Skill(
        id = "mia_1",
        name = "La mia",
        description = "Cose mie",
        instructions = "Fai a modo mio.",
    )

    @Test
    fun `al primo avvio ci sono le skill dell'app`() {
        assertEquals(Skills.defaults, SkillCatalog.merge(emptyList()))
    }

    @Test
    fun `una skill nuova dell'app compare a chi ha gia' l'app`() {
        val stored = listOf(Skills.defaults.first())
        val merged = SkillCatalog.merge(stored)
        assertEquals(Skills.defaults.size, merged.size)
    }

    @Test
    fun `il testo modificato dall'utente non viene sovrascritto`() {
        val changed = Skills.defaults.first().copy(instructions = "A modo mio", enabled = false)
        val merged = SkillCatalog.merge(listOf(changed))
        val found = merged.first { it.id == changed.id }
        assertEquals("A modo mio", found.instructions)
        assertFalse(found.enabled)
        assertTrue(found.builtIn)
    }

    @Test
    fun `le skill scritte dall'utente restano, in coda`() {
        val merged = SkillCatalog.merge(Skills.defaults + mine)
        assertEquals(mine.id, merged.last().id)
        assertFalse(merged.last().builtIn)
    }

    @Test
    fun `una skill cancellata non torna`() {
        val victim = Skills.defaults.first().id
        val merged = SkillCatalog.merge(Skills.defaults, removed = setOf(victim))
        assertTrue(merged.none { it.id == victim })
        assertTrue(SkillCatalog.merge(emptyList(), removed = setOf(victim)).none { it.id == victim })
    }

    @Test
    fun `un id nuovo non collide con quelli che ci sono`() {
        val id = SkillCatalog.newId(listOf(mine))
        assertEquals("mia_2", id)
        assertTrue(SkillCatalog.newId(emptyList()) == "mia_1")
    }

    @Test
    fun `la skill si aggiunge al prompt dell'utente, non lo sostituisce`() {
        val composed = SkillComposer.compose("Rispondi in dialetto.", mine)
        assertTrue(composed.startsWith("Rispondi in dialetto."))
        assertTrue(composed.contains("Fai a modo mio."))
        assertTrue(composed.contains("La mia"))
    }

    @Test
    fun `senza skill il prompt resta quello che era`() {
        assertEquals("Solo io.", SkillComposer.compose("Solo io.", null))
        assertEquals("Fai a modo mio.", SkillComposer.compose("", mine).trim().lines().last())
    }

    @Test
    fun `l'elenco delle competenze nomina solo quelle attive`() {
        val skills = listOf(mine, mine.copy(id = "spenta", name = "Spenta", enabled = false))
        val text = SkillComposer.catalogue(skills).orEmpty()
        assertTrue(text.contains("La mia"))
        assertFalse(text.contains("Spenta"))
        assertNull(SkillComposer.catalogue(emptyList()))
        assertNull(SkillComposer.catalogue(listOf(mine.copy(enabled = false))))
    }

    @Test
    fun `le skill dell'app sono coerenti`() {
        val defaults = Skills.defaults
        assertEquals(defaults.size, defaults.map { it.id }.distinct().size)
        defaults.forEach { skill ->
            assertTrue(skill.id, skill.builtIn)
            assertTrue(skill.id, skill.name.isNotBlank())
            assertTrue(skill.id, skill.description.isNotBlank())
            // Istruzioni vere, non un segnaposto.
            assertTrue(skill.id, skill.instructions.length > 200)
        }
    }
}
