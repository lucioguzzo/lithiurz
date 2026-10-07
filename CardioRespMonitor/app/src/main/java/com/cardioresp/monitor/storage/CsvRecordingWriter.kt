package com.cardioresp.monitor.storage

import com.cardioresp.monitor.domain.model.SensorSample
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Writer CSV ad alte prestazioni per i campioni combinati.
 *
 * - Scrive nello storage PRIVATO dell'app (nessuna latenza SAF/ContentProvider durante
 *   l'acquisizione); l'export verso una destinazione scelta dall'utente avviene dopo, via SAF.
 * - BufferedWriter da 256 KiB + StringBuilder riutilizzato: nessuna `String.format`
 *   (lenta e dipendente dal Locale: in italiano scriverebbe "9,81" rompendo il CSV).
 * - Separatore ',' e punto decimale '.', header standard: leggibile da pandas/MATLAB/R.
 * - Valori mancanti scritti come `NaN`.
 */
class CsvRecordingWriter(file: File) : Closeable {

    private val stream = FileOutputStream(file)
    private val out = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8), BUFFER_SIZE)
    private val sb = StringBuilder(128)

    var rowsWritten = 0L
        private set

    init {
        out.write(HEADER)
        out.newLine()
    }

    fun write(s: SensorSample) {
        sb.setLength(0)
        sb.append(s.sensorTimestampNs).append(',')
            .append(s.unixTimestampMs).append(',')
            .append(s.accX).append(',').append(s.accY).append(',').append(s.accZ).append(',')
            .append(s.gyroX).append(',').append(s.gyroY).append(',').append(s.gyroZ)
            .append('\n')
        out.append(sb)
        rowsWritten++
    }

    /** Svuota il buffer verso il kernel (chiamato periodicamente: limita la perdita in caso di crash). */
    fun flush() = out.flush()

    override fun close() {
        out.flush()
        stream.fd.sync() // garantisce che i dati siano su memoria persistente
        out.close()
    }

    companion object {
        const val HEADER = "timestamp_android_ns,timestamp_unix_ms,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z"
        private const val BUFFER_SIZE = 256 * 1024
    }
}
