package com.cardioresp.monitor.storage

import com.cardioresp.monitor.domain.model.SensorKind
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Log GREZZO di tutti gli eventi, esattamente come consegnati dal framework, senza
 * interpolazione. È la "fonte di verità" per la validazione scientifica: permette di
 * rifare offline l'allineamento acc/gyro con qualunque metodo e di verificare il jitter.
 *
 * Formato: sensor,timestamp_android_ns,timestamp_unix_ms,x,y,z
 */
class RawEventWriter(file: File) : Closeable {
    private val stream = FileOutputStream(file)
    private val out = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8), 256 * 1024)
    private val sb = StringBuilder(96)

    init {
        out.write("sensor,timestamp_android_ns,timestamp_unix_ms,x,y,z")
        out.newLine()
    }

    fun write(kind: SensorKind, tNs: Long, unixMs: Long, x: Float, y: Float, z: Float) {
        sb.setLength(0)
        sb.append(kind.label).append(',').append(tNs).append(',').append(unixMs).append(',')
            .append(x).append(',').append(y).append(',').append(z).append('\n')
        out.append(sb)
    }

    fun flush() = out.flush()

    override fun close() {
        out.flush()
        stream.fd.sync()
        out.close()
    }
}
