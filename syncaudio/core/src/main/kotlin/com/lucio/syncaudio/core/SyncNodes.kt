package com.lucio.syncaudio.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

typealias NanoClock = () -> Long

/** Hook to configure every socket before bind (Android: bind it to the Wi-Fi network). */
typealias SocketHook = (DatagramSocket) -> Unit

private fun udpSocket(port: Int, hook: SocketHook): DatagramSocket {
    val s = DatagramSocket(null)
    try {
        s.reuseAddress = true
        s.broadcast = true
        try { s.trafficClass = 0xB8 } catch (_: Exception) {} // DSCP EF hint (WMM voice queue); optional
        hook(s)
        s.bind(InetSocketAddress(port))
    } catch (e: Exception) {
        s.close(); throw e
    }
    return s
}

private fun thread(name: String, body: () -> Unit): Thread =
    Thread(body, name).apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }

/** Counters shown in the app's diagnostics screen. */
class NetStats {
    val sent = AtomicLong()
    val received = AtomicLong()
    val sendErrors = AtomicLong()
    @Volatile var lastError: String? = null

    fun error(where: String, e: Throwable) {
        sendErrors.incrementAndGet()
        lastError = "$where: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
    }
}

/**
 * The phone that captures the audio. Announces itself (periodic broadcast beacon, and a
 * unicast beacon in reply to receivers' probes), answers clock pings and fans the captured
 * audio out (unicast) to every receiver that said hello.
 */
class SyncSource(
    private val name: String,
    val sampleRate: Int,
    val channels: Int,
    private val beaconTargets: () -> List<InetAddress>,
    private val controlPort: Int = Proto.CONTROL_PORT,
    private val discoveryPort: Int = Proto.DISCOVERY_PORT,
    private val clock: NanoClock = System::nanoTime,
    val sessionId: Long = Random.nextLong(1, Long.MAX_VALUE),
    private val socketHook: SocketHook = {},
) {
    class Peer(val address: InetSocketAddress, @Volatile var name: String, @Volatile var lastSeenNs: Long)

    interface Listener {
        fun onPeersChanged(peers: List<Peer>) {}
        /** A receiver wants to become the source: we should stop and start receiving. */
        fun onTakeoverRequested(from: InetSocketAddress) {}
    }

    @Volatile var baseDelayMs: Int = 80
    @Volatile var listener: Listener? = null
    val stats = NetStats()
    val beaconsSent = AtomicLong()
    val probesAnswered = AtomicLong()
    val audioPacketsSent = AtomicLong()

    private val peers = ConcurrentHashMap<InetSocketAddress, Peer>()
    private lateinit var socket: DatagramSocket
    @Volatile private var running = false
    private val audioBuf = Wire.newBuffer()
    private var seq = 0

    val localPort: Int get() = socket.localPort
    val peerList: List<Peer> get() = peers.values.sortedBy { it.name }

    fun start() {
        socket = udpSocket(controlPort, socketHook)
        running = true
        thread("sync-src-rx") { rxLoop() }
        thread("sync-src-beacon") { beaconLoop() }
    }

    fun stop() {
        if (!running) return
        running = false
        val buf = Wire.newBuffer()
        val len = Wire.encode(Bye(sessionId, false), buf)
        for (p in peers.values) send(buf.array(), len, p.address)
        peers.clear()
        socket.close()
    }

    /** Called from the capture thread for each captured chunk. */
    fun sendAudio(framePos: Long, ptsNs: Long, pcm: ShortArray, frames: Int) {
        if (!running || peers.isEmpty()) return
        val len = Wire.encodeAudio(audioBuf, sessionId, seq++, framePos, ptsNs, sampleRate, channels, baseDelayMs, pcm, frames)
        for (p in peers.values) if (send(audioBuf.array(), len, p.address)) audioPacketsSent.incrementAndGet()
    }

    private fun send(data: ByteArray, len: Int, to: InetSocketAddress): Boolean = try {
        socket.send(DatagramPacket(data, len, to))
        stats.sent.incrementAndGet()
        true
    } catch (e: Exception) {
        if (running) stats.error("invio a ${to.address.hostAddress}", e)
        false
    }

    private fun beacon() = Beacon(sessionId, socket.localPort, sampleRate, channels, name)

    private fun rxLoop() {
        val rx = ByteArray(Proto.MAX_DATAGRAM)
        val pkt = DatagramPacket(rx, rx.size)
        val tx = Wire.newBuffer()
        while (running) {
            try {
                pkt.setData(rx, 0, rx.size)
                socket.receive(pkt)
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            val t2 = clock()
            stats.received.incrementAndGet()
            val from = pkt.socketAddress as InetSocketAddress
            when (val msg = Wire.decode(pkt.data, pkt.offset, pkt.length)) {
                is Ping -> {
                    if (msg.sessionId == sessionId) {
                        val len = Wire.encode(Pong(sessionId, msg.t1, t2, clock()), tx)
                        send(tx.array(), len, from)
                        touch(from, null)
                    } else {
                        // A receiver looking for sources (probe): tell it who we are, directly.
                        val len = Wire.encode(beacon(), tx)
                        if (send(tx.array(), len, from)) probesAnswered.incrementAndGet()
                    }
                }
                is Hello -> if (msg.sessionId == sessionId) touch(from, msg.name) else {
                    val len = Wire.encode(beacon(), tx)
                    send(tx.array(), len, from)
                }
                is Bye -> {
                    if (peers.remove(from) != null) notifyPeers()
                    if (msg.takeover) listener?.onTakeoverRequested(from)
                }
                else -> Unit
            }
        }
    }

    private fun touch(from: InetSocketAddress, name: String?) {
        val now = clock()
        val existing = peers[from]
        if (existing == null) {
            peers[from] = Peer(from, name ?: from.address.hostAddress ?: "?", now)
            notifyPeers()
        } else {
            existing.lastSeenNs = now
            if (name != null && name != existing.name) { existing.name = name; notifyPeers() }
        }
    }

    private fun notifyPeers() = listener?.onPeersChanged(peerList)

    private fun beaconLoop() {
        val buf = Wire.newBuffer()
        while (running) {
            val len = Wire.encode(beacon(), buf)
            for (addr in beaconTargets()) {
                if (send(buf.array(), len, InetSocketAddress(addr, discoveryPort))) beaconsSent.incrementAndGet()
            }
            val now = clock()
            val expired = peers.values.filter { now - it.lastSeenNs > PEER_TIMEOUT_NS }
            if (expired.isNotEmpty()) {
                expired.forEach { peers.remove(it.address) }
                notifyPeers()
            }
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
        }
    }

    companion object {
        const val PEER_TIMEOUT_NS = 6_000_000_000L
    }
}

/**
 * The phone that plays. Finds a source through three independent channels:
 *  1. the source's periodic broadcast beacon (port [Proto.DISCOVERY_PORT]);
 *  2. its own broadcast probes to [Proto.CONTROL_PORT], answered by the source in unicast
 *     (works when only one direction of broadcast gets through);
 *  3. explicit addresses: typed by the user or found via mDNS/NSD ([probe]).
 * Then it keeps its clock locked to the source and feeds audio into a [PlayoutEngine].
 */
class SyncReceiver(
    private val name: String,
    private val discoveryPort: Int = Proto.DISCOVERY_PORT,
    private val clock: NanoClock = System::nanoTime,
    private val probeTargets: () -> List<InetAddress> = { emptyList() },
    private val probePort: Int = Proto.CONTROL_PORT,
    private val socketHook: SocketHook = {},
) {
    data class SourceInfo(val address: InetSocketAddress, val sessionId: Long, val name: String, val sampleRate: Int, val channels: Int)

    interface Listener {
        fun onSourceChanged(source: SourceInfo?) {}
    }

    @Volatile var listener: Listener? = null
    @Volatile var source: SourceInfo? = null; private set
    @Volatile var engine: PlayoutEngine? = null; private set
    @Volatile var clockSync: ClockSync = ClockSync(); private set
    @Volatile var userOffsetMs: Int = 0
        set(value) { field = value; engine?.userOffsetMs = value }

    val stats = NetStats()
    val beaconsSeen = AtomicLong()
    val probeReplies = AtomicLong()
    val probesSent = AtomicLong()
    val pongs = AtomicLong()
    val audioPackets = AtomicLong()
    val attaches = AtomicLong()
    val lostSource = AtomicLong()
    /** Who answered last, even if we did not attach (e.g. wrong session). */
    @Volatile var lastSeenSource: String? = null; private set

    private lateinit var discSocket: DatagramSocket
    private lateinit var dataSocket: DatagramSocket
    @Volatile private var running = false
    @Volatile private var lastHeardNs = 0L
    @Volatile private var attachedAtNs = 0L
    private val extraTargets = ConcurrentHashMap.newKeySet<InetSocketAddress>()
    @Volatile private var ignoredSession: Long = -1

    fun start() {
        discSocket = udpSocket(discoveryPort, socketHook)
        try {
            dataSocket = udpSocket(0, socketHook)
        } catch (e: Exception) {
            discSocket.close(); throw e
        }
        running = true
        thread("sync-rcv-disc") { rxLoop(discSocket) }
        thread("sync-rcv-data") { rxLoop(dataSocket) }
        thread("sync-rcv-ping") { pingLoop() }
    }

    fun stop() {
        if (!running) return
        running = false
        source?.let { sendTo(Bye(it.sessionId, false), it.address) }
        detach(notify = false)
        discSocket.close()
        dataSocket.close()
    }

    /**
     * Adds an explicit source address to probe (typed by the user, or resolved via mDNS).
     * Blocking DNS resolution: call off the main thread.
     */
    fun probe(host: String, port: Int = Proto.CONTROL_PORT) {
        val addr = InetSocketAddress(InetAddress.getByName(host), port)
        extraTargets.add(addr)
        sendTo(Ping(0, clock()), addr)
        probesSent.incrementAndGet()
    }

    /** Tell the current source we are taking over (it will switch to receiving). */
    fun requestTakeover() {
        val s = source ?: return
        sendTo(Bye(s.sessionId, true), s.address)
        ignoredSession = s.sessionId
        detach(notify = true)
    }

    private fun attach(info: SourceInfo) {
        val cs = ClockSync()
        val eng = PlayoutEngine(info.sampleRate, info.channels, cs).also { it.userOffsetMs = userOffsetMs }
        clockSync = cs
        engine = eng
        source = info
        attachedAtNs = clock()
        lastHeardNs = clock()
        attaches.incrementAndGet()
        sendTo(Hello(info.sessionId, name), info.address)
        listener?.onSourceChanged(info)
    }

    private fun detach(notify: Boolean) {
        val had = source != null
        source = null
        engine = null
        if (notify && had) listener?.onSourceChanged(null)
    }

    private fun sendTo(msg: Msg, to: InetSocketAddress): Boolean = try {
        val buf = Wire.newBuffer()
        val len = Wire.encode(msg, buf)
        dataSocket.send(DatagramPacket(buf.array(), len, to))
        stats.sent.incrementAndGet()
        true
    } catch (e: Exception) {
        if (running) stats.error("invio a ${to.address.hostAddress}", e)
        false
    }

    private fun onBeacon(b: Beacon, from: InetAddress) {
        lastSeenSource = "${b.name} (${from.hostAddress})"
        if (b.sessionId == ignoredSession) return
        val current = source
        if (current == null || (current.sessionId != b.sessionId && clock() - lastHeardNs > STALE_NS)) {
            attach(SourceInfo(InetSocketAddress(from, b.controlPort), b.sessionId, b.name, b.sampleRate, b.channels))
        }
    }

    private fun rxLoop(sock: DatagramSocket) {
        val rx = ByteArray(Proto.MAX_DATAGRAM)
        val pkt = DatagramPacket(rx, rx.size)
        while (running) {
            try {
                pkt.setData(rx, 0, rx.size)
                sock.receive(pkt)
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            val t4 = clock()
            stats.received.incrementAndGet()
            val msg = Wire.decode(pkt.data, pkt.offset, pkt.length) ?: continue
            if (msg is Beacon) {
                if (sock === discSocket) beaconsSeen.incrementAndGet() else probeReplies.incrementAndGet()
                onBeacon(msg, pkt.address)
                continue
            }
            val s = source ?: continue
            if (msg.sessionId != s.sessionId) continue
            lastHeardNs = t4
            when (msg) {
                is Pong -> { pongs.incrementAndGet(); clockSync.addExchange(msg.t1, msg.t2, msg.t3, t4) }
                is Audio -> { audioPackets.incrementAndGet(); engine?.onAudio(msg.framePos, msg.ptsNs, msg.pcm, msg.frames, msg.baseDelayMs) }
                is Bye -> {
                    ignoredSession = msg.sessionId
                    detach(notify = true)
                }
                else -> Unit
            }
        }
    }

    private fun pingLoop() {
        var lastHello = 0L
        var lastProbe = 0L
        while (running) {
            val s = source
            val now = clock()
            if (s != null) {
                if (now - lastHeardNs > LOST_NS) {
                    lostSource.incrementAndGet()
                    detach(notify = true)
                } else {
                    sendTo(Ping(s.sessionId, clock()), s.address)
                    if (now - lastHello > 2_000_000_000L) { sendTo(Hello(s.sessionId, name), s.address); lastHello = now }
                }
            } else if (now - lastProbe > 1_000_000_000L) {
                lastProbe = now
                val targets = probeTargets().map { InetSocketAddress(it, probePort) } + extraTargets
                for (t in targets) if (sendTo(Ping(0, clock()), t)) probesSent.incrementAndGet()
            }
            // Fast pings while locking the clock, then a relaxed rate to track drift.
            val fast = s != null && now - attachedAtNs < 15_000_000_000L
            try { Thread.sleep(if (fast || s == null) 100 else 300) } catch (_: InterruptedException) { break }
        }
    }

    companion object {
        const val STALE_NS = 3_000_000_000L
        const val LOST_NS = 4_000_000_000L
    }
}
