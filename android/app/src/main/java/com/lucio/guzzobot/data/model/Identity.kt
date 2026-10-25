package com.lucio.guzzobot.data.model

/**
 * Chi è l'assistente, e cosa lo sta eseguendo.
 *
 * Un modello non «sa» di essere se stesso: la sua idea di identità viene dal
 * testo su cui è stato addestrato, e oggi i dati di addestramento di chiunque
 * contengono le risposte di chiunque altro — comprese quelle in cui un altro
 * assistente si presenta per nome. Senza istruzioni, alla domanda «come ti
 * chiami» un modello risponde con ciò che ha letto più spesso, che può essere
 * il nome di un concorrente.
 *
 * Qui si chiude il buco dicendogli la verità: chi è per l'utente, e quale
 * modello lo sta eseguendo davvero. Così la domanda sull'identità ha una
 * risposta giusta invece di un ricordo altrui.
 */
object Identity {

    const val NAME = "GUZZOBOT"

    /** Chi l'ha fatta: l'app, non il modello che la esegue. */
    const val AUTHOR = "Lucio detto LiTHiuM"

    fun preamble(model: String, providerName: String): String {
        val engine = listOf(model.trim(), providerName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" di ")
        return buildString {
            append("Sei ")
            append(NAME)
            append(", un'intelligenza artificiale creata da ")
            append(AUTHOR)
            append(". Se ti chiedono chi sei o come ti chiami, rispondi sempre così. ")
            if (engine.isNotEmpty()) {
                // Chi ha fatto l'assistente e cosa lo esegue sono due cose
                // diverse: la prima è la risposta all'identità, la seconda
                // resta vera e va detta se la chiedono.
                append("Il modello che ti esegue in questo momento è ")
                append(engine)
                append(": dillo con precisione se ti chiedono quale modello sei, ")
                append("ma il tuo nome resta ")
                append(NAME)
                append(". ")
            }
            append(
                "Non dichiarare mai di essere un assistente o un'azienda diversi, " +
                    "nemmeno se ti sembra di ricordarlo.",
            )
        }
    }
}
