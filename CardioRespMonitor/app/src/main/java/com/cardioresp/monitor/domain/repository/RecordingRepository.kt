package com.cardioresp.monitor.domain.repository

import android.net.Uri
import com.cardioresp.monitor.domain.model.RecordingSession

interface RecordingRepository {
    /** Ultima sessione completata (la più recente su disco), o null. */
    fun lastSession(): RecordingSession?

    /** Copia samples.csv nella destinazione scelta dall'utente via Storage Access Framework. */
    suspend fun exportCsv(session: RecordingSession, destination: Uri)

    /** Esporta l'intera sessione (samples + raw + metadata) come ZIP via SAF. */
    suspend fun exportZip(session: RecordingSession, destination: Uri)
}
