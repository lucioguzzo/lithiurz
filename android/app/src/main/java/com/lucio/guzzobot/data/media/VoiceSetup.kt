package com.lucio.guzzobot.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Portare l'utente dove si installano voci migliori.
 *
 * La voce di serie di molti telefoni è sgradevole, e quella buona c'è quasi
 * sempre: va solo scaricata. Il problema è che non esiste *un* posto dove
 * farlo — ogni produttore mette le impostazioni altrove, e l'azione per
 * aprire quelle di sintesi non fa parte dell'API pubblica.
 *
 * Per questo qui non c'è un link ma una scala: si prova la strada migliore,
 * e se il telefono non la conosce si scende alla successiva. L'ultima
 * funziona ovunque ci sia un browser.
 */
object VoiceSetup {

    /** Il motore di Google, quello con le voci neurali. */
    const val GOOGLE_TTS = "com.google.android.tts"

    /** L'azione ufficiale: apre l'installatore delle voci del motore attivo. */
    const val INSTALL_TTS_DATA = "android.speech.tts.engine.INSTALL_TTS_DATA"

    /** Dove si scelgono motore e voci. Non è API pubblica: può non esserci. */
    const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

    /** Un tentativo: o un'azione di sistema, o un indirizzo da aprire. */
    sealed interface Step {
        data class Action(val action: String) : Step
        data class Link(val uri: String) : Step
    }

    /** Le strade per scaricare voci nuove, dalla più diretta alla più generica. */
    fun installVoices(): List<Step> = listOf(
        Step.Action(INSTALL_TTS_DATA),
        Step.Action(TTS_SETTINGS),
        Step.Link(market(GOOGLE_TTS)),
        Step.Link(web(GOOGLE_TTS)),
    )

    /** Le strade per aprire le impostazioni di sintesi vocale di Android. */
    fun openSettings(): List<Step> = listOf(
        Step.Action(TTS_SETTINGS),
        Step.Action(INSTALL_TTS_DATA),
        Step.Link(web(GOOGLE_TTS)),
    )

    /** Le strade per arrivare al motore di Google. */
    fun googleEngine(): List<Step> = listOf(Step.Link(market(GOOGLE_TTS)), Step.Link(web(GOOGLE_TTS)))

    /** L'app del Play Store, quando c'è: apre la scheda senza passare dal browser. */
    internal fun market(packageName: String) = "market://details?id=$packageName"

    /** Il browser: funziona anche dove il Play Store non è installato. */
    internal fun web(packageName: String) =
        "https://play.google.com/store/apps/details?id=$packageName"

    /**
     * Apre la prima strada che il telefono sa gestire.
     *
     * Restituisce falso se nessuna funziona: va detto, perché un pulsante che
     * non fa niente è peggio di un pulsante che non c'è.
     */
    fun launch(context: Context, steps: List<Step>): Boolean {
        steps.forEach { step ->
            val intent = when (step) {
                is Step.Action -> Intent(step.action)
                is Step.Link -> Intent(Intent.ACTION_VIEW, Uri.parse(step.uri))
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) return@forEach
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
