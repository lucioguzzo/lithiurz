package com.lithiurz.signalmonitor

/**
 * Attribuzione delle celle all'operatore tramite il numero di canale radio
 * (EARFCN/NR-ARFCN/UARFCN/ARFCN). Android spesso non fornisce MCC/MNC per le
 * celle vicine non registrate, ma in Italia ogni blocco di frequenze è
 * assegnato a un operatore preciso, quindi il canale identifica il gestore.
 *
 * Valori ricavati dalle assegnazioni di spettro italiane (lteitaly.it):
 * ogni intervallo copre la portante dell'operatore (centro ± metà larghezza).
 */
object BandMap {

    enum class Rat { LTE, NR, WCDMA, GSM }

    private data class Entry(val rat: Rat, val range: IntRange, val operator: Operator)

    private val entries = listOf(
        // LTE (EARFCN downlink)
        // B20 800 MHz: WindTre 6200, TIM 6300, Vodafone 6400 (10 MHz ciascuno)
        Entry(Rat.LTE, 6150..6250, Operator.WIND),
        Entry(Rat.LTE, 6251..6350, Operator.TIM),
        Entry(Rat.LTE, 6351..6450, Operator.VODAFONE),
        // B3 1800 MHz: TIM 1350 (20), Iliad 1500 (10), WindTre 1650 (20), Vodafone 1850 (20)
        Entry(Rat.LTE, 1250..1450, Operator.TIM),
        Entry(Rat.LTE, 1451..1550, Operator.ILIAD),
        Entry(Rat.LTE, 1551..1750, Operator.WIND),
        Entry(Rat.LTE, 1751..1949, Operator.VODAFONE),
        // B1 2100 MHz: WindTre 100 (20), TIM 275 (15), Iliad 400 (10), Vodafone 525 (15)
        Entry(Rat.LTE, 0..200, Operator.WIND),
        Entry(Rat.LTE, 201..350, Operator.TIM),
        Entry(Rat.LTE, 351..450, Operator.ILIAD),
        Entry(Rat.LTE, 451..599, Operator.VODAFONE),
        // B7 2600 MHz: Iliad 2900 (10), Vodafone 3025 (15), TIM 3175 (15), WindTre 3350 (20)
        Entry(Rat.LTE, 2850..2950, Operator.ILIAD),
        Entry(Rat.LTE, 2951..3100, Operator.VODAFONE),
        Entry(Rat.LTE, 3101..3250, Operator.TIM),
        Entry(Rat.LTE, 3251..3449, Operator.WIND),
        // B8 900 MHz: TIM 3526 (5), Vodafone 3676 (5)
        Entry(Rat.LTE, 3501..3551, Operator.TIM),
        Entry(Rat.LTE, 3651..3701, Operator.VODAFONE),
        // B28 700 MHz: Iliad 9260 (10), TIM 9360 (10), Vodafone 9460 (10)
        Entry(Rat.LTE, 9210..9310, Operator.ILIAD),
        Entry(Rat.LTE, 9311..9410, Operator.TIM),
        Entry(Rat.LTE, 9411..9510, Operator.VODAFONE),
        // B32 1500 MHz SDL: TIM 10020 (20), Vodafone 10220 (20)
        Entry(Rat.LTE, 9920..10119, Operator.TIM),
        Entry(Rat.LTE, 10120..10320, Operator.VODAFONE),

        // 5G NR (SSB-ARFCN, ±600 intorno ai valori assegnati)
        // n78: TIM 636768/648768/650688 — WindTre 638016 — Vodafone 643296/645312
        Entry(Rat.NR, 636168..637368, Operator.TIM),
        Entry(Rat.NR, 648168..649368, Operator.TIM),
        Entry(Rat.NR, 650088..651288, Operator.TIM),
        Entry(Rat.NR, 637416..638616, Operator.WIND),
        Entry(Rat.NR, 641064..642264, Operator.ILIAD),
        Entry(Rat.NR, 642696..643896, Operator.VODAFONE),
        Entry(Rat.NR, 644712..645912, Operator.VODAFONE),
        // n38 2600 TDD: WindTre 516030/517210
        Entry(Rat.NR, 515430..517810, Operator.WIND),

        // UMTS 900 (UARFCN): Iliad 2938, WindTre 3063 (5 MHz ciascuno)
        Entry(Rat.WCDMA, 2925..2950, Operator.ILIAD),
        Entry(Rat.WCDMA, 3050..3075, Operator.WIND),

        // GSM 900 (ARFCN): TIM 1-25 e E-GSM 1000-1023, Vodafone 27-75, WindTre 77-124
        Entry(Rat.GSM, 1..25, Operator.TIM),
        Entry(Rat.GSM, 1000..1023, Operator.TIM),
        Entry(Rat.GSM, 27..75, Operator.VODAFONE),
        Entry(Rat.GSM, 77..124, Operator.WIND),

        // GSM 1800 (ARFCN 512-885). I blocchi coincidono con quelli LTE della
        // stessa banda, perché la licenza sullo spettro è unica per operatore:
        // TIM 1810-1830 MHz, Iliad 1830-1840, WindTre 1840-1860, Vodafone 1860-1880.
        // Conversione: F_DL(MHz) = 1805,2 + 0,2 × (ARFCN − 512).
        Entry(Rat.GSM, 537..636, Operator.TIM),
        Entry(Rat.GSM, 637..685, Operator.ILIAD),
        Entry(Rat.GSM, 686..785, Operator.WIND),
        Entry(Rat.GSM, 786..885, Operator.VODAFONE),
    )

    fun attribute(rat: Rat, channel: Int?): Operator? {
        if (channel == null) return null
        return entries.firstOrNull { it.rat == rat && channel in it.range }?.operator
    }

    /** Bande 3GPP a cui appartiene il canale, per mostrarle come fa un net monitor. */
    private val bands = listOf(
        // LTE (EARFCN downlink)
        Triple(Rat.LTE, 0..599, "B1"),
        Triple(Rat.LTE, 1200..1949, "B3"),
        Triple(Rat.LTE, 2750..3449, "B7"),
        Triple(Rat.LTE, 3450..3799, "B8"),
        Triple(Rat.LTE, 6150..6449, "B20"),
        Triple(Rat.LTE, 9210..9659, "B28"),
        Triple(Rat.LTE, 9920..10359, "B32"),
        Triple(Rat.LTE, 37750..38249, "B38"),
        Triple(Rat.LTE, 38650..39649, "B40"),
        Triple(Rat.LTE, 41590..43589, "B42"),
        // 5G NR (NR-ARFCN)
        Triple(Rat.NR, 151600..160600, "n28"),
        Triple(Rat.NR, 158200..164200, "n20"),
        Triple(Rat.NR, 361000..376000, "n3"),
        Triple(Rat.NR, 384000..396000, "n1"),
        Triple(Rat.NR, 514000..524000, "n38"),
        Triple(Rat.NR, 524000..538000, "n7"),
        Triple(Rat.NR, 620000..653333, "n78"),
        // UMTS (UARFCN) e GSM (ARFCN)
        Triple(Rat.WCDMA, 2937..3088, "U900"),
        Triple(Rat.WCDMA, 10562..10838, "U2100"),
        Triple(Rat.GSM, 0..124, "G900"),
        Triple(Rat.GSM, 975..1023, "G900"),
        Triple(Rat.GSM, 512..885, "G1800"),
    )

    fun bandOf(rat: Rat, channel: Int?): String? {
        if (channel == null) return null
        return bands.firstOrNull { it.first == rat && channel in it.second }?.third
    }
}
