package com.lucio.guzzobot.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Le chiavi API restano solo su questo dispositivo, in SharedPreferences cifrate
 * con una chiave custodita dal Keystore hardware di Android. Non lasciano il
 * telefono se non nella chiamata diretta al provider scelto.
 */
class SecretStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(encrypted(context))

    private val revision = MutableStateFlow(0)

    /** Cambia ogni volta che una chiave viene salvata o rimossa. */
    val changes: StateFlow<Int> = revision

    fun apiKey(providerId: String): String = prefs.getString(key(providerId), "").orEmpty()

    fun hasKey(providerId: String): Boolean = apiKey(providerId).isNotBlank()

    fun setApiKey(providerId: String, value: String) {
        prefs.edit().apply {
            if (value.isBlank()) remove(key(providerId)) else putString(key(providerId), value.trim())
        }.apply()
        revision.value = revision.value + 1
    }

    private fun key(providerId: String) = "api_key_$providerId"

    companion object {
        private const val FILE = "guzzobot_secure_keys"

        /**
         * Se il Keystore non riesce più a decifrare il file — succede dopo
         * certi ripristini o aggiornamenti di sistema — l'app non deve
         * morire a ogni avvio: si butta il file illeggibile e si riparte
         * senza chiavi. Perderle è brutto; non poter più aprire l'app è peggio.
         */
        private fun encrypted(context: Context): SharedPreferences =
            runCatching { open(context) }.getOrElse {
                context.deleteSharedPreferences(FILE)
                open(context)
            }

        private fun open(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
