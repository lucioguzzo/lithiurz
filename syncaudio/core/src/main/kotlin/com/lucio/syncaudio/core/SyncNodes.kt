package com.lucio.syncaudio.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

typealias NanoClock = () -> Long

private fun udpSocket(port: Int): DatagramSocket = DatagramSocket(null).apply {
    reuseAddress = true
    broadcast = true
    trafficClass = 0xB8 // DSCP EF: hint for Wi-Fi WMM "voice" queue (lower latency)
    bind(InetSocketAddress(port))
}

private fun thread(name: String, body: () -> Unit): Thread =
    Thread(body, name).apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }

/**
 * The phone that captures the audio. Announces itself, answers clock pings and fans the
 * captured audio out (unicast) to every receiver that said hello.
 */
class SyncSource(
    private val name: String,
    val sampleRate: Int,
    val channels: Int,
    private val beaconTargets: () -> List<InetAddress>,
    private val controlPort: Int = Proto.CONTROL_PORT,
    private val discoveryPort: Int = Proto.DISCOVERY_PORT,
    private val clock: NanoClock = System::nanoTime,
    val sessionId: Long = Random.nextLong() and Long.MAX_VALUE,
) {
    class Peer(val address: InetSocketAddress, @Volatile var name: String, @Volatile var lastSeenNs: Long)

    interface Listener {
        fun onPeersChanged(peers: List<Peer>) {}
        /** A receiver wants to become the source: we should stop and start receiving. */
        fun onTakeoverRequested(from: InetSocketAddress) {}
    }

    @Volatile var baseDelayMs: Int = 80
    @Volatile var listener: Listener? = null

    private val peers = ConcurrentHashMap<InetSocketAddress, Peer>()
    private lateinit var socket: DatagramSocket
    @Volatile private var running = false
    private val audioBuf = Wire.newBuffer()
    private var seq = 0

    val localPort: Int get() = socket.localPort
    val peerList: List<Peer> get() = peers.values.sortedBy { it.name }

    fun start() {
        socket = udpSocket(controlPort)
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
        for (p in peers.values) send(audioBuf.array(), len, p.address)
    }

    private fun send(data: ByteArray, len: Int, to: InetSocketAddress) {
        try {
            socket.send(DatagramPacket(data, len, to))
        } catch (_: Exception) {
            // network hiccup / socket closed: audio is real-time, just drop it
        }
    }

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
            val from = pkt.socketAddress as InetSocketAddress
            when (val msg = Wire.decode(pkt.data, pkt.offset, pkt.length)) {
                is Ping -> {
                    val len = Wire.encode(Pong(sessionId, msg.t1, t2, clock()), tx)
                    send(tx.array(), len, from)
                    touch(from, null)
                }
                is Hello -> touch(from, msg.name)
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
            val len = Wire.encode(Beacon(sessionId, socket.localPort, sampleRate, channels, name), buf)
            for (addr in beaconTargets()) send(buf.array(), len, InetSocketAddress(addr, discoveryPort))
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
 * The phone that plays. Finds a source (beacon or manual address), keeps its clock locked
 * to it, and feeds received audio into a [PlayoutEngine] that the local player pulls from.
 */
class SyncReceiver(
    private val name: String,
    private val discoveryPort: Int = Proto.DISCOVERY_PORT,
    private val clock: NanoClock = System::nanoTime,
) {
    data class SourceInfo(val address: InetSocketAddress, val sessionId: Long, val name: String, val sampleRate: Int, val channels: Int)

    interface Listener {
        fun onSourceChanged(source: SourceInfo?) {}
        /** The source asked us to become... nothing: the source simply stopped. */
        fun onSourceStopped() {}
    }

    @Volatile var listener: Listener? = null
    @Volatile var source: SourceInfo? = null; private set
    @Volatile var engine: PlayoutEngine? = null; private set
    @Volatile var clockSync: ClockSync = ClockSync(); private set
    @Volatile var userOffsetMs: Int = 0
        set(value) { field = value; engine?.userOffsetMs = value }

    private lateinit var discSocket: DatagramSocket
    private lateinit var dataSocket: DatagramSocket
    @Volatile private var running = false
    @Volatile private var lastHeardNs = 0L
    @Volatile private var attachedAtNs = 0L
    @Volatile private var manualTarget: InetSocketAddress? = null
    @Volatile private var ignoredSession: Long = -1

    fun start() {
        discSocket = udpSocket(discoveryPort)
        dataSocket = udpSocket(0)
        running = true
        thread("sync-rcv-disc") { discoveryLoop() }
        thread("sync-rcv-data") { dataLoop() }
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

    /** Connect to a source by IP when broadcast discovery is blocked by the network. */
    fun connectManual(host: String, port: Int = Proto.CONTROL_PORT) {
        val addr = InetSocketAddress(InetAddress.getByName(host), port)
        manualTarget = addr
        sendTo(Hello(0, name), addr)
        sendTo(Ping(0, clock()), addr)
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
        sendTo(Hello(info.sessionId, name), info.address)
        listener?.onSourceChanged(info)
    }

    private fun detach(notify: Boolean) {
        val had = source != null
        source = null
        engine = null
        if (notify && had) listener?.onSourceChanged(null)
    }

    private fun sendTo(msg: Msg, to: InetSocketAddress) {
        try {
            val buf = Wire.newBuffer()
            val len = Wire.encode(msg, buf)
            dataSocket.send(DatagramPacket(buf.array(), len, to))
        } catch (_: Exception) {
        }
    }

    private fun discoveryLoop() {
        val rx = ByteArray(Proto.MAX_DATAGRAM)
        val pkt = DatagramPacket(rx, rx.size)
        while (running) {
            try {
                pkt.setData(rx, 0, rx.size)
                discSocket.receive(pkt)
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            val b = Wire.decode(pkt.data, pkt.offset, pkt.length) as? Beacon ?: continue
            if (b.sessionId == ignoredSession) continue
            val current = source
            if (current == null || (current.sessionId != b.sessionId && clock() - lastHeardNs > STALE_NS)) {
                attach(SourceInfo(InetSocketAddress(pkt.address, b.controlPort), b.sessionId, b.name, b.sampleRate, b.channels))
            }
        }
    }

    private fun dataLoop() {
        val rx = ByteArray(Proto.MAX_DATAGRAM)
        val pkt = DatagramPacket(rx, rx.size)
        while (running) {
            try {
                pkt.setData(rx, 0, rx.size)
                dataSocket.receive(pkt)
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            val t4 = clock()
            val msg = Wire.decode(pkt.data, pkt.offset, pkt.length) ?: continue
            var s = source
            if (s == null) {
                // Manual connection: adopt the session from the first audio packet.
                val manual = manualTarget
                if (msg is Audio && manual != null && pkt.address == manual.address && msg.sessionId != ignoredSession) {
                    attach(SourceInfo(InetSocketAddress(pkt.address, pkt.port), msg.sessionId, manual.address.hostAddress ?: "?", msg.sampleRate, msg.channels))
                    s = source
                } else continue
            }
            if (s == null || msg.sessionId != s.sessionId) continue
            lastHeardNs = t4
            when (msg) {
                is Pong -> clockSync.addExchange(msg.t1, msg.t2, msg.t3, t4)
                is Audio -> engine?.onAudio(msg.framePos, msg.ptsNs, msg.pcm, msg.frames, msg.baseDelayMs)
                is Bye -> {
                    ignoredSession = msg.sessionId
                    detach(notify = true)
                    listener?.onSourceStopped()
                }
                else -> Unit
            }
        }
    }

    private fun pingLoop() {
        var lastHello = 0L
        while (running) {
            val s = source
            val now = clock()
            if (s != null) {
                if (now - lastHeardNs > LOST_NS) {
                    detach(notify = true)
                } else {
                    sendTo(Ping(s.sessionId, clock()), s.address)
                    if (now - lastHello > 2_000_000_000L) { sendTo(Hello(s.sessionId, name), s.address); lastHello = now }
                }
            } else {
                manualTarget?.let { if (now - lastHello > 1_000_000_000L) { sendTo(Hello(0, name), it); lastHello = now } }
            }
            // Fast pings while locking the clock, then a relaxed rate to track drift.
            val fast = s != null && now - attachedAtNs < 15_000_000_000L
            try { Thread.sleep(if (fast) 100 else 300) } catch (_: InterruptedException) { break }
        }
    }

    companion object {
        const val STALE_NS = 3_000_000_000L
        const val LOST_NS = 4_000_000_000L
    }
}
