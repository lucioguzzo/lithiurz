package com.lithiurz.signalmonitor

import android.content.Context
import android.os.Build
import android.telephony.AccessNetworkConstants.AccessNetworkType
import android.telephony.CellInfo
import android.telephony.NetworkScan
import android.telephony.NetworkScanRequest
import android.telephony.RadioAccessSpecifier
import android.telephony.TelephonyManager
import android.telephony.TelephonyScanManager

/**
 * Scansione radio di tutte le reti presenti, non solo di quella della SIM: è
 * l'unico modo previsto da Android per misurare gli altri operatori.
 *
 * `requestNetworkScan` richiede però `MODIFY_PHONE_STATE`, un permesso di
 * sistema che le app normali non possono ottenere: sulla maggior parte dei
 * telefoni la chiamata viene rifiutata. La tentiamo comunque — su alcune build
 * OEM e sui dispositivi con l'app installata come di sistema funziona —
 * riportando l'esito reale invece di fallire in silenzio.
 */
class NetworkScanner(private val context: Context) {

    sealed interface Result {
        /** Risultati parziali: la scansione è ancora in corso. */
        data class Cells(val cells: List<CellInfo>) : Result
        data object Completed : Result
        data class Failed(val reason: String) : Result
    }

    private var scan: NetworkScan? = null

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun start(onResult: (Result) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onResult(Result.Failed(context.getString(R.string.scan_needs_android_9)))
            return
        }
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (tm == null) {
            onResult(Result.Failed(context.getString(R.string.scan_no_telephony)))
            return
        }

        val specifiers = buildList {
            add(RadioAccessSpecifier(AccessNetworkType.EUTRAN, null, null))
            add(RadioAccessSpecifier(AccessNetworkType.UTRAN, null, null))
            add(RadioAccessSpecifier(AccessNetworkType.GERAN, null, null))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(RadioAccessSpecifier(AccessNetworkType.NGRAN, null, null))
            }
        }.toTypedArray()

        val request = NetworkScanRequest(
            NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
            specifiers,
            SEARCH_PERIODICITY_SEC,
            MAX_SEARCH_TIME_SEC,
            /* incrementalResults = */ true,
            INCREMENTAL_PERIODICITY_SEC,
            /* plmns = */ null,
        )

        // Oltre a SecurityException il modem può sollevare IllegalArgumentException
        // o UnsupportedOperationException a seconda dell'implementazione OEM.
        scan = try {
            tm.requestNetworkScan(
                request,
                context.mainExecutor,
                object : TelephonyScanManager.NetworkScanCallback() {
                    override fun onResults(results: MutableList<CellInfo>) {
                        onResult(Result.Cells(results))
                    }

                    override fun onComplete() {
                        scan = null
                        onResult(Result.Completed)
                    }

                    override fun onError(error: Int) {
                        scan = null
                        onResult(Result.Failed(errorLabel(error)))
                    }
                },
            )
        } catch (t: Throwable) {
            onResult(Result.Failed(describe(t)))
            null
        }
    }

    fun stop() {
        try {
            scan?.stopScan()
        } catch (t: Throwable) {
            // La scansione può essere già terminata lato modem: nulla da fare.
        }
        scan = null
    }

    private fun describe(t: Throwable): String = when (t) {
        is SecurityException -> context.getString(R.string.scan_denied)
        else -> "${t.javaClass.simpleName}${t.message?.let { ": $it" }.orEmpty()}"
    }

    private fun errorLabel(error: Int): String = when (error) {
        NetworkScan.ERROR_MODEM_ERROR -> context.getString(R.string.scan_error_modem)
        NetworkScan.ERROR_UNSUPPORTED -> context.getString(R.string.scan_error_unsupported)
        NetworkScan.ERROR_MODEM_UNAVAILABLE -> context.getString(R.string.scan_error_busy)
        NetworkScan.ERROR_INTERRUPTED -> context.getString(R.string.scan_error_interrupted)
        else -> context.getString(R.string.scan_error_code, error)
    }

    private companion object {
        const val SEARCH_PERIODICITY_SEC = 5
        const val MAX_SEARCH_TIME_SEC = 60
        const val INCREMENTAL_PERIODICITY_SEC = 5
    }
}
