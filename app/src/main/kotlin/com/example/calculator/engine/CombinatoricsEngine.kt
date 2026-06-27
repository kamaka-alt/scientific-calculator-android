package com.example.calculator.engine

import java.math.BigInteger
import kotlin.math.*

/**
 * Combinatorics and counting operations.
 *
 * Factorials use [BigInteger] for exact integer results up to n = 10 000.
 * [factorialDouble] stays in [Double] precision and returns [Double.POSITIVE_INFINITY]
 * for n > 170 (overflow threshold for IEEE-754 doubles).
 *
 * The [gamma] function uses the Lanczos approximation (g = 7, n = 9 coefficients)
 * which gives relative error < 2 × 10⁻¹⁰ for Re(z) > 0.
 *
 * **UI expression formats:**
 * ```
 * 10!            → 3628800
 * 5 nCr 2        → 10
 * 5 nPr 2        → 20
 * ```
 */
class CombinatoricsEngine {

    // ── Entry point ─────────────────────────────────────────────────────────

    fun evaluate(expression: String): String {
        val trimmed = expression.trim()
        return try {
            when {
                trimmed.contains("nCr", ignoreCase = true) -> {
                    val (n, r) = splitBinaryOp(trimmed, "nCr")
                    nCr(n, r).toString()
                }
                trimmed.contains("nPr", ignoreCase = true) -> {
                    val (n, r) = splitBinaryOp(trimmed, "nPr")
                    nPr(n, r).toString()
                }
                trimmed.endsWith("!") -> {
                    val n = trimmed.dropLast(1).trim().toLong()
                    factorial(n).toString()
                }
                else -> throw IllegalArgumentException("Unrecognised combinatorics expression")
            }
        } catch (e: Exception) {
            "Combinatorics error: ${e.message}"
        }
    }

    // ── Factorial ────────────────────────────────────────────────────────────

    /** Exact BigInteger factorial for n ≤ 10 000. */
    fun factorial(n: Long): BigInteger {
        require(n >= 0) { "Factorial is undefined for negative integers" }
        require(n <= 10_000) { "n = $n is too large; use lnFactorial for approximation" }
        var result = BigInteger.ONE
        for (i in 2..n) result = result.multiply(BigInteger.valueOf(i))
        return result
    }

    /** Double-precision factorial; returns +∞ for n > 170. */
    fun factorialDouble(n: Long): Double {
        require(n >= 0) { "Factorial is undefined for negative integers" }
        if (n > 170) return Double.POSITIVE_INFINITY
        var result = 1.0
        for (i in 2..n) result *= i
        return result
    }

    /** Natural log of n! via Stirling's series — avoids overflow for any large n. */
    fun lnFactorial(n: Double): Double {
        if (n <= 1.0) return 0.0
        return n * ln(n) - n + 0.5 * ln(2 * PI * n) +
               1.0 / (12 * n) - 1.0 / (360 * n * n * n)
    }

    // ── Permutations / combinations ──────────────────────────────────────────

    /** P(n, r) = n! / (n-r)!  — exact BigInteger. */
    fun nPr(n: Long, r: Long): BigInteger {
        require(n >= 0 && r >= 0 && r <= n) { "nPr requires 0 ≤ r ≤ n, got n=$n, r=$r" }
        return factorial(n).divide(factorial(n - r))
    }

    /** C(n, r) = n! / (r! × (n-r)!)  — exact BigInteger, optimised with symmetry. */
    fun nCr(n: Long, r: Long): BigInteger {
        require(n >= 0 && r >= 0 && r <= n) { "nCr requires 0 ≤ r ≤ n, got n=$n, r=$r" }
        val k = minOf(r, n - r)          // exploit C(n,r) = C(n,n-r)
        return factorial(n).divide(factorial(k).multiply(factorial(n - k)))
    }

    /**
     * Double-precision C(n, r) via log-factorials — works for large n where
     * [nCr] would be slow (though still exact as a BigInteger).
     */
    fun nCrDouble(n: Double, r: Double): Double =
        exp(lnFactorial(n) - lnFactorial(r) - lnFactorial(n - r))

    // ── Gamma function (Lanczos, g = 7) ─────────────────────────────────────

    /**
     * Real-valued Gamma function Γ(z).
     * Satisfies Γ(n) = (n-1)! for positive integers.
     * Valid for all real z except non-positive integers (where Γ has poles).
     */
    fun gamma(z: Double): Double {
        if (z < 0.5) return PI / (sin(PI * z) * gamma(1.0 - z))   // reflection formula
        val n = z - 1.0
        var x = LANCZOS_C[0]
        for (i in 1 until LANCZOS_C.size) x += LANCZOS_C[i] / (n + i)
        val t = n + LANCZOS_G + 0.5
        return sqrt(2 * PI) * t.pow(n + 0.5) * exp(-t) * x
    }

    // ── Random number generation ─────────────────────────────────────────────

    /** Uniform random Double in [min, max). */
    fun randomInRange(min: Double = 0.0, max: Double = 1.0): Double =
        Math.random() * (max - min) + min

    /** Uniform random Int in [min, max]. */
    fun randomInt(min: Int, max: Int): Int = (min..max).random()

    /** Standard-normal random sample via Box–Muller transform. */
    fun randomNormal(mean: Double = 0.0, stdDev: Double = 1.0): Double {
        val u1 = Math.random(); val u2 = Math.random()
        return mean + stdDev * sqrt(-2.0 * ln(u1)) * cos(2 * PI * u2)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun splitBinaryOp(expr: String, op: String): Pair<Long, Long> {
        val parts = expr.split(Regex(op, RegexOption.IGNORE_CASE))
        require(parts.size == 2) { "Expected format: n ${op} r" }
        return parts[0].trim().toLong() to parts[1].trim().toLong()
    }

    companion object {
        // Lanczos g = 7 coefficients (Spouge / Wikipedia variant)
        private val LANCZOS_G = 7.0
        private val LANCZOS_C = doubleArrayOf(
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
    }
}
