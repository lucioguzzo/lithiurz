package com.cardioresp.monitor.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cardioresp.monitor.CardioRespApp
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.AcquisitionState
import com.cardioresp.monitor.domain.model.SamplingRate
import kotlinx.coroutines.launch

/**
 * Foreground Service che "possiede" la sessione di acquisizione.
 *
 * Perché un Foreground Service:
 * - il processo ottiene priorità "foreground": non viene ucciso né congelato (cached apps
 *   freezer di Android 11+) quando l'utente cambia app o spegne lo schermo;
 * - su Android 9+ le app in background NON ricevono eventi da sensori continui: senza FGS
 *   l'acquisizione si interromperebbe appena l'Activity va in background;
 * - la notifica permanente rende l'acquisizione visibile e interrompibile (requisito di sistema).
 *
 * Il PARTIAL_WAKE_LOCK mantiene la CPU attiva a schermo spento: i sensori non-wakeup
 * continuano a riempire la FIFO, ma senza CPU sveglia nessuno la svuota -> perdita campioni.
 *
 * Tipo FGS "health" (Android 14+): è il tipo corretto per sensori corporei/fitness e,
 * a differenza di "dataSync", non ha il limite di 6 ore introdotto in Android 15.
 */
class AcquisitionService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val repository get() = (application as CardioRespApp).container.acquisitionRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> stopSelf() // riavvio di sistema senza intent: non riprendiamo una sessione a metà
        }
        // NOT_STICKY: se il sistema uccide il processo la sessione è comunque compromessa;
        // meglio una registrazione chiusa e marcata come incompleta che una ripresa silenziosa.
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (repository.state.value is AcquisitionState.Recording) return
        val startedAt = System.currentTimeMillis()
        // startForeground va chiamato entro pochi secondi da startForegroundService().
        ServiceCompat.startForeground(
            this,
            AcquisitionNotification.NOTIFICATION_ID,
            AcquisitionNotification.build(this, startedAt),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        )
        acquireWakeLock()

        val config = AcquisitionConfig(
            samplingRate = SamplingRate.valueOf(intent.getStringExtra(EXTRA_RATE) ?: SamplingRate.HZ_200.name),
            maxReportLatencyUs = intent.getIntExtra(EXTRA_LATENCY_US, 0),
            useUncalibratedSensors = intent.getBooleanExtra(EXTRA_UNCALIBRATED, false)
        )
        lifecycleScope.launch {
            repository.start(config)
            if (repository.state.value is AcquisitionState.Error) shutdown()
        }
    }

    private fun handleStop() {
        lifecycleScope.launch {
            repository.stop()
            shutdown()
        }
    }

    private fun shutdown() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Se il servizio viene distrutto con una sessione attiva (es. l'utente forza la chiusura
        // dalla notifica di sistema "app in esecuzione"), chiudiamo i file in modo pulito.
        if (repository.state.value is AcquisitionState.Recording) {
            val container = (application as CardioRespApp).container
            container.applicationScope.launch { container.acquisitionRepository.stop() }
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CardioResp:acquisition").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS) // timeout di sicurezza
        }
        Log.i(TAG, "Wake lock acquisito")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "AcquisitionService"
        const val ACTION_START = "com.cardioresp.monitor.action.START"
        const val ACTION_STOP = "com.cardioresp.monitor.action.STOP"
        const val EXTRA_RATE = "rate"
        const val EXTRA_LATENCY_US = "latency_us"
        const val EXTRA_UNCALIBRATED = "uncalibrated"
        private const val MAX_WAKELOCK_MS = 12L * 60 * 60 * 1000

        fun start(context: Context, config: AcquisitionConfig) {
            val i = Intent(context, AcquisitionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RATE, config.samplingRate.name)
                .putExtra(EXTRA_LATENCY_US, config.maxReportLatencyUs)
                .putExtra(EXTRA_UNCALIBRATED, config.useUncalibratedSensors)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AcquisitionService::class.java).setAction(ACTION_STOP))
        }
    }
}
