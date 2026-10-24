package com.lucio.guzzobot.data.skills

/**
 * Le skill salvate e quelle fornite con l'app si riconciliano come i provider:
 * le modifiche dell'utente restano, le skill nuove di un aggiornamento
 * compaiono, e una skill cancellata non torna da sola.
 */
object SkillCatalog {

    fun merge(
        stored: List<Skill>,
        defaults: List<Skill> = Skills.defaults,
        removed: Set<String> = emptySet(),
    ): List<Skill> {
        if (stored.isEmpty()) return defaults.filterNot { it.id in removed }
        val byId = stored.associateBy { it.id }
        val canonical = defaults.mapNotNull { default ->
            if (default.id in removed) return@mapNotNull null
            // Di una skill predefinita l'utente può aver cambiato il testo:
            // quello è suo e resta. Resta anche se l'ha disattivata.
            byId[default.id]?.copy(builtIn = true) ?: default
        }
        val custom = stored.filter { saved -> defaults.none { it.id == saved.id } }
        return canonical + custom
    }

    fun active(skills: List<Skill>): List<Skill> = skills.filter { it.enabled }

    fun byCategory(skills: List<Skill>): Map<SkillCategory, List<Skill>> =
        skills.groupBy { it.category }

    fun find(skills: List<Skill>, id: String?): Skill? =
        id?.let { wanted -> skills.firstOrNull { it.id == wanted } }

    /** Un id per una skill scritta dall'utente, che non collida con le altre. */
    fun newId(skills: List<Skill>): String {
        var index = 1
        while (skills.any { it.id == "mia_$index" }) index++
        return "mia_$index"
    }
}

/**
 * Come una skill entra nella conversazione.
 *
 * Il prompt della skill non sostituisce quello dell'utente: lo precede. Chi ha
 * scritto «rispondi sempre in dialetto» nelle impostazioni non si aspetta che
 * una skill glielo cancelli; e una skill non deve poter dire al modello di
 * ignorare le istruzioni di chi usa l'app.
 */
object SkillComposer {

    private const val HEADER = "Competenza attiva"

    fun compose(basePrompt: String, skill: Skill?): String {
        val base = basePrompt.trim()
        if (skill == null) return base
        val block = buildString {
            append("## ")
            append(HEADER)
            append(": ")
            append(skill.name.trim())
            append("\n\n")
            append(skill.instructions.trim())
        }
        return if (base.isEmpty()) block else base + "\n\n" + block
    }

    /**
     * L'elenco delle competenze disponibili, da mettere nel prompt quando
     * nessuna è applicata: il modello non le carica da sé — non ha uno
     * strumento per farlo — ma può dire all'utente che esistono.
     */
    fun catalogue(skills: List<Skill>, limit: Int = 12): String? {
        val usable = SkillCatalog.active(skills).take(limit)
        if (usable.isEmpty()) return null
        return buildString {
            append("Competenze che l'utente può attivare dal menù, se servono: ")
            append(usable.joinToString("; ") { "${it.name} (${it.description})" })
            append(". Non puoi attivarle tu: se una servirebbe, suggeriscila.")
        }
    }
}
