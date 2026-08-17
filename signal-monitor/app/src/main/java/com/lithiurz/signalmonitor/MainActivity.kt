package com.lithiurz.signalmonitor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
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
    }

    private lateinit var telephonyManager: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ITALY)

    private class OperatorCard(
        val root: View,
        val dbmView: TextView,
        val qualityView: TextView,
        val techView: TextView,
        val progressBar: ProgressBar,
    )

    private val cards = mutableMapOf<Operator, OperatorCard>()
    private lateinit var lastUpdateView: TextView
    private lateinit var detailView: TextView

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
        lastUpdateView = findViewById(R.id.last_update)
        detailView = findViewById(R.id.cells_detail)

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
            progress.progressTintList = android.content.res.ColorStateList.valueOf(colors.getValue(op))
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
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) refresh()
    }

    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
        val cellReadings = SignalReader.readCells(cells)
        val serving = SignalReader.readServing(this)
        // La lettura della rete attiva integra (senza duplicare) quanto già visto tra le celle.
        val readings = (cellReadings + serving)
            .distinctBy { Triple(it.operator ?: "${it.mcc}-${it.mnc}", it.tech, it.registered) }
        val best = SignalReader.bestPerOperator(readings)

        for ((op, card) in cards) {
            val reading = best[op]
            if (reading == null) {
                card.dbmView.text = getString(R.string.no_signal_dbm)
                card.qualityView.text = getString(R.string.quality_absent)
                card.techView.text = ""
                card.progressBar.progress = 0
            } else {
                card.dbmView.text = getString(R.string.dbm_format, reading.dbm)
                card.qualityView.text = qualityLabel(reading.dbm)
                card.techView.text = if (reading.registered) {
                    getString(R.string.tech_registered, reading.tech)
                } else {
                    reading.tech
                }
                card.progressBar.progress = dbmToPercent(reading.dbm)
            }
        }

        lastUpdateView.text = getString(R.string.last_update, timeFormat.format(Date()))
        detailView.text = buildDetailText(readings)
    }

    private fun buildDetailText(readings: List<CellReading>): String {
        if (readings.isEmpty()) {
            return if (!isLocationEnabled()) getString(R.string.location_off) else getString(R.string.no_cells)
        }
        return readings
            .sortedByDescending { it.dbm }
            .joinToString("\n") { r ->
                val name = r.operator?.displayName ?: "${r.mcc ?: "?"}-${r.mnc ?: "?"}"
                val reg = if (r.registered) "  ●" else ""
                "%-14s %-7s %4d dBm%s".format(Locale.ITALY, name, r.tech, r.dbm, reg)
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
