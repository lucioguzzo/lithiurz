package com.lucio.guzzobot.data.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Il punto in cui il browser riconsegna il codice.
 *
 * Il ritorno dall'autorizzazione arriva come un intent a un'attività che non è
 * quella principale, e che non ha accesso al ViewModel. Questa cassetta vive
 * quanto il processo e fa solo da passaggio: l'attività ci deposita l'indirizzo
 * e chiude, chi aspetta lo raccoglie.
 */
object OAuthInbox {

    private val _received = MutableStateFlow<String?>(null)
    val received: StateFlow<String?> = _received

    fun deliver(uri: String) {
        _received.value = uri
    }

    /** Preso in consegna: si svuota, così un cambio di configurazione non lo rilegge. */
    fun consume(): String? = _received.value.also { _received.value = null }
}
