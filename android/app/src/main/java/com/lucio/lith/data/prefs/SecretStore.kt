package com.lucio.lith.data.prefs

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
class SecretStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "lith_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

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
}
