package com.example.calculator.engine

import kotlin.math.*

/**
 * Numerical calculus:
 *  - Differentiation  → 5-point central-difference  O(h⁴)
 *  - Integration      → Adaptive Simpson's rule
 *
 * UI expression syntax
 *   d/dx(x^2+3*x, x=2)          → first and second derivative at x=2
 *   integrate(0, 1, x^2)         → definite integral
 *   ∫(0, π, sin(x))              → same, Unicode form
 */
class CalculusEngine(private val engine: CalculatorEngine) {

    // ── Differentiation ───────────────────────────────────────────────────────

    fun differentiate(raw: String): String = try {
        val inner = raw
            .removePrefix("d/dx").removePrefix("diff")
            .trim().removeWrappingParens()
        val parts = inner.splitTop(',')
        require(parts.size >= 2) { "Format: d/dx(f(x), x=value)" }

        val expr = parts[0].trim()
        val xPart = parts[1].trim()
        val variable = if ('=' in xPart) xPart.substringBefore('=').trim() else "x"
        val x = engine.evaluateDouble(xPart.substringAfter('=', xPart).trim())

        val d1 = centralDiff5(expr, variable, x)
        val d2 = secondDeriv(expr, variable, x)
        "f'(${fmt(x)}) = ${engine.formatResult(d1)}\nf''(${fmt(x)}) = ${engine.formatResult(d2)}"
    } catch (e: Exception) { "Diff error: ${e.message}" }

    fun integrate(raw: String): String = try {
        val inner = raw
            .replace("∫", "integrate")
            .removePrefix("integrate")
            .trim().removeWrappingParens()
        val parts = inner.splitTop(',')
        require(parts.size >= 3) { "Format: integrate(lower, upper, f(x))" }

        val lower = engine.evaluateDouble(parts[0].trim())
        val upper = engine.evaluateDouble(parts[1].trim())
        val expr  = parts.drop(2).joinToString(",").trim()
        val result = adaptiveSimpson(expr, "x", lower, upper)
        "∫ from ${fmt(lower)} to ${fmt(upper)} = ${engine.formatResult(result)}"
    } catch (e: Exception) { "Integral error: ${e.message}" }

    // ── Numerical methods ─────────────────────────────────────────────────────

    /** 5-point central difference, O(h⁴). */
    fun centralDiff5(expr: String, v: String, x: Double): Double {
        val h = maxOf(abs(x), 1.0) * 1e-5
        val f = { t: Double -> evalWith(expr, v, t) }
        return (-f(x + 2*h) + 8*f(x + h) - 8*f(x - h) + f(x - 2*h)) / (12.0 * h)
    }

    /** Standard 3-point central difference (first derivative). */
    fun centralDifference(expr: String, v: String, x: Double): Double {
        val h = maxOf(abs(x), 1.0) * 1e-7
        return (evalWith(expr, v, x + h) - evalWith(expr, v, x - h)) / (2.0 * h)
    }

    /** 3-point second derivative. */
    fun secondDeriv(expr: String, v: String, x: Double): Double {
        val h = maxOf(abs(x), 1.0) * 1e-4
        val f = { t: Double -> evalWith(expr, v, t) }
        return (f(x + h) - 2.0 * f(x) + f(x - h)) / (h * h)
    }

    /** Recursive adaptive Simpson's rule (tolerance 1e-9). */
    fun adaptiveSimpson(expr: String, v: String, a: Double, b: Double,
                        tol: Double = 1e-9, maxDepth: Int = 28): Double {
        fun s(lo: Double, hi: Double): Double {
            val c = (lo + hi) / 2
            return (hi - lo) / 6 * (evalWith(expr, v, lo)
                                  + 4 * evalWith(expr, v, c)
                                  + evalWith(expr, v, hi))
        }
        fun rec(lo: Double, hi: Double, t: Double, whole: Double, d: Int): Double {
            val c  = (lo + hi) / 2
            val L  = s(lo, c); val R = s(c, hi)
            val delta = L + R - whole
            return if (d >= maxDepth || abs(delta) <= 15 * t)
                L + R + delta / 15.0
            else
                rec(lo, c, t / 2, L, d + 1) + rec(c, hi, t / 2, R, d + 1)
        }
        return rec(a, b, tol, s(a, b), 0)
    }

    // ── Shared helper (also used by EquationSolver) ──────────────────────────

    fun evalWith(expr: String, variable: String, value: Double): Double {
        val sub = expr.replace(
            Regex("""(?<![a-zA-Z_\d])${Regex.escape(variable)}(?![a-zA-Z_\d])"""),
            "($value)"
        )
        return engine.evaluateDouble(sub)
    }

    // ── Private utils ─────────────────────────────────────────────────────────

    private fun String.removeWrappingParens(): String = trim().let {
        if (it.startsWith('(') && it.endsWith(')')) it.substring(1, it.length - 1) else it
    }

    private fun String.splitTop(d: Char): List<String> {
        val out = mutableListOf<String>(); var depth = 0; var s = 0
        forEachIndexed { i, c ->
            when (c) { '(' -> depth++; ')' -> depth--; d -> if (depth == 0) { out += substring(s, i); s = i + 1 } }
        }
        return out + substring(s)
    }

    private fun fmt(v: Double) =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.4g".format(v)
}
