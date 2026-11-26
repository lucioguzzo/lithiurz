package com.lucio.syncaudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

class ProtocolTest {
    @Test
    fun roundTripAllMessages() {
        val buf = Wire.newBuffer()
        val msgs = listOf(
            Beacon(42, 47811, 48000, 2, "Pixel 8 – cucina"),
            Hello(42, "Galaxy"),
            Bye(42, true),
            Ping(42, 123456789L),
            Pong(42, 1, 2, 3),
        )
        for (m in msgs) {
            val len = Wire.encode(m, buf)
            assertEquals(m, Wire.decode(buf.array(), 0, len))
        }
        val pcm = ShortArray(480) { (it * 37 - 9000).toShort() }
        val len = Wire.encodeAudio(buf, 7, 99, 1_000_000L, 555L, 48000, 2, 80, pcm, 240)
        val a = Wire.decode(buf.array(), 0, len) as Audio
        assertEquals(99, a.seq); assertEquals(1_000_000L, a.framePos); assertEquals(555L, a.ptsNs)
        assertEquals(240, a.frames); assertEquals(80, a.baseDelayMs)
        assertTrue(pcm.contentEquals(a.pcm))
        assertTrue("audio packet must fit one Ethernet/Wi-Fi MTU", len < 1472)
    }

    @Test
    fun rejectsGarbage() {
        assertEquals(null, Wire.decode(ByteArray(100) { it.toByte() }, 0, 100))
        assertEquals(null, Wire.decode(ByteArray(3), 0, 3))
    }
}

class ClockSyncTest {
    @Test
    fun estimatesOffsetAndSkewUnderAsymmetricJitter() {
        val rnd = Random(1)
        val offset0 = 987_654_321_000L // source clock started at a completely different time
        val skew = 40e-6                // source crystal 40 ppm fast
        fun src(local: Double) = (offset0 + local * (1 + skew)).roundToLong()

        val cs = ClockSync()
        var t = 1_000_000_000.0
        repeat(300) {
            val up = 1e6 + rnd.nextDouble() * 15e6 // 1..16 ms, heavy Wi-Fi jitter
            val down = 1e6 + rnd.nextDouble() * 15e6
            val t1 = t.roundToLong()
            val t2 = src(t + up)
            val t3 = t2 + 30_000
            val t4 = (t + up + 30_000 / (1 + skew) + down).roundToLong()
            cs.addExchange(t1, t2, t3, t4)
            t += 200e6
        }
        assertTrue(cs.synced)
        // Mapping error must be well below 1 ms (perceptual sync threshold is ~5-10 ms).
        val probe = t
        val err = (cs.localToSource(probe.roundToLong()) - src(probe)) / 1e6
        assertTrue("offset error $err ms", abs(err) < 1.5)
        // Skew is a secondary refinement (the playout loop absorbs drift); coarse is fine here.
        assertTrue("skew ${cs.skewPpm} ppm", abs(cs.skewPpm - 40.0) < 30.0)
        val back = cs.sourceToLocal(src(probe))
        assertTrue(abs(back - probe) / 1e6 < 1.5)
        println("clock: offset err %.3f ms, skew %.1f ppm".format(err, cs.skewPpm))
    }
}

class PlayoutEngineTest {
    /**
     * Full simulation: source with its own clock (offset + 50 ppm skew) captures a ramp at a
     * sample rate that is 30 ppm off; packets arrive with jitter and 2% loss; receiver output
     * clock runs at nominal 48 kHz. After convergence, what comes out of the receiver at time T
     * must be what the source captured at T - delay.
     */
    @Test
    fun receiverOutputIsTimeAlignedWithSource() {
        val sr = 48000
        val ch = 2
        val chunk = 240
        val rnd = Random(7)
        val offset0 = 5_000_000_000_000L
        val skew = 50e-6
        val captureRate = sr * (1 - 30e-6) // frames per local second
        fun src(local: Double) = (offset0 + local * (1 + skew)).roundToLong()
        // Continuous triangle wave, slope ±1 per frame: value error == timing error in frames.
        fun ramp(f: Long): Int { val m = (f % 60000).toInt(); return if (m < 30000) m - 15000 else 45000 - m }

        val cs = ClockSync()
        val engine = PlayoutEngine(sr, ch, cs)
        engine.baseDelayMs = 120

        // Pre-compute packet arrivals (local time) in order.
        data class Pkt(val arrive: Double, val framePos: Long, val pts: Long, val pcm: ShortArray)
        val pkts = ArrayDeque<Pkt>()
        val simSeconds = 40.0
        var f = 0L
        while (f / captureRate < simSeconds) {
            val captureLocal = (f + chunk) / captureRate * 1e9 // chunk is available after it is complete
            val ptsLocal = f / captureRate * 1e9 + (rnd.nextDouble() - 0.5) * 1e6 // ±0.5ms timestamp noise
            val pcm = ShortArray(chunk * ch) { i -> ramp(f + i / ch).toShort() }
            if (rnd.nextDouble() > 0.02) pkts.addLast(Pkt(captureLocal + 2e6 + rnd.nextDouble() * 20e6, f, src(ptsLocal), pcm))
            f += chunk
        }
        val sorted = ArrayDeque(pkts.sortedBy { it.arrive })

        var nextPing = 0.0
        val out = ShortArray(chunk * ch)
        val outputLatency = 60e6 // render happens 60 ms before the frames hit the speaker
        var present = 0.0
        var maxErrFrames = 0.0
        var checked = 0
        while (present < simSeconds * 1e9) {
            val now = present - outputLatency
            while (nextPing <= now) {
                val up = 1e6 + rnd.nextDouble() * 8e6; val down = 1e6 + rnd.nextDouble() * 8e6
                val t2 = src(nextPing + up)
                cs.addExchange(nextPing.roundToLong(), t2, t2 + 20_000, (nextPing + up + down + 20_000).roundToLong())
                nextPing += 200e6
            }
            while (sorted.isNotEmpty() && sorted.first().arrive <= now) {
                val p = sorted.removeFirst()
                engine.onAudio(p.framePos, p.pts, p.pcm, chunk, 120)
            }
            engine.render(out, chunk, present.roundToLong())
            if (present > 20e9) {
                // Expected source frame at this instant: captured at (present - delay).
                val expected = (present - 120e6) / 1e9 * captureRate
                val got = out[0].toInt()
                if (got != 0) {
                    val d = (got - ramp(expected.toLong())).toDouble()
                    maxErrFrames = maxOf(maxErrFrames, abs(d))
                    checked++
                }
            }
            present += chunk * 1e9 / sr
        }
        assertTrue("checked $checked", checked > 3000)
        val maxErrMs = maxErrFrames * 1000 / sr
        println("max alignment error after convergence: %.3f ms, resyncs=%d, lost frames=%d".format(maxErrMs, engine.resyncs, engine.missingFrames))
        assertTrue("alignment error $maxErrMs ms", maxErrMs < 1.5)
        assertTrue(engine.resyncs <= 3)
    }
}

class NetworkLoopbackTest {
    private fun freePort(): Int = DatagramSocket(0).use { it.localPort }

    @Test
    fun receiverDiscoversSourceSyncsClockAndGetsAudio() {
        val disc = freePort()
        val source = SyncSource("src", 48000, 2, { listOf(InetAddress.getLoopbackAddress()) }, controlPort = 0, discoveryPort = disc)
        val receiver = SyncReceiver("rcv", discoveryPort = disc)
        source.start(); receiver.start()
        try {
            val deadline = System.currentTimeMillis() + 8000
            val pcm = ShortArray(480) { 1000 }
            var frame = 0L
            while (System.currentTimeMillis() < deadline) {
                source.sendAudio(frame, System.nanoTime(), pcm, 240)
                frame += 240
                Thread.sleep(5)
                val e = receiver.engine
                if (e != null && receiver.clockSync.synced && e.packets > 50) break
            }
            assertNotNull("receiver attached", receiver.source)
            assertEquals(1, source.peerList.size)
            assertTrue("clock synced", receiver.clockSync.synced)
            // Same machine, same clock: estimated offset must be ~0.
            assertTrue("offset ${receiver.clockSync.offsetNs}", abs(receiver.clockSync.offsetNs) < 2_000_000)
            val out = ShortArray(480)
            val played = receiver.engine!!.render(out, 240, System.nanoTime() + 80_000_000L - 40_000_000L)
            assertTrue("audio rendered", played)
        } finally {
            receiver.stop(); source.stop()
        }
    }
}
