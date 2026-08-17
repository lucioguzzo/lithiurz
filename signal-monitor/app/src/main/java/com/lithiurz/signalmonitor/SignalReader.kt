package com.lithiurz.signalmonitor

import android.content.Context
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Lettura di una singola cella radio. */
data class CellReading(
    val operator: Operator?,
    val mcc: String?,
    val mnc: String?,
    val dbm: Int,
    val tech: String,
    val registered: Boolean,
    /** Canale radio (EARFCN/NR-ARFCN/UARFCN/ARFCN), se noto. */
    val channel: Int? = null,
    /** Identificativo fisico della cella: PCI su LTE/NR, PSC su UMTS, BSIC su GSM. */
    val pci: Int? = null,
    /** Banda 3GPP ricavata dal canale (B3, n78, …). */
    val band: String? = null,
    /** true se l'operatore è stato dedotto dalla frequenza anziché dal MCC/MNC. */
    val estimated: Boolean = false,
)

object SignalReader {

    /**
     * Converte la lista grezza di [CellInfo] in letture tipizzate, scartando i
     * valori non validi. Il modem restituisce spesso celle con tutti i campi
     * non disponibili (PCI e canale a Integer.MAX_VALUE): senza un solo dato
     * identificativo sono rumore e non dicono nulla su nessun operatore.
     */
    fun readCells(cells: List<CellInfo>): List<CellReading> =
        cells.mapNotNull { toReading(it) }
            .filter { it.dbm in -140..-40 }
            .filter { it.channel != null || it.pci != null || it.operator != null || it.registered }
            .filter { it.registered || it.pci != null || it.dbm > NO_SIGNAL_FLOOR_DBM }

    /**
     * Sotto questa soglia, e senza un identificativo fisico di cella decodificato
     * (PCI o BSIC), la voce non è una misura: è un canale della lista che la rete
     * comunica al telefono perché lo tenga d'occhio. In GSM il livello 0 vale
     * esattamente -113 dBm, ed è così che questi canali si presentano.
     */
    private const val NO_SIGNAL_FLOOR_DBM = -113

    /** Elenco celle di tutte le SIM attive, non solo di quella predefinita. */
    fun readAllCells(context: Context): List<CellInfo> {
        val default = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        return managersFor(context, default).flatMap { tm ->
            try {
                tm.allCellInfo ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }

    /** Un [TelephonyManager] per ogni SIM attiva, con ripiego su quello predefinito. */
    private fun managersFor(context: Context, default: TelephonyManager): List<TelephonyManager> =
        try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                ?.map { default.createForSubscriptionId(it.subscriptionId) }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: SecurityException) {
            null
        } ?: listOf(default)

    /** Per ogni operatore restituisce la cella con il segnale migliore, se visibile. */
    fun bestPerOperator(readings: List<CellReading>): Map<Operator, CellReading?> =
        Operator.entries.associateWith { op ->
            readings.filter { it.operator == op }.maxByOrNull { it.dbm }
        }

    /**
     * Legge il segnale della rete a cui il telefono è registrato (una lettura per SIM attiva),
     * tramite [TelephonyManager.getSignalStrength]: funziona anche quando il modem non espone
     * l'elenco delle celle (getAllCellInfo vuoto) e non richiede la localizzazione attiva.
     */
    fun readServing(context: Context): List<CellReading> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val default = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        return managersFor(context, default).flatMap { tm ->
            val op = tm.networkOperator
            if (op == null || op.length < 5) return@flatMap emptyList<CellReading>()
            val mcc = op.substring(0, 3)
            val mnc = op.substring(3).padStart(2, '0')
            val operator = Operator.fromMccMnc(mcc, mnc)
            // In 5G NSA il telefono è agganciato contemporaneamente a LTE e NR:
            // le riportiamo entrambe anziché tenere solo la più forte.
            tm.signalStrength?.cellSignalStrengths
                ?.filter { it.dbm in -140..-40 }
                ?.map { s -> CellReading(operator, mcc, mnc, s.dbm, techOf(s), registered = true) }
                .orEmpty()
        }
    }

    private fun techOf(s: CellSignalStrength): String = when (s) {
        is CellSignalStrengthNr -> "5G NR"
        is CellSignalStrengthLte -> "4G LTE"
        is CellSignalStrengthWcdma, is CellSignalStrengthTdscdma -> "3G UMTS"
        is CellSignalStrengthCdma -> "3G CDMA"
        is CellSignalStrengthGsm -> "2G GSM"
        else -> "?"
    }

    private fun toReading(info: CellInfo): CellReading? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val id = info.cellIdentity as? CellIdentityNr ?: return null
                build(id.mccString, id.mncString, info.cellSignalStrength.dbm, "5G NR",
                    info.isRegistered, BandMap.Rat.NR, id.nrarfcn, id.pci, bandsOf(id))
            }
            info is CellInfoLte -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "4G LTE",
                    info.isRegistered, BandMap.Rat.LTE, id.earfcn, id.pci, bandsOf(id))
            }
            info is CellInfoWcdma -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "3G UMTS",
                    info.isRegistered, BandMap.Rat.WCDMA, id.uarfcn, id.psc)
            }
            info is CellInfoGsm -> {
                val id = info.cellIdentity
                build(mcc(id.mccStringCompat(), id.mccInt()), id.mncStringCompat(), info.cellSignalStrength.dbm, "2G GSM",
                    info.isRegistered, BandMap.Rat.GSM, id.arfcn, id.bsic)
            }
            else -> null
        }
    }

    private fun build(
        mcc: String?,
        mnc: String?,
        dbm: Int,
        tech: String,
        registered: Boolean,
        rat: BandMap.Rat,
        rawChannel: Int,
        rawPci: Int,
        bandFromApi: String? = null,
    ): CellReading {
        val channel = rawChannel.takeIf { it != Int.MAX_VALUE && it >= 0 }
        val fromPlmn = Operator.fromMccMnc(mcc, mnc)
        val operator = fromPlmn ?: BandMap.attribute(rat, channel)
        return CellReading(
            operator, mcc, mnc, dbm, tech, registered,
            channel = channel,
            pci = rawPci.takeIf { it != Int.MAX_VALUE && it >= 0 },
            // La banda dedotta dal canale è la più precisa; quella dichiarata
            // dall'API copre i casi in cui il canale non arriva.
            band = BandMap.bandOf(rat, channel) ?: bandFromApi,
            estimated = fromPlmn == null && operator != null,
        )
    }

    /** Banda dichiarata direttamente dall'identità di cella (Android 11+). */
    private fun bandsOf(id: android.telephony.CellIdentityLte): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            id.bands.firstOrNull()?.let { "B$it" }
        } else {
            null
        }

    private fun bandsOf(id: CellIdentityNr): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            id.bands.firstOrNull()?.let { "n$it" }
        } else {
            null
        }

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
