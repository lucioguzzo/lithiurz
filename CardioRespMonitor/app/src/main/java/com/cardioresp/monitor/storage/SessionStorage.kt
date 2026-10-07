package com.cardioresp.monitor.storage

import android.content.Context
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.RecordingSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout su disco: <app-specific external>/recordings/<yyyyMMdd_HHmmss>/{samples.csv, raw_events.csv, metadata.json}
 * La directory app-specific non richiede permessi ed è raggiungibile via `adb pull`.
 */
class SessionStorage(private val context: Context) {

    private val root: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings").apply { mkdirs() }

    fun createSession(config: AcquisitionConfig): RecordingSession {
        val now = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val dir = File(root, id).apply { mkdirs() }
        return RecordingSession(id, dir, now, config)
    }

    /** Sessione più recente con un samples.csv non vuoto. */
    fun latestSession(): RecordingSession? =
        root.listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstOrNull { File(it, RecordingSession.SAMPLES_FILE).length() > 0 }
            ?.let { RecordingSession(it.name, it, it.lastModified(), AcquisitionConfig()) }
}
