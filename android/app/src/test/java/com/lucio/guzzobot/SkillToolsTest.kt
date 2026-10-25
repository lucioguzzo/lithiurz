package com.lucio.guzzobot

import com.lucio.guzzobot.data.skills.Skill
import com.lucio.guzzobot.data.skills.SkillTools
import com.lucio.guzzobot.data.skills.Skills
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillToolsTest {

    private val skills = Skills.defaults

    @Test
    fun `lo strumento elenca solo gli identificativi che esistono`() {
        val definition = SkillTools.definition(skills)!!
        val parameters = definition["function"]!!.jsonObject["parameters"]!!.jsonObject
        val allowed = parameters["properties"]!!.jsonObject["name"]!!.jsonObject["enum"]!!
            .jsonArray.map { it.jsonPrimitive.content }
        assertEquals(skills.map { it.id }, allowed)
        assertEquals(
            listOf("name"),
            parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `senza competenze attive lo strumento non viene offerto`() {
        assertNull(SkillTools.definition(emptyList()))
        assertNull(SkillTools.definition(skills.map { it.copy(enabled = false) }))
    }

    @Test
    fun `una competenza spenta non finisce fra quelle caricabili`() {
        val halved = skills.mapIndexed { index, skill -> skill.copy(enabled = index % 2 == 0) }
        val allowed = SkillTools.definition(halved)!!["function"]!!.jsonObject["parameters"]!!
            .jsonObject["properties"]!!.jsonObject["name"]!!.jsonObject["enum"]!!
            .jsonArray.map { it.jsonPrimitive.content }
        assertEquals(halved.filter { it.enabled }.map { it.id }, allowed)
    }

    @Test
    fun `l'indice dice a cosa servono e quando non usarle`() {
        val text = SkillTools.describe(skills)
        // Ogni competenza compare con nome e descrizione.
        skills.forEach { assertTrue(it.id, text.contains(it.id) && text.contains(it.name)) }
        // E la regola contro l'abuso c'è.
        assertTrue(text, text.contains("al massimo una"))
        assertTrue(text, text.contains("nessuna"))
    }

    @Test
    fun `una competenza inventata viene riconosciuta come tale`() {
        assertTrue(SkillTools.unknown("non_esiste", skills))
        assertFalse(SkillTools.unknown(skills.first().id, skills))
        assertTrue(SkillTools.describe("non_esiste", skills).contains("sconosciuta"))
        assertTrue(SkillTools.describe(skills.first().id, skills).contains(skills.first().name))
    }

    @Test
    fun `l'indice resta corto anche con molte competenze`() {
        // La descrizione viaggia a ogni richiesta: non deve diventare un libro.
        val many = (1..40).map {
            Skill(id = "s$it", name = "Competenza $it", description = "Serve a fare $it", instructions = "x")
        }
        assertTrue("${SkillTools.describe(many).length}", SkillTools.describe(many).length < 3_000)
    }
}
