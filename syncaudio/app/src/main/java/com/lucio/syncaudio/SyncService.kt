package com.lucio.syncaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.lucio.syncaudio.core.SyncReceiver
import com.lucio.syncaudio.core.SyncSource
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/** Shared state between the service (which does the work) and the UI (which shows it). */
object SyncController {
    enum class Role { IDLE, SOURCE, RECEIVER }

    @Volatile var role: Role = Role.IDLE
    @Volatile var source: SyncSource? = null
    @Volatile var receiver: SyncReceiver? = null
    @Volatile var lastPeak: Int = 0
    @Volatile var warning: String? = null

    fun prefs(ctx: Context) = ctx.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun deviceName(ctx: Context): String =
        Settings.Global.getString(ctx.contentResolver, "device_name") ?: Build.MODEL
}

object NetUtil {
    /** Broadcast address of every active IPv4 interface (Wi-Fi, hotspot, Wi-Fi Direct...). */
    fun broadcastAddresses(): List<InetAddress> {
        val out = linkedSetOf<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { out.add(it) }
            }
        } catch (_: Exception) {}
        out.add(InetAddress.getByName("255.255.255.255"))
        return out.toList()
    }

    fun localIPv4(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${it.hostAddress} (${ni.name})" } }
    } catch (_: Exception) { emptyList() }
}

/**
 * Foreground service that runs either as SOURCE (captures and sends) or RECEIVER (plays).
 * A single service with a role switch makes "takeover" possible: when the other phone
 * starts transmitting, this one becomes a receiver without user interaction.
 */
class SyncService : Service() {

    companion object {
        const val ACTION_SOURCE = "com.lucio.syncaudio.SOURCE"
        const val ACTION_RECEIVER = "com.lucio.syncaudio.RECEIVER"
        const val ACTION_CONNECT = "com.lucio.syncaudio.CONNECT"
        const val ACTION_STOP = "com.lucio.syncaudio.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_HOST = "host"
        private const val CHANNEL_ID = "sync"
        private const val NOTIF_ID = 1
        private const val TAG = "SyncService"
    }

    private val main = Handler(Looper.getMainLooper())
    /** All role changes run here: they do network I/O, which Android forbids on the main thread. */
    private val ctlThread = HandlerThread("sync-control").apply { start() }
    private val ctl = Handler(ctlThread.looper)
    private var projection: MediaProjection? = null
    private var capture: AudioCapture? = null
    private var player: AudioPlayer? = null
    private var wifiLocks = mutableListOf<WifiManager.WifiLock>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var silentSeconds = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Sincronizzazione audio", NotificationManager.IMPORTANCE_LOW))
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ctl.post { handle(intent) }
        return START_NOT_STICKY
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_SOURCE -> {
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else intent.getParcelableExtra(EXTRA_DATA)
                if (data != null) startSource(code, data) else stopEverything()
            }
            ACTION_RECEIVER -> startReceiver()
            ACTION_CONNECT -> {
                if (SyncController.role != SyncController.Role.RECEIVER) startReceiver()
                val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
                try { SyncController.receiver?.connectManual(host) } catch (e: Exception) {
                    SyncController.warning = "Indirizzo non valido: $host"
                }
            }
            ACTION_STOP -> stopEverything()
            null -> stopEverything()
        }
    }

    // ---------------------------------------------------------------- source

    private fun startSource(code: Int, data: Intent) {
        // Must be foreground with type mediaProjection BEFORE getMediaProjection (Android 14+).
        if (!goForeground("Trasmissione in corso…", ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)) {
            if (!goForeground("Trasmissione in corso…", ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)) return
        }
        // If we were listening to the other phone, ask it to switch roles with us.
        SyncController.receiver?.requestTakeover()
        stopReceiverParts()
        stopSourceParts()

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (mp == null) {
            SyncController.warning = "Permesso di cattura negato."
            stopEverything(); return
        }
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { ctl.post { if (projection === mp) stopEverything() } }
        }, main)
        projection = mp

        val prefs = SyncController.prefs(this)
        val src = SyncSource(SyncController.deviceName(this), SAMPLE_RATE, CHANNELS, NetUtil::broadcastAddresses)
        src.baseDelayMs = prefs.getInt("baseDelayMs", 150)
        src.listener = object : SyncSource.Listener {
            override fun onPeersChanged(peers: List<SyncSource.Peer>) {
                ctl.post { updateNotification(if (peers.isEmpty()) "In attesa di ricevitori…" else "Trasmetto a: " + peers.joinToString { it.name }) }
            }
            override fun onTakeoverRequested(from: InetSocketAddress) {
                // The other phone wants to be the source: become a receiver.
                ctl.post { startReceiver() }
            }
        }
        try {
            src.start()
        } catch (e: Exception) {
            SyncController.warning = "Rete non disponibile: ${e.message}"
            stopEverything(); return
        }
        SyncController.source = src
        silentSeconds = 0
        SyncController.warning = null
        capture = AudioCapture(mp, src) { peak -> onCapturePeak(peak) }.also { it.start() }
        SyncController.role = SyncController.Role.SOURCE
        updateNotification("In attesa di ricevitori…")
    }

    private fun onCapturePeak(peak: Int) {
        SyncController.lastPeak = peak
        val am = getSystemService(AudioManager::class.java)
        silentSeconds = if (peak < 8) silentSeconds + 1 else 0
        SyncController.warning = when {
            capture?.error != null -> capture?.error
            silentSeconds >= 4 && am.isMusicActive ->
                "C'è audio in riproduzione ma la cattura è muta: l'app che sta suonando probabilmente vieta la cattura (tipico di app con DRM)."
            else -> null
        }
    }

    private fun stopSourceParts() {
        capture?.stop(); capture = null
        SyncController.source?.stop(); SyncController.source = null
        projection?.let { p -> projection = null; try { p.stop() } catch (_: Exception) {} }
    }

    // -------------------------------------------------------------- receiver

    private fun startReceiver() {
        if (!goForeground("Ricezione…", ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)) return
        stopSourceParts()
        if (SyncController.receiver != null) { SyncController.role = SyncController.Role.RECEIVER; return }
        val prefs = SyncController.prefs(this)
        val rcv = SyncReceiver(SyncController.deviceName(this))
        rcv.userOffsetMs = prefs.getInt("userOffsetMs", 0)
        rcv.listener = object : SyncReceiver.Listener {
            override fun onSourceChanged(source: SyncReceiver.SourceInfo?) {
                ctl.post { updateNotification(if (source == null) "Cerco una sorgente…" else "Ricevo da ${source.name}") }
            }
        }
        try {
            rcv.start()
        } catch (e: Exception) {
            SyncController.warning = "Impossibile aprire la rete (porta occupata?): ${e.message}"
            stopEverything(); return
        }
        SyncController.receiver = rcv
        SyncController.warning = null
        player = AudioPlayer { SyncController.receiver?.engine }.also { it.start() }
        SyncController.role = SyncController.Role.RECEIVER
        updateNotification("Cerco una sorgente…")
    }

    private fun stopReceiverParts() {
        player?.stop(); player = null
        SyncController.receiver?.stop(); SyncController.receiver = null
    }

    // ---------------------------------------------------------------- common

    private fun stopEverything() {
        stopSourceParts()
        stopReceiverParts()
        SyncController.role = SyncController.Role.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ctl.post {
            stopSourceParts()
            stopReceiverParts()
            SyncController.role = SyncController.Role.IDLE
            releaseLocks()
            ctlThread.quitSafely()
        }
        super.onDestroy()
    }

    private fun goForeground(text: String, type: Int): Boolean = try {
        startForeground(NOTIF_ID, buildNotification(text), type)
        true
    } catch (e: Exception) {
        Log.e(TAG, "startForeground failed", e)
        SyncController.warning = "Android non ha permesso il servizio in primo piano: apri l'app e riprova."
        false
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SyncService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentTitle("SyncAudio")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        if (SyncController.role == SyncController.Role.IDLE) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        // Wi-Fi power save adds 50-300 ms of random latency: keep the radio awake.
        for (mode in listOf(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WifiManager.WIFI_MODE_FULL_HIGH_PERF)) {
            try { wifiLocks += wm.createWifiLock(mode, "syncaudio:$mode").apply { setReferenceCounted(false); acquire() } } catch (_: Exception) {}
        }
        multicastLock = wm.createMulticastLock("syncaudio").apply { setReferenceCounted(false); acquire() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "syncaudio:run").apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseLocks() {
        wifiLocks.forEach { try { it.release() } catch (_: Exception) {} }
        wifiLocks.clear()
        multicastLock?.release(); multicastLock = null
        wakeLock?.release(); wakeLock = null
    }
}
