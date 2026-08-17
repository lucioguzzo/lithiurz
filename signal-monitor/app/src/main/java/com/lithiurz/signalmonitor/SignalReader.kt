package com.lithiurz.signalmonitor

import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma

/** Lettura di una singola cella radio. */
data class CellReading(
    val operator: Operator?,
    val mcc: String?,
    val mnc: String?,
    val dbm: Int,
    val tech: String,
    val registered: Boolean,
)

object SignalReader {

    /** Converte la lista grezza di [CellInfo] in letture tipizzate, scartando i valori non validi. */
    fun readCells(cells: List<CellInfo>): List<CellReading> =
        cells.mapNotNull { toReading(it) }.filter { it.dbm in -140..-40 }

    /** Per ogni operatore restituisce la cella con il segnale migliore, se visibile. */
    fun bestPerOperator(readings: List<CellReading>): Map<Operator, CellReading?> =
        Operator.entries.associateWith { op ->
            readings.filter { it.operator == op }.maxByOrNull { it.dbm }
        }

    private fun toReading(info: CellInfo): CellReading? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val id = info.cellIdentity as? CellIdentityNr ?: return null
                build(id.mccString, id.mncString, info.cellSignalStrength.dbm, "5G NR", info.isRegistered)
            }
            info is CellInfoLte -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "4G LTE", info.isRegistered)
            }
            info is CellInfoWcdma -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "3G UMTS", info.isRegistered)
            }
            info is CellInfoGsm -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "2G GSM", info.isRegistered)
            }
            else -> null
        }
    }

    private fun build(mcc: String?, mnc: String?, dbm: Int, tech: String, registered: Boolean): CellReading =
        CellReading(Operator.fromMccMnc(mcc, mnc), mcc, mnc, dbm, tech, registered)

    private fun mcc(mccString: String?, mccInt: Int?): String? =
        mccString ?: mccInt?.takeIf { it in 0..999 }?.toString()

    // Su API < 28 esistono solo i campi numerici deprecati mcc/mnc: questi helper
    // usano le stringhe quando disponibili e ripiegano sui numeri altrimenti.
    private fun android.telephony.CellIdentityLte.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mccString else null

    private fun android.telephony.CellIdentityLte.mccInt(): Int? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) @Suppress("DEPRECATION") mcc else null

    private fun android.telephony.CellIdentityLte.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mncString
        else @Suppress("DEPRECATION") mnc.takeIf { it in 0..999 }?.toString()?.padStart(2, '0')

    private fun android.telephony.CellIdentityWcdma.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mccString else null

    private fun android.telephony.CellIdentityWcdma.mccInt(): Int? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) @Suppress("DEPRECATION") mcc else null

    private fun android.telephony.CellIdentityWcdma.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mncString
        else @Suppress("DEPRECATION") mnc.takeIf { it in 0..999 }?.toString()?.padStart(2, '0')

    private fun android.telephony.CellIdentityGsm.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mccString else null

    private fun android.telephony.CellIdentityGsm.mccInt(): Int? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) @Suppress("DEPRECATION") mcc else null

    private fun android.telephony.CellIdentityGsm.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mncString
        else @Suppress("DEPRECATION") mnc.takeIf { it in 0..999 }?.toString()?.padStart(2, '0')
}
