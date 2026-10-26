package com.lucio.guzzobot

import com.lucio.guzzobot.data.crash.CrashReport
import com.lucio.guzzobot.data.crash.CrashStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CrashReportTest {

    private fun report(error: Throwable) = CrashReport.compose(
        error = error,
        version = "6.3 (34)",
        device = "Xiaomi 13T",
        android = "14 (API 34)",
        moment = "2026-10-26 10:00:00",
        thread = "main",
    )

    @Test
    fun `il rapporto ha intestazione e traccia`() {
        val text = report(IllegalStateException("rotta assente"))
        assertTrue(text.startsWith("GUZZOBOT 6.3 (34)"))
        assertTrue("Xiaomi 13T" in text)
        assertTrue("java.lang.IllegalStateException: rotta assente" in text)
        assertTrue("CrashReportTest" in text)
    }

    @Test
    fun `il titolo è la causa più profonda`() {
        val error = RuntimeException("involucro", IllegalArgumentException("destinazione voice non trovata"))
        assertEquals(
            "java.lang.IllegalArgumentException: destinazione voice non trovata",
            CrashReport.headline(report(error)),
        )
    }

    @Test
    fun `senza causa il titolo è l'eccezione`() {
        assertEquals(
            "java.lang.NullPointerException: niente",
            CrashReport.headline(report(NullPointerException("niente"))),
        )
    }

    @Test
    fun `una traccia enorme viene tagliata`() {
        fun deep(n: Int): Throwable = if (n == 0) Error("fondo") else RuntimeException("livello $n", deep(n - 1))
        assertTrue(report(deep(400)).length <= CrashReport.MAX_LENGTH)
    }

    @Test
    fun `il file si scrive, si legge e si cancella`() {
        val dir = Files.createTempDirectory("crash").toFile()
        val store = CrashStore(dir.resolve("crash/ultimo.txt"))
        assertNull(store.read())
        store.save("boom")
        assertEquals("boom", store.read())
        store.clear()
        assertNull(store.read())
        dir.deleteRecursively()
    }
}
