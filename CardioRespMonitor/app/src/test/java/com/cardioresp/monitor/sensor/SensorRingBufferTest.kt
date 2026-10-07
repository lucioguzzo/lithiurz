package com.cardioresp.monitor.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.concurrent.thread

class SensorRingBufferTest {

    @Test
    fun preservesOrderAndValues() {
        val rb = SensorRingBuffer(8)
        for (i in 0 until 5) rb.offer(i % 2, i * 10L, i.toFloat(), 0f, 0f)
        val ts = mutableListOf<Long>()
        rb.drain({ _, t, _, _, _ -> ts += t })
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L), ts)
        assertEquals(0, rb.size())
    }

    @Test
    fun countsOverflowWithDropNewest() {
        val rb = SensorRingBuffer(4)
        repeat(4) { rb.offer(0, it.toLong(), 0f, 0f, 0f) }
        assertFalse(rb.offer(0, 99L, 0f, 0f, 0f))
        assertEquals(1L, rb.overflowCount)
        val ts = mutableListOf<Long>()
        rb.drain({ _, t, _, _, _ -> ts += t })
        assertEquals(listOf(0L, 1L, 2L, 3L), ts)
    }

    @Test
    fun concurrentProducerConsumerLosesNothing() {
        val rb = SensorRingBuffer(1 shl 10)
        val total = 200_000
        val producer = thread {
            var i = 0
            while (i < total) if (rb.offer(0, i.toLong(), i.toFloat(), 0f, 0f)) i++ else Thread.yield()
        }
        var expected = 0L
        var received = 0
        while (received < total) {
            received += rb.drain({ _, t, x, _, _ ->
                assertEquals(expected, t)
                assertEquals(expected.toFloat(), x)
                expected++
            })
        }
        producer.join()
        assertEquals(total.toLong(), expected)
    }
}
