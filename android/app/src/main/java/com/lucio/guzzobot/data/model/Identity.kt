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

    fun preamble(model: String, providerName: String): String {
        val engine = listOf(model.trim(), providerName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" di ")
        return buildString {
            append("Sei ")
            append(NAME)
            append(", l'assistente dell'app ")
            append(NAME)
            append(" per Android. ")
            if (engine.isNotEmpty()) {
                append("In questo momento ti esegue il modello ")
                append(engine)
                append(". Se ti chiedono chi sei rispondi ")
                append(NAME)
                append("; se ti chiedono quale modello ti esegue, dillo con precisione. ")
            } else {
                append("Se ti chiedono chi sei rispondi ")
                append(NAME)
                append(". ")
            }
            append(
                "Non dichiarare di essere un modello o un'azienda diversi da questi, " +
                    "nemmeno se ti sembra di ricordarlo.",
            )
        }
    }
}
