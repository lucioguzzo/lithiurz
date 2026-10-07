package com.cardioresp.monitor.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.SensorKind
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Registrazione dei listener su un HandlerThread DEDICATO ad alta priorità.
 *
 * Scelte progettuali:
 * - Un solo thread per entrambi i sensori -> un solo produttore -> ring buffer SPSC lock-free.
 * - `registerListener(listener, sensor, samplingPeriodUs, maxReportLatencyUs, handler)`:
 *   il periodo è esplicito (o 0 = FASTEST); il quarto parametro controlla il batching FIFO.
 * - Il callback fa SOLO una copia dei 3 float + timestamp nel ring buffer (~100 ns).
 *   `SensorEvent` viene riciclato dal framework: non va mai conservato.
 * - Priorità URGENT_DISPLAY: il thread viene schedulato prima dei thread normali di
 *   qualunque app; riduce la latenza di consegna (il timestamp hardware non ne dipende,
 *   ma code piene nel framework possono causare perdite).
 */
class SensorDataSource(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var thread: HandlerThread? = null
    private var ring: SensorRingBuffer? = null
    private var accSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    @Volatile private var flushLatch: CountDownLatch? = null

    /** Cambi di accuratezza riportati dal sensore (salvati nei metadati). */
    val accuracyEvents = ConcurrentLinkedQueue<AccuracyEvent>()

    data class AccuracyEvent(val sensor: SensorKind, val accuracy: Int, val elapsedRealtimeNs: Long)

    data class StartResult(val accelerometer: Sensor?, val gyroscope: Sensor?)

    private val listener = object : SensorEventListener2 {
        override fun onSensorChanged(event: SensorEvent) {
            val code = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> SensorKind.ACCELEROMETER.code
                else -> SensorKind.GYROSCOPE.code
            }
            val v = event.values
            // Per i sensori UNCALIBRATED values[3..5] contengono la stima del bias: qui salviamo
            // solo il segnale grezzo (values[0..2]), che è ciò che serve alla DSP.
            ring?.offer(code, event.timestamp, v[0], v[1], v[2])
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            val kind = if (sensor.type == accSensor?.type) SensorKind.ACCELEROMETER else SensorKind.GYROSCOPE
            accuracyEvents.add(AccuracyEvent(kind, accuracy, android.os.SystemClock.elapsedRealtimeNanos()))
        }

        override fun onFlushCompleted(sensor: Sensor) {
            flushLatch?.countDown()
        }
    }

    fun resolveSensors(useUncalibrated: Boolean): StartResult {
        val acc = (if (useUncalibrated) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) else null)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = (if (useUncalibrated) sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED) else null)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        return StartResult(acc, gyro)
    }

    fun start(config: AcquisitionConfig, ringBuffer: SensorRingBuffer): StartResult {
        check(thread == null) { "SensorDataSource già avviato" }
        val sensors = resolveSensors(config.useUncalibratedSensors)
        requireNotNull(sensors.accelerometer) { "Accelerometro non disponibile su questo dispositivo" }

        ring = ringBuffer
        accSensor = sensors.accelerometer
        gyroSensor = sensors.gyroscope
        accuracyEvents.clear()

        val t = object : HandlerThread("SensorAcquisition") {
            override fun onLooperPrepared() {
                // Impostata qui (e non nel costruttore) per gestire eventuali SecurityException
                // su ROM restrittive senza far morire il thread.
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
                } catch (e: Exception) {
                    Log.w(TAG, "Impossibile alzare la priorità del thread sensori", e)
                }
            }
        }
        t.start()
        thread = t
        val handler = Handler(t.looper)

        val period = config.samplingRate.periodUs // 0 == SensorManager.SENSOR_DELAY_FASTEST
        val latency = config.maxReportLatencyUs
        val okAcc = sensorManager.registerListener(listener, sensors.accelerometer, period, latency, handler)
        val okGyro = sensors.gyroscope?.let { sensorManager.registerListener(listener, it, period, latency, handler) } ?: false
        Log.i(TAG, "registerListener acc=$okAcc gyro=$okGyro periodUs=$period latencyUs=$latency")
        return sensors
    }

    /**
     * Stop ordinato. Se il batching è attivo, prima chiede un flush della FIFO hardware
     * (altrimenti gli eventi ancora nel sensor hub andrebbero persi all'unregister).
     */
    fun stop(batchingEnabled: Boolean) {
        val t = thread ?: return
        if (batchingEnabled) {
            val sensorsToFlush = listOfNotNull(accSensor, gyroSensor).size
            flushLatch = CountDownLatch(sensorsToFlush)
            if (sensorManager.flush(listener)) {
                flushLatch?.await(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            flushLatch = null
        }
        sensorManager.unregisterListener(listener)
        t.quitSafely()
        t.join(1_000)
        thread = null
        ring = null
    }

    private companion object {
        const val TAG = "SensorDataSource"
        const val FLUSH_TIMEOUT_MS = 1_000L
    }
}
