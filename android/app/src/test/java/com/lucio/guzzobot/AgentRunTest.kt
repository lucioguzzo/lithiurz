package com.lucio.guzzobot

import com.lucio.guzzobot.data.agent.AgentLimits
import com.lucio.guzzobot.data.agent.AgentOutcome
import com.lucio.guzzobot.data.agent.AgentRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il tetto di sicurezza dell'agente: va avanti da solo, ma passi, giri, tempo
 * ed errori di fila hanno un limite, e ogni uscita ha il suo messaggio.
 */
class AgentRunTest {

    private var now = 0L
    private val clock = { now }

    private fun run(limits: AgentLimits = AgentLimits()) =
        AgentRun("sistemare i test", limits, clock)

    @Test
    fun `si ferma quando finiscono i passi`() {
        val agent = run(AgentLimits(maxSteps = 3))
        repeat(3) {
            assertTrue(agent.beginRound())
            agent.onToolResult(failed = false)
        }
        assertFalse(agent.beginRound())
        assertEquals(AgentOutcome.BUDGET, agent.outcome)
        assertTrue(agent.closingNote().contains("Budget esaurito"))
    }

    @Test
    fun `si ferma quando finisce il tempo`() {
        val agent = run(AgentLimits(maxMinutes = 10))
        assertTrue(agent.beginRound())
        now += 11 * 60_000
        assertFalse(agent.beginRound())
        assertEquals(AgentOutcome.BUDGET, agent.outcome)
    }

    @Test
    fun `troppi errori di fila lo fermano, uno buono azzera il conto`() {
        val agent = run(AgentLimits(maxConsecutiveErrors = 3))
        agent.beginRound()
        agent.onToolResult(failed = true)
        agent.onToolResult(failed = true)
        agent.onToolResult(failed = false)
        assertTrue(agent.running)
        agent.onToolResult(failed = true)
        agent.onToolResult(failed = true)
        assertTrue(agent.running)
        agent.onToolResult(failed = true)
        assertEquals(AgentOutcome.FAILING, agent.outcome)
        assertTrue(agent.closingNote().contains("errori di fila"))
    }

    @Test
    fun `i richiami all'ordine sono contati`() {
        val agent = run(AgentLimits(maxNudges = 2))
        assertTrue(agent.nudge())
        assertTrue(agent.nudge())
        assertFalse(agent.nudge())
        assertEquals(AgentOutcome.BUDGET, agent.outcome)
    }

    @Test
    fun `conclusione e blocco riportano il messaggio del modello`() {
        val finished = run()
        finished.finish("Aggiornati tre file e CI verde.")
        assertEquals(AgentOutcome.FINISHED, finished.outcome)
        assertEquals("Aggiornati tre file e CI verde.", finished.closingNote())

        val blocked = run()
        blocked.blocked("Mi serve il nome del branch di destinazione.")
        assertEquals(AgentOutcome.BLOCKED, blocked.outcome)
        assertTrue(blocked.closingNote().contains("branch di destinazione"))
    }

    @Test
    fun `una corsa conclusa non riparte`() {
        val agent = run()
        agent.finish("fatto")
        assertFalse(agent.beginRound())
        agent.stop()
        assertEquals(AgentOutcome.FINISHED, agent.outcome)
    }

    @Test
    fun `l'interruzione dell'utente ha la precedenza su tutto il resto`() {
        val agent = run()
        agent.beginRound()
        agent.onToolResult(failed = false)
        agent.stop()
        assertEquals(AgentOutcome.STOPPED, agent.outcome)
        assertTrue(agent.closingNote().contains("Interrotto"))
    }
}
