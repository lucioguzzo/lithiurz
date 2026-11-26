package com.lucio.syncaudio.core

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire protocol between phones. Everything travels over UDP on the local network
 * (same Wi-Fi, or one phone acting as hotspot for the other).
 *
 * Every datagram starts with: MAGIC(4) VERSION(1) TYPE(1) SESSION_ID(8).
 * All integers are little-endian. PCM is 16-bit signed, interleaved.
 */
object Proto {
    const val MAGIC = 0x4C535943 // "CYSL"
    const val VERSION = 1
    const val DISCOVERY_PORT = 47810
    const val CONTROL_PORT = 47811
    const val MAX_DATAGRAM = 2048
    const val HEADER_SIZE = 14

    const val T_BEACON = 1
    const val T_HELLO = 2
    const val T_BYE = 3
    const val T_PING = 4
    const val T_PONG = 5
    const val T_AUDIO = 6
}

sealed interface Msg {
    val sessionId: Long
}

/** Broadcast by the source ~1/s so receivers can find it. */
data class Beacon(
    override val sessionId: Long,
    val controlPort: Int,
    val sampleRate: Int,
    val channels: Int,
    val name: String,
) : Msg

/** Receiver -> source: "send me the audio". Also used as keep-alive. */
data class Hello(override val sessionId: Long, val name: String) : Msg

/** Either side leaves. [takeover] = "I want to become the source, please switch to receiver". */
data class Bye(override val sessionId: Long, val takeover: Boolean) : Msg

/** NTP-style clock exchange. t1 = receiver send time (receiver clock). */
data class Ping(override val sessionId: Long, val t1: Long) : Msg

/** t2 = source receive time, t3 = source send time (both source clock). */
data class Pong(override val sessionId: Long, val t1: Long, val t2: Long, val t3: Long) : Msg

/**
 * A chunk of captured audio.
 * [framePos] = index of the first frame since the session started (monotonic, gap = loss).
 * [ptsNs] = source monotonic clock time at which frame [framePos] was captured.
 * [baseDelayMs] = playout delay requested by the source (receivers add their own offset).
 */
class Audio(
    override val sessionId: Long,
    val seq: Int,
    val framePos: Long,
    val ptsNs: Long,
    val sampleRate: Int,
    val channels: Int,
    val baseDelayMs: Int,
    val frames: Int,
    val pcm: ShortArray,
) : Msg

object Wire {
    private const val MAX_NAME_BYTES = 64

    fun newBuffer(): ByteBuffer = ByteBuffer.allocate(Proto.MAX_DATAGRAM).order(ByteOrder.LITTLE_ENDIAN)

    /** Encodes [msg] into [buf] (cleared first). Returns the encoded length. */
    fun encode(msg: Msg, buf: ByteBuffer): Int {
        buf.clear()
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(Proto.MAGIC)
        buf.put(Proto.VERSION.toByte())
        buf.put(typeOf(msg).toByte())
        buf.putLong(msg.sessionId)
        when (msg) {
            is Beacon -> {
                buf.putShort(msg.controlPort.toShort())
                buf.putInt(msg.sampleRate)
                buf.put(msg.channels.toByte())
                putString(buf, msg.name)
            }
            is Hello -> putString(buf, msg.name)
            is Bye -> buf.put(if (msg.takeover) 1 else 0)
            is Ping -> buf.putLong(msg.t1)
            is Pong -> {
                buf.putLong(msg.t1); buf.putLong(msg.t2); buf.putLong(msg.t3)
            }
            is Audio -> {
                buf.putInt(msg.seq)
                buf.putLong(msg.framePos)
                buf.putLong(msg.ptsNs)
                buf.putInt(msg.sampleRate)
                buf.put(msg.channels.toByte())
                buf.putShort(msg.baseDelayMs.toShort())
                buf.putShort(msg.frames.toShort())
                val samples = msg.frames * msg.channels
                buf.asShortBuffer().put(msg.pcm, 0, samples)
                buf.position(buf.position() + samples * 2)
            }
        }
        return buf.position()
    }

    /** Fast path for the hot audio loop: encodes without allocating an [Audio] object. */
    fun encodeAudio(
        buf: ByteBuffer, sessionId: Long, seq: Int, framePos: Long, ptsNs: Long,
        sampleRate: Int, channels: Int, baseDelayMs: Int, pcm: ShortArray, frames: Int,
    ): Int {
        buf.clear()
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(Proto.MAGIC)
        buf.put(Proto.VERSION.toByte())
        buf.put(Proto.T_AUDIO.toByte())
        buf.putLong(sessionId)
        buf.putInt(seq)
        buf.putLong(framePos)
        buf.putLong(ptsNs)
        buf.putInt(sampleRate)
        buf.put(channels.toByte())
        buf.putShort(baseDelayMs.toShort())
        buf.putShort(frames.toShort())
        val samples = frames * channels
        buf.asShortBuffer().put(pcm, 0, samples)
        buf.position(buf.position() + samples * 2)
        return buf.position()
    }

    /** Returns null for anything that is not a valid message of ours. */
    fun decode(data: ByteArray, offset: Int, length: Int): Msg? {
        if (length < Proto.HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            if (buf.int != Proto.MAGIC) return null
            if (buf.get().toInt() != Proto.VERSION) return null
            val type = buf.get().toInt()
            val session = buf.long
            when (type) {
                Proto.T_BEACON -> Beacon(
                    sessionId = session,
                    controlPort = buf.short.toInt() and 0xFFFF,
                    sampleRate = buf.int,
                    channels = buf.get().toInt(),
                    name = getString(buf),
                )
                Proto.T_HELLO -> Hello(session, getString(buf))
                Proto.T_BYE -> Bye(session, buf.get().toInt() != 0)
                Proto.T_PING -> Ping(session, buf.long)
                Proto.T_PONG -> Pong(session, buf.long, buf.long, buf.long)
                Proto.T_AUDIO -> {
                    val seq = buf.int
                    val framePos = buf.long
                    val pts = buf.long
                    val sr = buf.int
                    val ch = buf.get().toInt()
                    val delay = buf.short.toInt()
                    val frames = buf.short.toInt()
                    if (ch !in 1..8 || frames <= 0 || sr <= 0) return null
                    val samples = frames * ch
                    if (buf.remaining() < samples * 2) return null
                    val pcm = ShortArray(samples)
                    buf.asShortBuffer().get(pcm)
                    Audio(session, seq, framePos, pts, sr, ch, delay, frames, pcm)
                }
                else -> null
            }
        } catch (e: BufferUnderflowException) {
            null
        }
    }

    private fun typeOf(msg: Msg) = when (msg) {
        is Beacon -> Proto.T_BEACON
        is Hello -> Proto.T_HELLO
        is Bye -> Proto.T_BYE
        is Ping -> Proto.T_PING
        is Pong -> Proto.T_PONG
        is Audio -> Proto.T_AUDIO
    }

    private fun putString(buf: ByteBuffer, s: String) {
        var bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_NAME_BYTES) bytes = bytes.copyOf(MAX_NAME_BYTES)
        buf.put(bytes.size.toByte())
        buf.put(bytes)
    }

    private fun getString(buf: ByteBuffer): String {
        val n = buf.get().toInt() and 0xFF
        val bytes = ByteArray(n)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
