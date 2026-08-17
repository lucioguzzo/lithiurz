package com.lithiurz.signalmonitor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
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
        const val HISTORY_WINDOW_DRIVE_MS = 15 * 60 * 1000L

        /**
         * Intervallo minimo tra due richieste di aggiornamento celle al modem.
         * In modalità drive test si campiona al massimo ritmo utile per cogliere
         * le celle che compaiono solo per pochi istanti.
         */
        const val CELL_INFO_REQUEST_INTERVAL_MS = 5_000L
        const val CELL_INFO_REQUEST_INTERVAL_DRIVE_MS = 2_000L

        /** Tolleranza prima di segnalare che il modem non restituisce celle. */
        const val NO_CELLS_GRACE_MS = 30_000L
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
    private var lastCellInfoRequest = 0L
    private var lastCellsSeenAt = 0L
    private var driveTest = false
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var warningAction: (() -> Unit)? = null

    private lateinit var lastUpdateView: TextView
    private lateinit var detailView: TextView
    private lateinit var scanButton: Button
    private lateinit var scanStatusView: TextView
    private lateinit var driveTestButton: Button
    private lateinit var warningBox: LinearLayout
    private lateinit var warningText: TextView
    private lateinit var warningButton: Button

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
        warningBox = findViewById(R.id.warning_box)
        warningText = findViewById(R.id.warning_text)
        warningButton = findViewById(R.id.warning_button)

        scanButton.setOnClickListener { if (scanning) stopScan(getString(R.string.scan_stopped)) else startScan() }
        warningButton.setOnClickListener { warningAction?.invoke() }

        driveTestButton = findViewById(R.id.drive_test_button)
        driveTestButton.setOnClickListener {
            driveTest = !driveTest
            lastCellInfoRequest = 0L
            updateDriveTestButton()
        }
        updateDriveTestButton()

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
        lastCellInfoRequest = 0L
        startListening()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
        stopListening()
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
            updateWarning(rawCells = 0)
            return
        }
        // L'elenco in cache è economico e non soggetto a limitazioni: lo leggiamo
        // ogni giro.
        render(cachedCellInfo())

        // requestCellInfoUpdate accende il ricevitore: Android la limita, e se
        // invocata troppo spesso risponde con una lista vuota. Una volta ogni
        // dieci secondi è sotto la soglia su tutti i dispositivi testati.
        val now = SystemClock.elapsedRealtime()
        val interval =
            if (driveTest) CELL_INFO_REQUEST_INTERVAL_DRIVE_MS else CELL_INFO_REQUEST_INTERVAL_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && now - lastCellInfoRequest >= interval) {
            lastCellInfoRequest = now
            try {
                telephonyManager.requestCellInfoUpdate(
                    mainExecutor,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            if (cellInfo.isNotEmpty()) render(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) = Unit
                    },
                )
            } catch (e: SecurityException) {
                lastUpdateView.text = getString(R.string.permission_needed)
            }
        }
    }

    /**
     * Ascolto push degli aggiornamenti di cella: il framework li consegna quando
     * cambiano, senza dipendere dal nostro polling.
     */
    private fun startListening() {
        if (!hasPermissions()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        if (cellInfo.isNotEmpty()) render(cellInfo)
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                        if (!cellInfo.isNullOrEmpty()) render(cellInfo)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
            }
        } catch (e: SecurityException) {
            // Senza permessi restiamo sul solo polling.
        }
    }

    private fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }

    private fun cachedCellInfo(): List<CellInfo> = try {
        telephonyManager.allCellInfo ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun render(cells: List<CellInfo>) {
        lastRawCellCount = cells.size
        if (cells.isNotEmpty()) lastCellsSeenAt = SystemClock.elapsedRealtime()
        val fromCells = SignalReader.readCells(cells)
        // La lettura della rete attiva serve a coprire le tecnologie che l'elenco
        // celle non riporta (tipicamente la portante NR in 5G NSA, o l'elenco vuoto).
        val coveredTechs = fromCells.filter { it.registered }.map { it.tech }.toSet()
        val serving = SignalReader.readServing(this).filterNot { it.tech in coveredTechs }
        val current = (fromCells + serving).distinctBy { keyOf(it) }
        remember(current)
        updateUi(current)
    }

    /**
     * Chiave stabile di una cella. Include il PCI: celle diverse sullo stesso
     * canale si distinguono solo per quello, e senza di esso finirebbero fuse
     * in una sola riga.
     */
    private fun keyOf(r: CellReading): String {
        val who = r.operator?.name ?: "${r.mcc ?: "?"}-${r.mnc ?: "?"}"
        return "$who|${r.tech}|${r.channel ?: "-"}|${r.pci ?: "-"}"
    }

    private fun remember(readings: List<CellReading>) {
        val now = SystemClock.elapsedRealtime()
        val window = if (driveTest) HISTORY_WINDOW_DRIVE_MS else HISTORY_WINDOW_MS
        readings.forEach { history[keyOf(it)] = Seen(it, now) }
        history.entries.removeAll { now - it.value.timestamp > window }

        // La lettura della rete attiva non porta canale né PCI: quando la stessa
        // cella è già presente con quei dettagli, la voce generica è un doppione.
        val detailed = history.values
            .filter { it.reading.channel != null }
            .mapTo(mutableSetOf()) { it.reading.operator to it.reading.tech }
        history.entries.removeAll { (_, seen) ->
            seen.reading.channel == null && (seen.reading.operator to seen.reading.tech) in detailed
        }
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
        updateWarning(lastRawCellCount)
    }

    /**
     * Se il modem non consegna celle il motivo è quasi sempre uno dei permessi
     * di posizione: lo diciamo esplicitamente, con la scorciatoia per risolverlo.
     */
    private fun updateWarning(rawCells: Int) {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        val problem: Triple<String, String, () -> Unit>? = when {
            !fine -> Triple(
                getString(R.string.warn_precise_location),
                getString(R.string.warn_open_app_settings),
                ::openAppSettings,
            )
            !isLocationEnabled() -> Triple(
                getString(R.string.warn_location_off),
                getString(R.string.warn_open_location_settings),
                ::openLocationSettings,
            )
            // Una singola lettura vuota è normale: segnaliamo solo se il modem
            // resta muto a lungo.
            rawCells == 0 && SystemClock.elapsedRealtime() - lastCellsSeenAt > NO_CELLS_GRACE_MS -> Triple(
                getString(R.string.warn_no_cells),
                getString(R.string.warn_open_app_settings),
                ::openAppSettings,
            )
            else -> null
        }

        if (problem == null) {
            warningBox.visibility = View.GONE
            warningAction = null
            return
        }
        warningBox.visibility = View.VISIBLE
        warningText.text = problem.first
        warningButton.text = problem.second
        warningAction = problem.third
    }

    private fun updateDriveTestButton() {
        driveTestButton.text = getString(
            if (driveTest) R.string.drive_test_on else R.string.drive_test_off
        )
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    private fun buildDetailText(now: Long, visibleKeys: Set<String>): String {
        if (history.isEmpty()) {
            return if (!isLocationEnabled()) getString(R.string.location_off) else getString(R.string.no_cells)
        }
        val rows = history.entries
            .sortedWith(compareByDescending<Map.Entry<String, Seen>> { it.key in visibleKeys }
                .thenByDescending { it.value.reading.dbm })
            .map { (key, seen) ->
                val r = seen.reading
                val name = (r.operator?.shortName ?: "?") + if (r.estimated) "*" else ""
                val marker = when {
                    key !in visibleKeys -> " ${compactAge(now, seen.timestamp)}"
                    r.registered -> " ●"
                    else -> ""
                }
                "%-9s %-5s %5d %-7s %-6s%s".format(
                    Locale.ITALY,
                    name,
                    r.band ?: r.tech,
                    r.dbm,
                    r.channel?.let { "ch$it" }.orEmpty(),
                    r.pci?.let { "p$it" }.orEmpty(),
                    marker,
                )
            }
        return (listOf(getString(R.string.cells_header)) + rows).joinToString("\n")
    }

    /** Età abbreviata, per non mandare a capo le righe dell'elenco. */
    private fun compactAge(now: Long, timestamp: Long): String {
        val seconds = ((now - timestamp) / 1000).toInt()
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
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
