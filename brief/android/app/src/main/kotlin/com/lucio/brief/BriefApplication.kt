package com.lucio.brief

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/**
 * Intercetta gli errori non gestiti del lato Java e li mostra a schermo
 * invece di far sparire l'app con il messaggio generico del sistema.
 *
 * Il gestore viene installato in [attachBaseContext], che l'Android runtime
 * esegue **prima** dei ContentProvider di avvio (AdMob, androidx.startup) e
 * quindi prima di qualsiasi codice Dart: è l'unico punto da cui si possono
 * catturare i crash che avvengono durante l'inizializzazione.
 */
class BriefApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val report = buildReport(thread, error)
            Log.e(TAG, report)
            saveReport(report)
            if (!showReport(report)) {
                // Se non riusciamo ad aprire la schermata, lasciamo fare al
                // gestore di sistema: almeno il crash resta nei log.
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            exitProcess(1)
        }
    }

    private fun buildReport(thread: Thread, error: Throwable): String = buildString {
        appendLine("BRIEF ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        appendLine()
        append(Log.getStackTraceString(error))
    }

    /** Copia su file, così il rapporto resta disponibile anche dopo. */
    private fun saveReport(report: String) {
        try {
            File(getExternalFilesDir(null), "ultimo_errore.txt").writeText(report)
        } catch (ignored: Throwable) {
            Log.w(TAG, "Impossibile salvare il rapporto di errore", ignored)
        }
    }

    private fun showReport(report: String): Boolean = try {
        val intent = Intent(this, CrashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(CrashActivity.EXTRA_REPORT, report)
        startActivity(intent)
        true
    } catch (error: Throwable) {
        Log.w(TAG, "Impossibile mostrare il rapporto di errore", error)
        false
    }

    private companion object {
        const val TAG = "BRIEF"
    }
}
