package com.bensinpris.app.data

import java.time.LocalDate

// Deciding when fuel near you is cheap enough to say "fill up now". Pure Kotlin, unit-tested.

/** The lowest current price near you on one day. */
data class DayLow(val day: LocalDate, val fuel: FuelType, val price: Double)

enum class AlertBasis {
    /** Compared with the average of last week's daily lows near you. */
    LAST_WEEK,
    /** Not enough history yet: compared with today's typical price in Norway. */
    NATIONAL,
}

data class CheapAlert(
    val spot: Spot,
    val fuel: FuelType,
    val price: FuelPrice,
    val baseline: Double,
    val basis: AlertBasis,
    /** True for the example shown from settings. */
    val example: Boolean = false,
) {
    val drop: Double get() = baseline - price.price
}

object Deal {
    /** At least this much cheaper per litre … */
    const val MIN_DROP = 0.50
    /** … and at least this share cheaper. */
    const val MIN_SHARE = 0.025
    /** Without a week of history, ask for a bigger drop from the national price. */
    const val MIN_DROP_NATIONAL = 1.00
    /** Prices older than this don't count as "now". */
    const val FRESH_MS = 36 * Format.HOUR
    /** Days of history needed before comparing with last week. */
    const val MIN_DAYS = 3

    fun isCurrent(p: FuelPrice, now: Long) = p.updatedAt != null && now - p.updatedAt <= FRESH_MS

    /** The cheapest station with a price that is current (optionally only real station prices, no chain estimates). */
    fun cheapestCurrent(spots: List<Spot>, now: Long, realOnly: Boolean = false): Spot? =
        spots.filter { s -> s.price?.let { isCurrent(it, now) && (!realOnly || !it.isEstimate) } == true }
            .minWithOrNull(compareBy<Spot> { it.price!!.price }.thenBy { it.distanceM })

    /** Average of the daily lows for [fuel] over the 7 days before [today], or null with fewer than [MIN_DAYS] days. */
    fun lastWeekAverage(history: List<DayLow>, fuel: FuelType, today: LocalDate): Double? {
        val days = history.filter { it.fuel == fuel && it.day < today && it.day >= today.minusDays(7) }
        return if (days.size < MIN_DAYS) null else days.map { it.price }.average()
    }

    /** Middle of today's chain prices: the "usual" price in Norway today. */
    fun nationalTypical(chains: List<ChainPrice>, fuel: FuelType): Double? {
        val p = chains.mapNotNull { it.prices[fuel] }.sorted()
        return if (p.isEmpty()) null else if (p.size % 2 == 1) p[p.size / 2] else (p[p.size / 2 - 1] + p[p.size / 2]) / 2
    }

    /** Is there a reason to say "fill up now"? */
    fun check(spots: List<Spot>, fuel: FuelType, history: List<DayLow>, national: Double?, today: LocalDate, now: Long): CheapAlert? {
        val week = lastWeekAverage(history, fuel, today)
        if (week != null) {
            val best = cheapestCurrent(spots, now) ?: return null
            val p = best.price!!
            val drop = week - p.price
            return if (drop >= MIN_DROP && drop / week >= MIN_SHARE) CheapAlert(best, fuel, p, week, AlertBasis.LAST_WEEK) else null
        }
        if (national == null) return null
        val best = cheapestCurrent(spots, now, realOnly = true) ?: return null
        val p = best.price!!
        val drop = national - p.price
        return if (drop >= MIN_DROP_NATIONAL && drop / national >= MIN_SHARE) CheapAlert(best, fuel, p, national, AlertBasis.NATIONAL) else null
    }
}
