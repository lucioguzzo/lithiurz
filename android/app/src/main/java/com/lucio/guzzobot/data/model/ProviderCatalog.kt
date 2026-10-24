package com.lucio.guzzobot.data.model

/**
 * I provider salvati sul telefono non sono l'elenco dell'app: chi ha installato
 * GUZZOBOT mesi fa ha in memoria i provider di allora, e un aggiornamento che
 * ne aggiunge due nuovi non li farebbe vedere a nessuno.
 *
 * Qui i due elenchi si riconciliano: le scelte dell'utente restano (i modelli
 * che ha scritto, l'indirizzo che ha corretto, i provider che ha aggiunto o
 * cancellato), i metadati che spettano all'app si aggiornano, e i predefiniti
 * nuovi entrano al loro posto.
 */
object ProviderCatalog {

    fun merge(
        stored: List<Provider>,
        defaults: List<Provider> = Providers.defaults,
        removed: Set<String> = emptySet(),
    ): List<Provider> {
        if (stored.isEmpty()) return defaults.filterNot { it.id in removed }
        val byId = stored.associateBy { it.id }
        // Prima i predefiniti, nell'ordine dell'app, poi quelli aggiunti a mano.
        val canonical = defaults.mapNotNull { default ->
            if (default.id in removed) return@mapNotNull null
            val saved = byId[default.id] ?: return@mapNotNull default
            saved.copy(
                // Nome, pagina della chiave e piano gratuito li mantiene l'app:
                // sono le cose che cambiano quando cambia il provider, non l'utente.
                name = if (saved.builtIn) default.name else saved.name,
                keyUrl = default.keyUrl ?: saved.keyUrl,
                freeTier = default.freeTier,
                builtIn = true,
            )
        }
        val custom = stored.filter { saved -> defaults.none { it.id == saved.id } }
        return canonical + custom
    }

    /** I provider con un piano gratuito, nell'ordine in cui conviene provarli. */
    fun free(providers: List<Provider>): List<Provider> = providers.filter { it.freeTier != null }

    /** Vero se il modello è coperto dal piano gratuito del suo provider. */
    fun isFree(providers: List<Provider>, providerId: String, model: String): Boolean =
        providers.firstOrNull { it.id == providerId }?.freeTier?.covers(model) == true
}
