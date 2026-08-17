package com.lithiurz.signalmonitor

/**
 * Operatori italiani monitorati, identificati tramite MCC (222 = Italia) + MNC.
 * WindTre trasmette sia con il codice storico Wind (88) sia con quello di 3 Italia (99).
 */
enum class Operator(val displayName: String, val shortName: String, val mncs: Set<String>) {
    TIM("TIM", "TIM", setOf("01")),
    VODAFONE("Vodafone", "Vodafone", setOf("10", "06")),
    WIND("WindTre (Wind)", "WindTre", setOf("88", "99"));

    companion object {
        const val ITALY_MCC = "222"

        fun fromMccMnc(mcc: String?, mnc: String?): Operator? {
            if (mcc != ITALY_MCC || mnc == null) return null
            val normalized = mnc.padStart(2, '0')
            return entries.firstOrNull { normalized in it.mncs }
        }
    }
}
