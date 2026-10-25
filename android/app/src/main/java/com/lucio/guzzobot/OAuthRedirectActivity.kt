package com.lucio.guzzobot

import android.app.Activity
import android.os.Bundle
import com.lucio.guzzobot.data.mcp.OAuthInbox

/**
 * Riceve il ritorno dal browser dopo l'autorizzazione di un connettore.
 *
 * Non mostra niente e non resta: deposita l'indirizzo nella cassetta e si
 * chiude subito, lasciando riemergere la schermata da cui si era partiti.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let(OAuthInbox::deliver)
        finish()
    }
}
