package com.example.calculator.engine

import kotlin.math.*

/**
 * Single-variable algebraic equation solver.
 *
 * **Strategies (applied in order)**
 * 1. Analytic — linear and quadratic forms solved exactly.
 * 2. Cardano — cubic `ax³ + bx² + cx + d = 0` via Cardano's formula.
 * 3. Newton–Raphson — general non-linear equations, multiple initial guesses.
 * 4. Bisection fallback — when Newton–Raphson stagnates or diverges.
 *
 * **UI expression syntax** (routed via `solve(…)` prefix):
 * ```
 * solve(x^2 - 5*x + 6 = 0)            → "x₁ = 2, x₂ = 3"
 * solve(sin(x) = 0.5, x)              → "x = 0.523599"  (π/6)
 * solve(x^3 - 6*x^2 + 11*x - 6 = 0)  → "x₁ = 1, x₂ = 2, x₃ = 3"
 * solve(e^x = 10, x, guess=2)         → "x = 2.302585"
 * ```
 *
 * @param calculusEngine Used for function evaluation / numerical differentiation.
 */
class EquationSolver(
    private val calculatorEngine: CalculatorEngine,
    private val calculusEngine: CalculusEngine
) {

    // ── Entry point ──────────────────────────────────────────────────────────

    fun solve(expression: String): String {
        return try {
            val (lhsExpr, rhsExpr, variable, guess) = parseInput(expression)
            // Normalise: f(x) = lhs - rhs
            val fExpr = if (rhsExpr == "0") lhsExpr else "($lhsExpr) - ($rhsExpr)"

            // Coefficient extraction (enables analytic paths)
            val coeffs = tryExtractPolynomialCoeffs(fExpr, variable)

            when (coeffs?.size) {
                2 -> solveLinear(coeffs[1], coeffs[0])
                3 -> solveQuadratic(coeffs[2], coeffs[1], coeffs[0])
                4 -> solveCubic(coeffs[3], coeffs[2], coeffs[1], coeffs[0])
                else -> {
                    // Numerical fallback
                    val roots = findRootsNumerically(fExpr, variable, guess)
                    formatRoots(roots, variable)
                }
            }
        } catch (e: Exception) {
            "Solver error: ${e.message}"
        }
    }

    // ── Analytic solvers ─────────────────────────────────────────────────────

    private fun solveLinear(a: Double, b: Double): String {
        // ax + b = 0  →  x = -b/a
        if (abs(a) < EPSILON) return if (abs(b) < EPSILON) "Identity (infinite solutions)" else "No solution"
        return "x = ${fmt(-b / a)}"
    }

    private fun solveQuadratic(a: Double, b: Double, c: Double): String {
        if (abs(a) < EPSILON) return solveLinear(b, c)
        val disc = b * b - 4 * a * c
        return when {
            disc < 0 -> {
                val re = -b / (2 * a)
                val im = sqrt(-disc) / (2 * a)
                "x₁ = ${fmt(re)} + ${fmt(im)}i\nx₂ = ${fmt(re)} - ${fmt(im)}i  (complex)"
            }
            abs(disc) < EPSILON -> "x = ${fmt(-b / (2 * a))}  (double root)"
            else -> {
                val sqrtD = sqrt(disc)
                "x₁ = ${fmt((-b + sqrtD) / (2 * a))}\nx₂ = ${fmt((-b - sqrtD) / (2 * a))}"
            }
        }
    }

    /**
     * Cardano's method for depressed cubics, with real-root discrimination.
     * Reference: Numerical Recipes §5.6
     */
    private fun solveCubic(a: Double, b: Double, c: Double, d: Double): String {
        if (abs(a) < EPSILON) return solveQuadratic(b, c, d)
        val A = b / a; val B = c / a; val C = d / a
        val p = B - A * A / 3
        val q = 2 * A * A * A / 27 - A * B / 3 + C
        val disc = q * q / 4 + p * p * p / 27
        val roots: List<Double> = when {
            abs(disc) < EPSILON -> {
                val u = if (q > 0) -(q / 2).pow(1.0 / 3) else ((-q) / 2).pow(1.0 / 3)
                listOf(2 * u - A / 3, -u - A / 3).distinct()
            }
            disc > 0 -> {
                val sqrtD = sqrt(disc)
                val u = cbrt(-q / 2 + sqrtD)
                val v = cbrt(-q / 2 - sqrtD)
                listOf(u + v - A / 3)
            }
            else -> {
                val r = sqrt(-(p * p * p) / 27)
                val theta = acos((-q / 2) / r.coerceAtLeast(1e-15))
                val m = 2 * cbrt(r)
                listOf(
                    m * cos(theta / 3) - A / 3,
                    m * cos((theta + 2 * PI) / 3) - A / 3,
                    m * cos((theta + 4 * PI) / 3) - A / 3
                )
            }
        }
        val verified = roots.filter { x ->
            abs(a * x.pow(3) + b * x.pow(2) + c * x + d) < 1e-5
        }.sortedBy { abs(it) }
        return if (verified.isEmpty()) "Roots: ${roots.joinToString { fmt(it) }}"
               else formatRoots(verified, "x")
    }

    // ── Numerical solvers ─────────────────────────────────────────────────────

    /**
     * Tries several starting points and collects distinct real roots via Newton–Raphson.
     * Falls back to bisection on predetermined bracket intervals.
     */
    fun findRootsNumerically(
        fExpr: String,
        variable: String,
        initialGuess: Double? = null
    ): List<Double> {
        val f = { x: Double -> calculusEngine.evalWith(fExpr, variable, x) }
        val df = { x: Double -> calculusEngine.centralDifference(fExpr, variable, x) }

        val candidates = (initialGuess?.let { listOf(it) } ?: emptyList()) +
                listOf(0.0, 0.5, 1.0, -1.0, 2.0, -2.0, 5.0, -5.0, 10.0, -10.0, 0.1, -0.1, 100.0, -100.0)

        val roots = mutableListOf<Double>()

        for (x0 in candidates) {
            try {
                val root = newtonRaphson(f, df, x0) ?: continue
                if (roots.none { abs(it - root) < 1e-6 }) roots += root
            } catch (_: Exception) { /* try next seed */ }
        }

        // Bisection on common brackets (catches roots Newton missed)
        val brackets = listOf(-1e3..(-0.01), -0.01..0.01, 0.01..1e3)
        for (bracket in brackets) {
            try {
                val a = bracket.start; val b = bracket.endInclusive
                if (f(a) * f(b) < 0) {
                    val root = bisection(f, a, b)
                    if (roots.none { abs(it - root) < 1e-6 }) roots += root
                }
            } catch (_: Exception) { }
        }

        return roots.sortedBy { abs(it) }
    }

    private fun newtonRaphson(
        f: (Double) -> Double,
        df: (Double) -> Double,
        x0: Double,
        maxIter: Int = 150,
        tol: Double = 1e-10
    ): Double? {
        var x = x0
        repeat(maxIter) {
            val fx  = f(x)
            if (abs(fx) < tol) return x
            val dfx = df(x)
            if (abs(dfx) < 1e-14) return null   // flat derivative — abandon
            val xNew = x - fx / dfx
            if (!xNew.isFinite()) return null
            if (abs(xNew - x) < tol && abs(f(xNew)) < tol * 100) return xNew
            x = xNew
        }
        return if (abs(f(x)) < 1e-6) x else null
    }

    fun bisection(
        f: (Double) -> Double,
        a: Double, b: Double,
        tol: Double = 1e-10,
        maxIter: Int = 200
    ): Double {
        var lo = a; var hi = b
        repeat(maxIter) {
            val mid = (lo + hi) / 2
            val fm  = f(mid)
            when {
                abs(fm) < tol || (hi - lo) / 2 < tol -> return mid
                f(lo) * fm < 0 -> hi = mid
                else           -> lo = mid
            }
        }
        return (lo + hi) / 2
    }

    // ── Polynomial coefficient extraction ────────────────────────────────────

    /**
     * Attempts to identify the polynomial degree and extract coefficients
     * by sampling the function at integer points and solving the Vandermonde system.
     *
     * Returns `null` for non-polynomial functions (trig, log, exp, etc.)
     * or polynomials of degree > 3.
     */
    private fun tryExtractPolynomialCoeffs(fExpr: String, variable: String): DoubleArray? {
        // Quick heuristic: reject expressions with transcendental function names
        val transcendental = listOf("sin", "cos", "tan", "log", "ln", "exp", "sqrt", "abs")
        if (transcendental.any { fExpr.contains(it, ignoreCase = true) }) return null

        return try {
            val f = { x: Double -> calculusEngine.evalWith(fExpr, variable, x) }
            // Sample at -2, -1, 0, 1, 2 and test if differences stabilise at degree ≤ 3
            val pts = (-2..2).map { f(it.toDouble()) }
            val d1 = (1..4).map { pts[it] - pts[it - 1] }
            val d2 = (1..3).map { d1[it] - d1[it - 1] }
            val d3 = (1..2).map { d2[it] - d2[it - 1] }
            val d4 = abs(d3[1] - d3[0])

            val degree = when {
                abs(d3[0]) > 1e-6 && d4 < 1e-4 -> 3
                abs(d2[0]) > 1e-6 && abs(d3[0]) < 1e-4 -> 2
                abs(d1[0]) > 1e-6 && abs(d2[0]) < 1e-4 -> 1
                else -> return null   // degree 0 or non-polynomial
            }

            // Reconstruct coefficients via Newton forward differences
            val coeffs = DoubleArray(degree + 1)
            when (degree) {
                1 -> {
                    coeffs[0] = pts[2]   // f(0) = c
                    coeffs[1] = d1[2]    // slope
                }
                2 -> {
                    coeffs[0] = pts[2]
                    coeffs[1] = d1[2] - d2[1] / 2
                    coeffs[2] = d2[1] / 2
                }
                3 -> {
                    coeffs[0] = pts[2]
                    coeffs[1] = d1[2] - d2[1] / 2 + d3[0] / 6
                    coeffs[2] = d2[1] / 2 - d3[0] / 2
                    coeffs[3] = d3[0] / 6
                }
            }
            coeffs
        } catch (_: Exception) { null }
    }

    // ── Input parser ──────────────────────────────────────────────────────────

    private data class SolveInput(
        val lhs: String,
        val rhs: String,
        val variable: String,
        val guess: Double?
    )

    private fun parseInput(raw: String): SolveInput {
        val inner = raw.removePrefix("solve(").removeSuffix(")")
        // Split top-level commas
        val parts = inner.splitTopLevel(',')
        val eq    = parts[0].trim()

        val variable = parts.getOrNull(1)?.trim()?.let {
            if (it.startsWith("guess", ignoreCase = true)) "x" else it
        } ?: "x"

        val guess = parts.firstOrNull { it.trim().startsWith("guess", ignoreCase = true) }
            ?.let { it.substringAfter('=').trim().toDoubleOrNull() }

        val (lhs, rhs) = if ('=' in eq) {
            eq.substringBefore('=').trim() to eq.substringAfter('=').trim()
        } else {
            eq to "0"
        }
        return SolveInput(lhs, rhs, variable, guess)
    }

    private fun String.splitTopLevel(delim: Char): List<String> {
        val out = mutableListOf<String>(); var depth = 0; var start = 0
        forEachIndexed { i, ch ->
            when (ch) { '(' -> depth++; ')' -> depth--; delim -> if (depth == 0) { out += substring(start, i); start = i + 1 } }
        }
        out += substring(start); return out
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun eval(expr: String) = try { calculatorEngine.evaluateDouble(expr) } catch (_: Exception) { Double.NaN }

    private fun formatRoots(roots: List<Double>, variable: String): String {
        if (roots.isEmpty()) return "No real roots found in the search range"
        return if (roots.size == 1) "$variable = ${fmt(roots[0])}"
               else roots.mapIndexed { i, r -> "$variable${subscript(i + 1)} = ${fmt(r)}" }.joinToString("\n")
    }

    private fun subscript(n: Int) = when (n) { 1 -> "₁"; 2 -> "₂"; 3 -> "₃"; 4 -> "₄"; else -> n.toString() }

    private fun fmt(v: Double): String {
        val r = (v * 1e10).toLong() / 1e10
        return if (r == r.toLong().toDouble() && abs(r) < 1e12) r.toLong().toString()
               else if (abs(r) >= 1e10 || (abs(r) < 1e-4 && r != 0.0)) "%.6e".format(r)
               else "%.8f".format(r).trimEnd('0').trimEnd('.')
    }

    private fun Double.pow(n: Double) = Math.pow(this, n)
    private fun cbrt(x: Double) = if (x < 0) -(-x).pow(1.0 / 3) else x.pow(1.0 / 3)

    companion object { private const val EPSILON = 1e-12 }
}
