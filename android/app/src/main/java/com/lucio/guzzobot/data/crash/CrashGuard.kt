package com.lucio.guzzobot.data.crash

import android.os.Build
import com.lucio.guzzobot.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Annota il crash e poi lascia morire l'app come avrebbe fatto comunque.
 *
 * Il gestore precedente va richiamato: è lui che chiude il processo e mostra
 * il dialogo di sistema. Ingoiare l'errore lascerebbe un'app zombie.
 */
object CrashGuard {

    fun install(store: CrashStore) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                store.save(
                    CrashReport.compose(
                        error = error,
                        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        moment = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()),
                        thread = thread.name,
                    ),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
