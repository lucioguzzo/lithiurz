package com.cardioresp.monitor.data

import android.os.SystemClock
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.RecordingSession
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import com.cardioresp.monitor.domain.model.SensorKind
import com.cardioresp.monitor.domain.model.SensorSample
import com.cardioresp.monitor.future_algorithms.ProcessingContext
import com.cardioresp.monitor.future_algorithms.SignalProcessingPipeline
import com.cardioresp.monitor.sensor.ChartBuffer
import com.cardioresp.monitor.sensor.SampleAligner
import com.cardioresp.monitor.sensor.SensorDataSource
import com.cardioresp.monitor.sensor.SensorRingBuffer
import com.cardioresp.monitor.sensor.StreamStatsCalculator
import com.cardioresp.monitor.sensor.TimeBaseCalibrator
import com.cardioresp.monitor.storage.CsvRecordingWriter
import com.cardioresp.monitor.storage.RawEventWriter
import com.cardioresp.monitor.storage.SessionMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Una sessione di registrazione: collega tutti i pezzi della pipeline.
 *
 *   [thread SensorAcquisition]          [thread AcquisitionPipeline]                    [thread SignalProcessing]
 *   onSensorChanged ──offer──► RingBuffer ──drain──► stats / chart / raw.csv
 *                                                   └► SampleAligner ──► samples.csv
 *                                                                     └─trySend─► Channel ──► SignalProcessor(s)
 *   [thread UI] ◄── snapshot @30 FPS ── ChartBuffer        diagnostiche @2 Hz ──► StateFlow
 *
 * Tutto lo stato mutabile qui dentro è toccato SOLO dal thread AcquisitionPipeline ([dispatcher]),
 * quindi niente lock: l'unico punto di concorrenza è il ring buffer SPSC.
 */
internal class AcquisitionSession(
    val session: RecordingSession,
    private val config: AcquisitionConfig,
    private val sensorSource: SensorDataSource,
    private val processing: SignalProcessingPipeline,
    private val accChart: ChartBuffer,
    private val gyroChart: ChartBuffer,
    private val dispatcher: CoroutineDispatcher,
    private val publishDiagnostics: (SamplingDiagnostics) -> Unit
) : SensorRingBuffer.Consumer {

    private val ring = SensorRingBuffer(RING_CAPACITY)
    private val calibrator = TimeBaseCalibrator()
    private val accStats = StreamStatsCalculator(SensorKind.ACCELEROMETER)
    private val gyroStats = StreamStatsCalculator(SensorKind.GYROSCOPE)

    private lateinit var csv: CsvRecordingWriter
    private var raw: RawEventWriter? = null
    private lateinit var aligner: SampleAligner
    private lateinit var sensors: SensorDataSource.StartResult

    private var timeBase: TimeBaseCalibrator.TimeBase? = null
    private var offsetStart: TimeBaseCalibrator.OffsetMeasurement? = null
    private var combinedCount = 0L

    @Volatile private var running = true
    private var loopJob: Job? = null

    fun start(scope: CoroutineScope) {
        val resolved = sensorSource.resolveSensors(config.useUncalibratedSensors)
        // Verifica PRIMA di aprire file o avviare thread: un errore qui non lascia risorse appese.
        requireNotNull(resolved.accelerometer) { "Accelerometro non disponibile su questo dispositivo" }
        csv = CsvRecordingWriter(session.samplesCsv)
        raw = if (config.writeRawEvents) RawEventWriter(session.rawEventsCsv) else null
        aligner = SampleAligner(gyroAvailable = resolved.gyroscope != null, emit = ::onCombined)
        accChart.clear()
        gyroChart.clear()
        processing.start(ProcessingContext(config.samplingRate.nominalHz, session.id))

        // Il consumatore parte PRIMA della registrazione dei listener: nessun evento resta in attesa.
        loopJob = scope.launch(dispatcher) { consumerLoop() }
        sensors = sensorSource.start(config, ring)
    }

    private suspend fun consumerLoop() {
        var lastPublish = 0L
        var lastFlush = SystemClock.elapsedRealtime()
        while (running) {
            ring.drain(this)
            val now = SystemClock.elapsedRealtime()
            if (now - lastPublish >= DIAGNOSTICS_PERIOD_MS) {
                publishDiagnostics(buildDiagnostics())
                lastPublish = now
            }
            if (now - lastFlush >= FLUSH_PERIOD_MS) {
                csv.flush(); raw?.flush()
                lastFlush = now
            }
            // Polling: a 1 kHz complessivi sono ~4 eventi per giro. La latenza qui NON influisce
            // sui timestamp (sono hardware) ed è assorbita dal ring buffer.
            delay(POLL_PERIOD_MS)
        }
        while (ring.drain(this) > 0) { /* drain finale */ }
    }

    /** Chiamato dal ring buffer, sul thread AcquisitionPipeline. */
    override fun onEvent(sensor: Int, tNs: Long, x: Float, y: Float, z: Float) {
        if (timeBase == null) {
            val base = calibrator.detect(tNs)
            val offset = calibrator.measureOffset(base)
            timeBase = base
            offsetStart = offset
            aligner.unixOffsetNs = offset.offsetNs
        }
        val unixMs = Math.floorDiv(tNs + aligner.unixOffsetNs, 1_000_000L)
        if (sensor == SensorKind.ACCELEROMETER.code) {
            accStats.add(tNs)
            accChart.add(tNs, x, y, z)
            raw?.write(SensorKind.ACCELEROMETER, tNs, unixMs, x, y, z)
            aligner.onAccelerometer(tNs, x, y, z)
        } else {
            gyroStats.add(tNs)
            gyroChart.add(tNs, x, y, z)
            raw?.write(SensorKind.GYROSCOPE, tNs, unixMs, x, y, z)
            aligner.onGyroscope(tNs, x, y, z)
        }
    }

    private fun onCombined(sample: SensorSample) {
        csv.write(sample)
        combinedCount++
        processing.submit(sample)
    }

    private fun buildDiagnostics() = SamplingDiagnostics(
        accelerometer = accStats.snapshot(),
        gyroscope = gyroStats.snapshot(),
        combinedSamples = combinedCount,
        ringBufferOverflows = ring.overflowCount,
        ringBufferHighWatermark = ring.highWatermark,
        ringBufferCapacity = ring.capacity,
        samplesWithoutGyro = aligner.samplesWithoutGyro,
        processorQueueDrops = processing.drops,
        durationMs = (accStats.lastTimestampNs - accStats.firstTimestampNs) / 1_000_000L,
        timeBase = timeBase?.name ?: "-"
    )

    /** Stop ordinato: flush FIFO -> unregister -> drain finale -> flush aligner -> chiusura file -> metadati. */
    suspend fun stop(): SamplingDiagnostics {
        withContext(Dispatchers.IO) { sensorSource.stop(batchingEnabled = config.maxReportLatencyUs > 0) }
        running = false
        loopJob?.join()
        val diagnostics = withContext(dispatcher) {
            aligner.flush()
            val offsetStop = timeBase?.let { calibrator.measureOffset(it) }
            val d = buildDiagnostics()
            csv.close()
            raw?.close()
            SessionMetadata.write(
                session.metadataJson,
                SessionMetadata.Content(
                    sessionId = session.id,
                    config = config,
                    accelerometer = sensors.accelerometer,
                    gyroscope = sensors.gyroscope,
                    timeBase = timeBase,
                    offsetAtStart = offsetStart,
                    offsetAtStop = offsetStop,
                    diagnostics = d,
                    accuracyEvents = sensorSource.accuracyEvents.toList(),
                    firstSampleNs = accStats.firstTimestampNs,
                    lastSampleNs = accStats.lastTimestampNs
                )
            )
            d
        }
        processing.stop()
        val final = diagnostics.copy(processorQueueDrops = processing.drops)
        publishDiagnostics(final)
        return final
    }

    private companion object {
        const val RING_CAPACITY = 1 shl 16
        const val POLL_PERIOD_MS = 4L
        const val DIAGNOSTICS_PERIOD_MS = 500L
        const val FLUSH_PERIOD_MS = 5_000L
    }
}
