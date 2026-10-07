package com.cardioresp.monitor.storage

import android.hardware.Sensor
import android.os.Build
import com.cardioresp.monitor.BuildConfig
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import com.cardioresp.monitor.domain.model.StreamDiagnostics
import com.cardioresp.monitor.sensor.SensorDataSource
import com.cardioresp.monitor.sensor.TimeBaseCalibrator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * metadata.json: tutto ciò che serve per riprodurre/validare una registrazione.
 * Senza questi dati un CSV di IMU non è scientificamente utilizzabile (modello di sensore,
 * frequenza reale, time base, offset dei clock, versione dell'app...).
 */
object SessionMetadata {

    data class Content(
        val sessionId: String,
        val config: AcquisitionConfig,
        val accelerometer: Sensor?,
        val gyroscope: Sensor?,
        val timeBase: TimeBaseCalibrator.TimeBase?,
        val offsetAtStart: TimeBaseCalibrator.OffsetMeasurement?,
        val offsetAtStop: TimeBaseCalibrator.OffsetMeasurement?,
        val diagnostics: SamplingDiagnostics,
        val accuracyEvents: List<SensorDataSource.AccuracyEvent>,
        val firstSampleNs: Long,
        val lastSampleNs: Long
    )

    fun write(file: File, c: Content) {
        val root = JSONObject()
        root.put("schema_version", 1)
        root.put("session_id", c.sessionId)
        root.put("app_version", BuildConfig.VERSION_NAME)
        root.put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("android_release", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("fingerprint", Build.FINGERPRINT)
        })
        root.put("config", JSONObject().apply {
            put("requested_rate", c.config.samplingRate.name)
            put("requested_period_us", c.config.samplingRate.periodUs)
            put("max_report_latency_us", c.config.maxReportLatencyUs)
            put("uncalibrated_sensors", c.config.useUncalibratedSensors)
        })
        root.put("sensors", JSONObject().apply {
            c.accelerometer?.let { put("accelerometer", sensorJson(it)) }
            c.gyroscope?.let { put("gyroscope", sensorJson(it)) }
        })
        root.put("timing", JSONObject().apply {
            put("event_timestamp_time_base", c.timeBase?.name ?: JSONObject.NULL)
            put("first_sample_ns", c.firstSampleNs)
            put("last_sample_ns", c.lastSampleNs)
            c.offsetAtStart?.let { put("unix_offset_ns_at_start", it.offsetNs); put("unix_offset_uncertainty_ns_at_start", it.uncertaintyNs) }
            c.offsetAtStop?.let { put("unix_offset_ns_at_stop", it.offsetNs); put("unix_offset_uncertainty_ns_at_stop", it.uncertaintyNs) }
            if (c.offsetAtStart != null && c.offsetAtStop != null) {
                // Deriva wall clock vs clock monotono durante la sessione (NTP, drift quarzo).
                put("clock_drift_ns", c.offsetAtStop.offsetNs - c.offsetAtStart.offsetNs)
            }
            put("unix_ms_formula", "floor((timestamp_android_ns + unix_offset_ns_at_start) / 1e6)")
        })
        root.put("diagnostics", JSONObject().apply {
            put("accelerometer", streamJson(c.diagnostics.accelerometer))
            put("gyroscope", streamJson(c.diagnostics.gyroscope))
            put("combined_samples", c.diagnostics.combinedSamples)
            put("samples_without_gyro", c.diagnostics.samplesWithoutGyro)
            put("ring_buffer_overflows", c.diagnostics.ringBufferOverflows)
            put("ring_buffer_high_watermark", c.diagnostics.ringBufferHighWatermark)
            put("ring_buffer_capacity", c.diagnostics.ringBufferCapacity)
            put("duration_ms", c.diagnostics.durationMs)
        })
        root.put("accuracy_events", JSONArray().apply {
            c.accuracyEvents.forEach {
                put(JSONObject().put("sensor", it.sensor.label).put("accuracy", it.accuracy).put("elapsed_realtime_ns", it.elapsedRealtimeNs))
            }
        })
        root.put("units", JSONObject().put("acc", "m/s^2").put("gyro", "rad/s").put("timestamp_android_ns", "ns").put("timestamp_unix_ms", "ms since epoch UTC"))
        file.writeText(root.toString(2))
    }

    private fun sensorJson(s: Sensor) = JSONObject().apply {
        put("name", s.name)
        put("vendor", s.vendor)
        put("version", s.version)
        put("type", s.stringType)
        put("resolution", s.resolution.toDouble())
        put("max_range", s.maximumRange.toDouble())
        put("min_delay_us", s.minDelay)
        put("max_delay_us", s.maxDelay)
        put("fifo_max_event_count", s.fifoMaxEventCount)
        put("fifo_reserved_event_count", s.fifoReservedEventCount)
        put("power_ma", s.power.toDouble())
        put("is_wakeup", s.isWakeUpSensor)
    }

    private fun streamJson(d: StreamDiagnostics) = JSONObject().apply {
        put("samples", d.sampleCount)
        put("effective_rate_hz", d.effectiveRateHz)
        put("nominal_period_ms", d.nominalPeriodMs)
        put("mean_interval_ms", d.meanIntervalMs)
        put("std_interval_ms", d.stdIntervalMs)
        put("min_interval_ms", d.minIntervalMs)
        put("max_interval_ms", d.maxIntervalMs)
        put("peak_jitter_ms", d.peakJitterMs)
        put("estimated_lost_samples", d.estimatedLostSamples)
        put("gap_count", d.gapCount)
        put("non_monotonic_count", d.nonMonotonicCount)
    }
}
