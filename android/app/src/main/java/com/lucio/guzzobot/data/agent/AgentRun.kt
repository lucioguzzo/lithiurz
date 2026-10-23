package com.lucio.guzzobot.data.agent

/** I limiti entro cui l'agente può muoversi da solo. */
data class AgentLimits(
    /** Chiamate a strumenti complessive. */
    val maxSteps: Int = 60,
    /** Giri di modello: ogni giro può chiedere più strumenti. */
    val maxRounds: Int = 40,
    val maxMinutes: Int = 25,
    /** Errori di fila oltre i quali è inutile insistere. */
    val maxConsecutiveErrors: Int = 4,
    /** Quante volte lo si richiama all'ordine se smette di agire senza concludere. */
    val maxNudges: Int = 3,
)

enum class AgentOutcome {
    RUNNING,

    /** Ha dichiarato di aver finito. */
    FINISHED,

    /** Si è fermato e chiede qualcosa. */
    BLOCKED,

    /** L'utente ha interrotto. */
    STOPPED,

    /** Ha esaurito passi, giri o tempo. */
    BUDGET,

    /** Troppi errori di fila. */
    FAILING,
}

/**
 * Lo stato di una corsa dell'agente: contabilità dei passi e condizioni di
 * uscita. Niente rete e niente Android, così è verificabile da sola.
 */
class AgentRun(
    val goal: String,
    val limits: AgentLimits = AgentLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val startedAt = clock()

    var steps: Int = 0
        private set
    var rounds: Int = 0
        private set
    var consecutiveErrors: Int = 0
        private set
    var nudges: Int = 0
        private set
    var outcome: AgentOutcome = AgentOutcome.RUNNING
        private set
    var summary: String? = null
        private set

    val running: Boolean get() = outcome == AgentOutcome.RUNNING

    val elapsedMinutes: Long get() = (clock() - startedAt) / 60_000

    /** Riga mostrata nella barra di avanzamento. */
    val progress: String
        get() = "passo $steps/${limits.maxSteps} · ${elapsedMinutes} min"

    /** Da chiamare prima di ogni giro: false se il budget è finito. */
    fun beginRound(): Boolean {
        if (!running) return false
        if (rounds >= limits.maxRounds || steps >= limits.maxSteps ||
            elapsedMinutes >= limits.maxMinutes
        ) {
            outcome = AgentOutcome.BUDGET
            return false
        }
        rounds++
        return true
    }

    fun onToolResult(failed: Boolean) {
        steps++
        consecutiveErrors = if (failed) consecutiveErrors + 1 else 0
        if (consecutiveErrors >= limits.maxConsecutiveErrors) {
            outcome = AgentOutcome.FAILING
        }
    }

    /** Il modello ha smesso di agire senza dichiarare la fine: lo si richiama. */
    fun nudge(): Boolean {
        if (nudges >= limits.maxNudges) {
            outcome = AgentOutcome.BUDGET
            return false
        }
        nudges++
        return true
    }

    fun finish(summary: String) {
        this.summary = summary.ifBlank { "Compito concluso." }
        outcome = AgentOutcome.FINISHED
    }

    fun blocked(reason: String) {
        summary = reason.ifBlank { "Serve una tua indicazione per proseguire." }
        outcome = AgentOutcome.BLOCKED
    }

    fun stop() {
        if (running) outcome = AgentOutcome.STOPPED
    }

    /** Il messaggio di chiusura che finisce nella conversazione. */
    fun closingNote(): String = when (outcome) {
        AgentOutcome.FINISHED -> summary.orEmpty()
        AgentOutcome.BLOCKED -> "Mi fermo qui: ${summary.orEmpty()}"
        AgentOutcome.STOPPED -> "Interrotto dopo $steps passi."
        AgentOutcome.BUDGET ->
            "Budget esaurito dopo $steps passi e $elapsedMinutes minuti. " +
                "Dimmi se vuoi che continui."
        AgentOutcome.FAILING ->
            "Mi fermo dopo $consecutiveErrors errori di fila: qualcosa non torna."
        AgentOutcome.RUNNING -> ""
    }
}
