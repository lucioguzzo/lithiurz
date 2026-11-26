package com.lucio.syncaudio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * mDNS / DNS-SD discovery, the same mechanism printers and Chromecasts use. It is a second,
 * independent discovery path next to our UDP broadcast: the system mDNS daemon handles
 * Wi-Fi power-save and multicast filtering better than an app socket does, and some
 * networks pass multicast but not broadcast. (Bonus: addresses resolved via NSD remain
 * reachable under Android 17's local-network permission.)
 */
class Nsd(ctx: Context) {
    private val nsd = ctx.getSystemService(NsdManager::class.java)
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    @Volatile private var stopped = false

    fun advertise(name: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "SyncAudio $name"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(si: NsdServiceInfo) { SyncController.nsdStatus = "annunciato come \"${si.serviceName}\"" }
            override fun onRegistrationFailed(si: NsdServiceInfo, err: Int) { SyncController.nsdStatus = "annuncio fallito ($err)" }
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
            override fun onUnregistrationFailed(si: NsdServiceInfo, err: Int) {}
        }
        regListener = l
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) {
            SyncController.nsdStatus = "annuncio non disponibile: ${e.message}"
        }
    }

    /** Calls [onFound] with host/port of every SyncAudio source found on the network. */
    fun discover(onFound: (String, Int) -> Unit) {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { SyncController.nsdStatus = "ricerca attiva" }
            override fun onStartDiscoveryFailed(type: String, err: Int) { SyncController.nsdStatus = "ricerca fallita ($err)" }
            override fun onStopDiscoveryFailed(type: String, err: Int) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onServiceFound(si: NsdServiceInfo) {
                SyncController.nsdStatus = "trovato \"${si.serviceName}\""
                synchronized(pending) { pending.addLast(si) }
                resolveNext(onFound)
            }
        }
        discListener = l
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) {
            SyncController.nsdStatus = "ricerca non disponibile: ${e.message}"
        }
    }

    // NsdManager resolves one service at a time.
    @Suppress("DEPRECATION")
    private fun resolveNext(onFound: (String, Int) -> Unit) {
        val si = synchronized(pending) {
            if (resolving || pending.isEmpty() || stopped) return
            resolving = true
            pending.removeFirst()
        }
        try {
            nsd.resolveService(si, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) r.hostAddresses.firstOrNull()?.hostAddress else r.host?.hostAddress
                    if (host != null && !stopped) {
                        SyncController.nsdStatus = "trovato \"${r.serviceName}\" a $host:${r.port}"
                        onFound(host, r.port)
                    }
                    done()
                }
                override fun onResolveFailed(s: NsdServiceInfo, err: Int) { done() }
                private fun done() {
                    synchronized(pending) { resolving = false }
                    resolveNext(onFound)
                }
            })
        } catch (e: Exception) {
            Log.w("SyncAudio", "resolve failed", e)
            synchronized(pending) { resolving = false }
        }
    }

    fun stop() {
        stopped = true
        regListener?.let { try { nsd.unregisterService(it) } catch (_: Exception) {} }
        discListener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        regListener = null; discListener = null
    }

    companion object {
        const val SERVICE_TYPE = "_syncaudio._udp."
    }
}
