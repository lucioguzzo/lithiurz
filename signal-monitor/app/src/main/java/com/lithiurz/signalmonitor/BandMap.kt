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
        // B3 1800 MHz: TIM 1350 (20), WindTre 1650 (20), Vodafone 1850 (20); Iliad 1500 esclusa
        Entry(Rat.LTE, 1250..1450, Operator.TIM),
        Entry(Rat.LTE, 1551..1750, Operator.WIND),
        Entry(Rat.LTE, 1751..1949, Operator.VODAFONE),
        // B1 2100 MHz: WindTre 100 (20), TIM 275 (15), Vodafone 525 (15); Iliad 400 esclusa
        Entry(Rat.LTE, 0..200, Operator.WIND),
        Entry(Rat.LTE, 201..350, Operator.TIM),
        Entry(Rat.LTE, 451..599, Operator.VODAFONE),
        // B7 2600 MHz: Vodafone 3025 (15), TIM 3175 (15), WindTre 3350 (20); Iliad 2900 esclusa
        Entry(Rat.LTE, 2951..3100, Operator.VODAFONE),
        Entry(Rat.LTE, 3101..3250, Operator.TIM),
        Entry(Rat.LTE, 3251..3449, Operator.WIND),
        // B8 900 MHz: TIM 3526 (5), Vodafone 3676 (5)
        Entry(Rat.LTE, 3501..3551, Operator.TIM),
        Entry(Rat.LTE, 3651..3701, Operator.VODAFONE),
        // B28 700 MHz: TIM 9360 (10), Vodafone 9460 (10); Iliad 9260 esclusa
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
        Entry(Rat.NR, 642696..643896, Operator.VODAFONE),
        Entry(Rat.NR, 644712..645912, Operator.VODAFONE),
        // n38 2600 TDD: WindTre 516030/517210
        Entry(Rat.NR, 515430..517810, Operator.WIND),

        // UMTS 900 (UARFCN): WindTre 3063 (5 MHz) — TIM e Vodafone hanno dismesso il 3G
        Entry(Rat.WCDMA, 3050..3075, Operator.WIND),

        // GSM 900 (ARFCN): TIM 1-26 + E-GSM 975-1023, Vodafone 27-76, WindTre 77-124
        Entry(Rat.GSM, 1..26, Operator.TIM),
        Entry(Rat.GSM, 975..1023, Operator.TIM),
        Entry(Rat.GSM, 27..76, Operator.VODAFONE),
        Entry(Rat.GSM, 77..124, Operator.WIND),
    )

    fun attribute(rat: Rat, channel: Int?): Operator? {
        if (channel == null) return null
        return entries.firstOrNull { it.rat == rat && channel in it.range }?.operator
    }
}
