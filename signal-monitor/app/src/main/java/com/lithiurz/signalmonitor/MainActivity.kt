package com.lithiurz.signalmonitor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val UPDATE_INTERVAL_MS = 2000L

        /** Le celle viste restano in elenco per questo tempo dopo l'ultimo avvistamento. */
        const val HISTORY_WINDOW_MS = 5 * 60 * 1000L
    }

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var scanner: NetworkScanner
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ITALY)

    private class OperatorCard(
        val root: View,
        val dbmView: TextView,
        val qualityView: TextView,
        val techView: TextView,
        val progressBar: ProgressBar,
    )

    /** Cella osservata, con il momento dell'ultimo avvistamento. */
    private class Seen(val reading: CellReading, val timestamp: Long)

    private val cards = mutableMapOf<Operator, OperatorCard>()
    private val history = LinkedHashMap<String, Seen>()
    private var scanning = false
    private var lastRawCellCount = 0

    private lateinit var lastUpdateView: TextView
    private lateinit var detailView: TextView
    private lateinit var scanButton: Button
    private lateinit var scanStatusView: TextView

    private val updateRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        scanner = NetworkScanner(this)
        lastUpdateView = findViewById(R.id.last_update)
        detailView = findViewById(R.id.cells_detail)
        scanButton = findViewById(R.id.scan_button)
        scanStatusView = findViewById(R.id.scan_status)

        scanButton.setOnClickListener { if (scanning) stopScan(getString(R.string.scan_stopped)) else startScan() }

        val container = findViewById<LinearLayout>(R.id.operators_container)
        val inflater = LayoutInflater.from(this)
        val colors = mapOf(
            Operator.TIM to getColor(R.color.tim),
            Operator.VODAFONE to getColor(R.color.vodafone),
            Operator.WIND to getColor(R.color.wind),
        )
        for (op in Operator.entries) {
            val view = inflater.inflate(R.layout.operator_card, container, false)
            val nameView = view.findViewById<TextView>(R.id.operator_name)
            nameView.text = op.displayName
            nameView.setTextColor(colors.getValue(op))
            val progress = view.findViewById<ProgressBar>(R.id.signal_progress)
            progress.progressTintList = ColorStateList.valueOf(colors.getValue(op))
            cards[op] = OperatorCard(
                root = view,
                dbmView = view.findViewById(R.id.signal_dbm),
                qualityView = view.findViewById(R.id.signal_quality),
                techView = view.findViewById(R.id.signal_tech),
                progressBar = progress,
            )
            container.addView(view)
        }

        if (!hasPermissions()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE),
                PERMISSION_REQUEST,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
        // Non lasciamo il modem in scansione quando l'app non è in primo piano.
        if (scanning) stopScan(getString(R.string.scan_stopped))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) refresh()
    }

    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // --- Scansione completa delle reti ---------------------------------------

    private fun startScan() {
        scanning = true
        scanButton.setText(R.string.scan_stop)
        scanStatusView.setText(R.string.scan_running)
        scanner.start { result ->
            when (result) {
                is NetworkScanner.Result.Cells -> {
                    remember(SignalReader.readCells(result.cells))
                    scanStatusView.text = getString(R.string.scan_found, result.cells.size)
                }
                NetworkScanner.Result.Completed -> stopScan(getString(R.string.scan_done))
                is NetworkScanner.Result.Failed -> stopScan(getString(R.string.scan_failed, result.reason))
            }
        }
    }

    private fun stopScan(message: String) {
        scanner.stop()
        scanning = false
        scanButton.setText(R.string.scan_start)
        scanStatusView.text = message
    }

    // --- Lettura periodica ---------------------------------------------------

    private fun refresh() {
        if (!hasPermissions()) {
            lastUpdateView.text = getString(R.string.permission_needed)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.requestCellInfoUpdate(
                    mainExecutor,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) =
                            render(cellInfo.ifEmpty { cachedCellInfo() })

                        override fun onError(errorCode: Int, detail: Throwable?) =
                            render(cachedCellInfo())
                    },
                )
            } else {
                render(cachedCellInfo())
            }
        } catch (e: SecurityException) {
            lastUpdateView.text = getString(R.string.permission_needed)
        }
    }

    private fun cachedCellInfo(): List<CellInfo> = try {
        telephonyManager.allCellInfo ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun render(cells: List<CellInfo>) {
        lastRawCellCount = cells.size
        val current = (SignalReader.readCells(cells) + SignalReader.readServing(this))
            .distinctBy { keyOf(it) }
        remember(current)
        updateUi(current)
    }

    /** Chiave stabile di una cella: operatore + tecnologia + canale radio. */
    private fun keyOf(r: CellReading): String {
        val who = r.operator?.name ?: "${r.mcc ?: "?"}-${r.mnc ?: "?"}"
        return "$who|${r.tech}|${r.channel ?: "-"}"
    }

    private fun remember(readings: List<CellReading>) {
        val now = SystemClock.elapsedRealtime()
        readings.forEach { history[keyOf(it)] = Seen(it, now) }
        history.entries.removeAll { now - it.value.timestamp > HISTORY_WINDOW_MS }
    }

    // --- Interfaccia ---------------------------------------------------------

    private fun updateUi(current: List<CellReading>) {
        val now = SystemClock.elapsedRealtime()
        val visibleKeys = current.map { keyOf(it) }.toSet()
        val best = SignalReader.bestPerOperator(current)

        for ((op, card) in cards) {
            val live = best[op]
            // Se l'operatore non è visibile adesso, mostriamo l'ultimo dato raccolto.
            val recalled = if (live == null) {
                history.values
                    .filter { it.reading.operator == op }
                    .maxByOrNull { it.timestamp }
            } else {
                null
            }
            val reading = live ?: recalled?.reading

            if (reading == null) {
                card.root.alpha = 1f
                card.dbmView.text = getString(R.string.no_signal_dbm)
                card.qualityView.text = getString(R.string.quality_absent)
                card.techView.text = ""
                card.progressBar.progress = 0
                continue
            }

            card.root.alpha = if (live == null) 0.55f else 1f
            card.dbmView.text = getString(R.string.dbm_format, reading.dbm)
            card.qualityView.text = qualityLabel(reading.dbm)
            card.techView.text = when {
                live == null -> getString(R.string.tech_recalled, reading.tech, ageOf(now, recalled!!.timestamp))
                reading.registered -> getString(R.string.tech_registered, reading.tech)
                reading.estimated -> getString(R.string.tech_estimated, reading.tech)
                else -> reading.tech
            }
            card.progressBar.progress = dbmToPercent(reading.dbm)
        }

        lastUpdateView.text =
            getString(R.string.last_update, timeFormat.format(Date()), lastRawCellCount)
        detailView.text = buildDetailText(now, visibleKeys)
    }

    private fun buildDetailText(now: Long, visibleKeys: Set<String>): String {
        if (history.isEmpty()) {
            return if (!isLocationEnabled()) getString(R.string.location_off) else getString(R.string.no_cells)
        }
        return history.entries
            .sortedWith(compareByDescending<Map.Entry<String, Seen>> { it.key in visibleKeys }
                .thenByDescending { it.value.reading.dbm })
            .joinToString("\n") { (key, seen) ->
                val r = seen.reading
                val name = (r.operator?.displayName ?: "${r.mcc ?: "?"}-${r.mnc ?: "?"}") +
                    if (r.estimated) "*" else ""
                val ch = r.channel?.let { " ch$it" }.orEmpty()
                val suffix = when {
                    key !in visibleKeys -> " (${ageOf(now, seen.timestamp)})"
                    r.registered -> " ●"
                    else -> ""
                }
                "%-15s %-7s %4d dBm%s%s".format(Locale.ITALY, name, r.tech, r.dbm, ch, suffix)
            }
    }

    private fun ageOf(now: Long, timestamp: Long): String {
        val seconds = ((now - timestamp) / 1000).toInt()
        return if (seconds < 60) {
            getString(R.string.seen_seconds_ago, seconds)
        } else {
            getString(R.string.seen_minutes_ago, seconds / 60)
        }
    }

    private fun qualityLabel(dbm: Int): String = getString(
        when {
            dbm >= -85 -> R.string.quality_excellent
            dbm >= -95 -> R.string.quality_good
            dbm >= -105 -> R.string.quality_fair
            else -> R.string.quality_weak
        }
    )

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /** Mappa il range utile -120..-60 dBm su 0..100%. */
    private fun dbmToPercent(dbm: Int): Int = ((dbm + 120) * 100 / 60).coerceIn(0, 100)
}
