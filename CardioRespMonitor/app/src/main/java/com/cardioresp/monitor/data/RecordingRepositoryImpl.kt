package com.cardioresp.monitor.data

import android.net.Uri
import com.cardioresp.monitor.domain.model.RecordingSession
import com.cardioresp.monitor.domain.repository.RecordingRepository
import com.cardioresp.monitor.storage.SafExporter
import com.cardioresp.monitor.storage.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingRepositoryImpl(
    private val storage: SessionStorage,
    private val exporter: SafExporter
) : RecordingRepository {

    override fun lastSession(): RecordingSession? = storage.latestSession()

    override suspend fun exportCsv(session: RecordingSession, destination: Uri) =
        withContext(Dispatchers.IO) { exporter.copyFile(session.samplesCsv, destination) }

    override suspend fun exportZip(session: RecordingSession, destination: Uri) =
        withContext(Dispatchers.IO) { exporter.zipSession(session, destination) }
}
