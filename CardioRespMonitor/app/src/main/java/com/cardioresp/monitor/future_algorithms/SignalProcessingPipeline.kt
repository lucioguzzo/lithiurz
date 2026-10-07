package com.cardioresp.monitor.future_algorithms

import android.util.Log
import com.cardioresp.monitor.domain.model.SensorSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Smista i campioni combinati verso i [SignalProcessor] registrati, su un thread DEDICATO.
 *
 * Qui usiamo un `Channel` (e non il ring buffer) perché:
 * + semantica suspend naturale per il consumatore, codice semplice per chi scrive algoritmi;
 * + l'allocazione di un `SensorSample` per campione è irrilevante a questo livello (~200–500/s);
 * + `trySend` non blocca MAI il produttore (thread della pipeline di registrazione):
 *   se gli algoritmi sono troppo lenti la coda si riempie e i campioni in eccesso vengono
 *   scartati SOLO per gli algoritmi (contatore [drops]); la registrazione CSV non ne risente.
 */
class SignalProcessingPipeline(private val processors: List<SignalProcessor>) {

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "SignalProcessing") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var channel: Channel<SensorSample>? = null
    private var job: Job? = null
    private val dropCounter = AtomicLong(0)

    val drops: Long get() = dropCounter.get()
    val isEmpty: Boolean get() = processors.isEmpty()

    fun start(context: ProcessingContext) {
        if (processors.isEmpty()) return
        dropCounter.set(0)
        val ch = Channel<SensorSample>(capacity = QUEUE_CAPACITY)
        channel = ch
        job = scope.launch {
            processors.forEach { it.onSessionStart(context) }
            for (sample in ch) {
                for (p in processors) {
                    try {
                        p.process(sample)
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore nel processore ${p.name}", e) // un algoritmo difettoso non ferma gli altri
                    }
                }
            }
            processors.forEach { it.onSessionEnd() }
        }
    }

    /** Non bloccante: chiamato dal thread della pipeline di acquisizione. */
    fun submit(sample: SensorSample) {
        val ch = channel ?: return
        if (ch.trySend(sample).isFailure) dropCounter.incrementAndGet()
    }

    suspend fun stop() {
        channel?.close()
        job?.join()
        channel = null
        job = null
    }

    private companion object {
        const val TAG = "SignalProcessing"
        const val QUEUE_CAPACITY = 16_384 // ~30 s a 500 Hz
    }
}
