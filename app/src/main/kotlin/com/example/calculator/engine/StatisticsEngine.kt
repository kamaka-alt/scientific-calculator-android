package com.example.calculator.engine
import kotlin.math.roundToInt

import kotlin.math.*

/**
 * Descriptive and bivariate statistics.
 *
 * **1-Variable statistics** (`stat1:` prefix)
 * Input  : comma-separated numbers, e.g. `stat1:12,14,11,18,15`
 * Output : n, x̄, Σx, Σx², Sx (sample), σx (population), min, Q1, median, Q3, max
 *
 * **2-Variable statistics** (`stat2:` prefix)
 * Input  : parenthesised (x,y) pairs, e.g. `stat2:(1,2),(3,5),(4,8),(6,11)`
 * Output : n, x̄, ȳ, Σx, Σy, Σx², Σy², Σxy, Sx, Sy, r (Pearson), r², regression slope a
 *          and intercept b for the model ŷ = ax + b
 *
 * Follows the same notation as Casio scientific calculators (STAT mode).
 */
class StatisticsEngine {

    // ── 1-Variable ───────────────────────────────────────────────────────────

    fun oneStat(data: String): String {
        val xs = parseValues(data)
        require(xs.size >= 2) { "Need at least 2 data points" }
        val n = xs.size.toDouble()
        val sorted = xs.sorted()

        val sumX  = xs.sum()
        val sumX2 = xs.sumOf { it * it }
        val mean  = sumX / n
        val popVar    = xs.sumOf { (it - mean).pow(2) } / n
        val sampleVar = xs.sumOf { (it - mean).pow(2) } / (n - 1)

        return buildString {
            line("n",   xs.size.toString())
            line("x̄",   fmt(mean))
            line("Σx",  fmt(sumX))
            line("Σx²", fmt(sumX2))
            line("Sx",  fmt(sqrt(sampleVar)))
            line("σx",  fmt(sqrt(popVar)))
            line("min", fmt(sorted.first()))
            line("Q1",  fmt(percentile(sorted, 25.0)))
            line("Med", fmt(percentile(sorted, 50.0)))
            line("Q3",  fmt(percentile(sorted, 75.0)))
            append("max = ${fmt(sorted.last())}")
        }
    }

    // ── 2-Variable ───────────────────────────────────────────────────────────

    fun twoStat(data: String): String {
        val pairs = parsePairs(data)
        require(pairs.size >= 2) { "Need at least 2 (x, y) pairs" }
        val n = pairs.size.toDouble()

        val xs   = pairs.map { it.first }
        val ys   = pairs.map { it.second }
        val sumX  = xs.sum();   val sumY  = ys.sum()
        val sumX2 = xs.sumOf { it * it };  val sumY2 = ys.sumOf { it * it }
        val sumXY = pairs.sumOf { it.first * it.second }
        val meanX = sumX / n;  val meanY = sumY / n

        val sxSample = sqrt(xs.sumOf { (it - meanX).pow(2) } / (n - 1))
        val sySample = sqrt(ys.sumOf { (it - meanY).pow(2) } / (n - 1))

        // Pearson correlation coefficient
        val r = (sumXY - n * meanX * meanY) /
                sqrt((sumX2 - n * meanX * meanX) * (sumY2 - n * meanY * meanY))

        // Linear regression  ŷ = a·x + b
        val a = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val b = (sumY - a * sumX) / n

        return buildString {
            line("n",    pairs.size.toString())
            line("x̄",    fmt(meanX));        line("ȳ",    fmt(meanY))
            line("Σx",   fmt(sumX));          line("Σy",   fmt(sumY))
            line("Σx²",  fmt(sumX2));         line("Σy²",  fmt(sumY2))
            line("Σxy",  fmt(sumXY))
            line("Sx",   fmt(sxSample));      line("Sy",   fmt(sySample))
            line("r",    fmt(r));             line("r²",   fmt(r * r))
            line("a (slope)",     fmt(a))
            append("b (intercept) = ${fmt(b)}")
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Sample variance of a list of doubles. */
    fun sampleVariance(data: List<Double>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean).pow(2) } / (data.size - 1)
    }

    /** Population variance. */
    fun popVariance(data: List<Double>): Double {
        val mean = data.average()
        return data.sumOf { (it - mean).pow(2) } / data.size
    }

    /** Pearson correlation coefficient for two equal-length lists. */
    fun pearsonR(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size && xs.size >= 2) { "Lists must have equal length ≥ 2" }
        val n = xs.size.toDouble()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> x * y }
        val meanX = xs.average(); val meanY = ys.average()
        val ssx   = xs.sumOf { (it - meanX).pow(2) }
        val ssy   = ys.sumOf { (it - meanY).pow(2) }
        return (sumXY - n * meanX * meanY) / sqrt(ssx * ssy)
    }

    /** Percentile (0–100) via linear interpolation on sorted data. */
    fun percentile(sorted: List<Double>, p: Double): Double {
        val idx      = p / 100.0 * (sorted.size - 1)
        val lower    = idx.toInt()
        val fraction = idx - lower
        return if (lower + 1 < sorted.size)
            sorted[lower] + fraction * (sorted[lower + 1] - sorted[lower])
        else sorted[lower]
    }

    // ── Private parsers ───────────────────────────────────────────────────────

    private fun parseValues(raw: String): List<Double> =
        raw.split(",").map { it.trim().toDouble() }

    /**
     * Parses `(x1,y1),(x2,y2),...` into a list of [Pair]<Double, Double>.
     * Spaces are optional.
     */
    private fun parsePairs(raw: String): List<Pair<Double, Double>> {
        val rx = Regex("""\(\s*([^\s,)]+)\s*,\s*([^\s,)]+)\s*\)""")
        return rx.findAll(raw).map { m ->
            m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
        }.toList().also {
            require(it.isNotEmpty()) { "No valid (x,y) pairs found. Format: (x1,y1),(x2,y2),..." }
        }
    }

    private fun StringBuilder.line(label: String, value: String) {
        appendLine("$label = $value")
    }

    private fun fmt(v: Double): String {
        val rounded = (v * 1e10).roundToLong() / 1e10
        return if (rounded == rounded.toLong().toDouble() && abs(rounded) < 1e15)
            rounded.toLong().toString()
        else if (abs(rounded) >= 1e10 || (abs(rounded) < 1e-4 && rounded != 0.0))
            "%.6e".format(rounded)
        else "%.8f".format(rounded).trimEnd('0').trimEnd('.')
    }
}

