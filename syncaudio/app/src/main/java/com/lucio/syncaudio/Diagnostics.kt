package com.lucio.syncaudio

import android.content.Context
import android.os.Build

/** Human-readable state of everything that matters for the connection, plus likely causes. */
object Diagnostics {

    fun report(ctx: Context): String {
        val sb = StringBuilder()
        val pkg = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" }
        sb.appendLine("SyncAudio $pkg – ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Ruolo: ${SyncController.role}")
        sb.appendLine()
        sb.appendLine("RETI")
        sb.append(NetUtil.networkReport(ctx))
        sb.appendLine("IP locali: " + NetUtil.localIPv4().joinToString().ifEmpty { "nessuno" })
        sb.appendLine("Broadcast: " + NetUtil.broadcastAddresses().joinToString { it.hostAddress ?: "?" })
        sb.appendLine("Socket legati alla Wi-Fi: " + if (SyncController.boundToWifi) "sì" else "no")
        sb.appendLine("mDNS: ${SyncController.nsdStatus}")
        SyncController.lastNetError?.let { sb.appendLine("Errore rete: $it") }

        SyncController.source?.let { s ->
            sb.appendLine()
            sb.appendLine("TRASMETTITORE (porta ${try { s.localPort } catch (_: Exception) { "?" }})")
            sb.appendLine("Beacon inviati: ${s.beaconsSent.get()}  ·  probe a cui ho risposto: ${s.probesAnswered.get()}")
            sb.appendLine("Pacchetti ricevuti: ${s.stats.received.get()}  ·  audio inviati: ${s.audioPacketsSent.get()}")
            sb.appendLine("Ricevitori: " + s.peerList.joinToString { "${it.name} ${it.address.address.hostAddress}" }.ifEmpty { "nessuno" })
            sb.appendLine("Livello cattura: ${SyncController.lastPeak * 100 / 32767}%")
            sb.appendLine("Errori invio: ${s.stats.sendErrors.get()}" + (s.stats.lastError?.let { " – ultimo: $it" } ?: ""))
        }
        SyncController.receiver?.let { r ->
            sb.appendLine()
            sb.appendLine("RICEVITORE")
            sb.appendLine("Sorgente: " + (r.source?.let { "${it.name} ${it.address}" } ?: "nessuna"))
            sb.appendLine("Ultima sorgente vista: ${r.lastSeenSource ?: "nessuna"}")
            sb.appendLine("Beacon visti: ${r.beaconsSeen.get()}  ·  probe inviati: ${r.probesSent.get()}  ·  risposte ai probe: ${r.probeReplies.get()}")
            sb.appendLine("Pong: ${r.pongs.get()}  ·  pacchetti audio: ${r.audioPackets.get()}")
            sb.appendLine("Agganci: ${r.attaches.get()}  ·  sorgente persa: ${r.lostSource.get()}")
            val cs = r.clockSync
            sb.appendLine("Orologio: " + if (cs.synced) "ok, RTT min %.1f ms, deriva %.0f ppm".format(cs.bestRttNs / 1e6, cs.skewPpm) else "non agganciato")
            r.engine?.let { e ->
                sb.appendLine("Ritardo %d ms · margine %.0f ms · mancante %d/%d frame · resync %d".format(
                    e.targetDelayMs, e.bufferedMs, e.missingFrames, e.renderedFrames, e.resyncs))
            }
            sb.appendLine("Errori invio: ${r.stats.sendErrors.get()}" + (r.stats.lastError?.let { " – ultimo: $it" } ?: ""))
        }
        val hints = hints(ctx)
        if (hints.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("POSSIBILI CAUSE")
            hints.forEach { sb.appendLine("• $it") }
        }
        return sb.toString().trimEnd()
    }

    /** Plain-language guesses from the counters above. */
    fun hints(ctx: Context): List<String> {
        val out = mutableListOf<String>()
        if (NetUtil.vpnActive(ctx)) out += "C'è una VPN attiva: spesso blocca il traffico verso la rete locale. Disattivala."
        val wlanLike = NetUtil.localIPv4().any { ip -> listOf("wlan", "ap", "swlan", "softap", "p2p").any { ip.contains("($it") } }
        if (NetUtil.wifiNetwork(ctx) == null && !wlanLike) {
            out += "Nessuna Wi-Fi né hotspot: i due telefoni devono stare sulla stessa Wi-Fi, o uno in hotspot e l'altro collegato."
        }
        val s = SyncController.source
        if (s != null && s.peerList.isEmpty() && s.stats.received.get() == 0L && s.beaconsSent.get() > 5) {
            out += "Il trasmettitore non riceve nulla dall'altro telefono: la rete isola i dispositivi (Wi-Fi ospiti/pubbliche, \"isolamento AP\") o i telefoni sono su reti diverse. Prova con l'hotspot di uno dei due."
        }
        val r = SyncController.receiver
        if (r != null && r.source == null) {
            if (r.beaconsSeen.get() == 0L && r.probeReplies.get() == 0L && r.probesSent.get() > 5) {
                out += "Il ricevitore non sente nessun trasmettitore. Verifica che l'altro telefono sia in \"Trasmetti\" e sulla stessa rete; altrimenti inserisci a mano l'IP mostrato in basso sull'altro telefono."
            }
        }
        if (r != null && r.lostSource.get() >= 2 && r.pongs.get() == 0L) {
            out += "Il ricevitore vede il trasmettitore ma le risposte non tornano: traffico unidirezionale (firewall/isolamento o rete sbagliata)."
        }
        val sendErrors = (r?.stats?.sendErrors?.get() ?: 0L) + (s?.stats?.sendErrors?.get() ?: 0L)
        if (sendErrors > 0L) {
            out += "Ci sono errori di invio: vedi \"ultimo\" sopra (es. \"Network is unreachable\" = il telefono non ha una rete adatta)."
        }
        return out
    }
}
