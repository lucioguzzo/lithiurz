package com.lucio.guzzobot.data.mcp

import java.security.MessageDigest
import java.util.Base64
import java.security.SecureRandom

/**
 * PKCE: il segreto che lega la richiesta di autorizzazione allo scambio del
 * codice. Senza, chi intercettasse il codice potrebbe spenderlo lui.
 */
object Pkce {

    /** Il segreto, da tenere finché il codice non è stato scambiato. */
    fun verifier(): String = encode(ByteArray(64).also { SecureRandom().nextBytes(it) })

    /** Quello che si manda nella richiesta: l'impronta del segreto, non il segreto. */
    fun challenge(verifier: String): String =
        encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** Lo stato con cui si riconosce la propria risposta fra tante. */
    fun state(): String = encode(ByteArray(24).also { SecureRandom().nextBytes(it) })

    /**
     * base64url senza riempimento, come vuole la specifica.
     *
     * Si usa quello di Java e non quello di Android perché questa logica deve
     * restare verificabile fuori dal telefono: android.util.Base64 in un test
     * non fa niente.
     */
    internal fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
