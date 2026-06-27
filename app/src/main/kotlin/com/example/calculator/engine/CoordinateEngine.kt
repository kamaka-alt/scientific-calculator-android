package com.example.calculator.engine

import kotlin.math.*

/**
 * Coordinate system conversions.
 *
 * All angle inputs/outputs honour the active [AngleMode]:
 * - [AngleMode.DEGREES]  → angles in degrees  (0 – 360)
 * - [AngleMode.RADIANS]  → angles in radians  (0 – 2π)
 * - [AngleMode.GRADIANS] → angles in gradians (0 – 400)
 *
 * **2-D conversions**
 * ```
 * Pol(x, y)      → r = √(x²+y²),  θ = atan2(y,x)
 * Rec(r, θ)      → x = r·cosθ,    y = r·sinθ
 * ```
 *
 * **3-D conversions**
 * ```
 * CylToCart(r, θ, z)     → (x, y, z)
 * CartToCyl(x, y, z)     → (r, θ, z)
 * SphToCart(r, θ, φ)     → (x, y, z)   (θ = azimuth, φ = polar from +z)
 * CartToSph(x, y, z)     → (r, θ, φ)
 * ```
 */
class CoordinateEngine(var angleMode: AngleMode = AngleMode.RADIANS) {

    // ── Entry points (called by CalculatorEngine) ────────────────────────────

    /**
     * `Pol(x, y)` → polar form.
     * Stores r in ANS and θ in a secondary register (engine-level concern).
     */
    fun toPolar(expression: String): String {
        val (x, y) = parseTwoArgs(expression, "Pol")
        val r      = sqrt(x * x + y * y)
        val theta  = fromRadians(atan2(y, x))
        return "r = ${fmt(r)}\nθ = ${fmt(theta)}"
    }

    /**
     * `Rec(r, θ)` → rectangular form.
     */
    fun toRectangular(expression: String): String {
        val (r, thetaIn) = parseTwoArgs(expression, "Rec")
        val thetaRad     = toRadians(thetaIn)
        val x            = r * cos(thetaRad)
        val y            = r * sin(thetaRad)
        return "x = ${fmt(x)}\ny = ${fmt(y)}"
    }

    // ── 3-D convenience methods ───────────────────────────────────────────────

    /**
     * Cylindrical (r, θ, z) → Cartesian (x, y, z).
     * `θ` is the azimuthal angle from the +x axis.
     */
    fun cylindricalToCartesian(r: Double, theta: Double, z: Double): Triple<Double, Double, Double> {
        val tRad = toRadians(theta)
        return Triple(r * cos(tRad), r * sin(tRad), z)
    }

    /** Cartesian (x, y, z) → Cylindrical (r, θ, z). */
    fun cartesianToCylindrical(x: Double, y: Double, z: Double): Triple<Double, Double, Double> =
        Triple(sqrt(x * x + y * y), fromRadians(atan2(y, x)), z)

    /**
     * Spherical (r, θ, φ) → Cartesian (x, y, z).
     * Physics convention: θ = azimuth (longitude), φ = polar angle from +z (colatitude).
     */
    fun sphericalToCartesian(r: Double, theta: Double, phi: Double): Triple<Double, Double, Double> {
        val tRad = toRadians(theta)
        val pRad = toRadians(phi)
        return Triple(
            r * sin(pRad) * cos(tRad),
            r * sin(pRad) * sin(tRad),
            r * cos(pRad)
        )
    }

    /** Cartesian (x, y, z) → Spherical (r, θ, φ). */
    fun cartesianToSpherical(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val r     = sqrt(x * x + y * y + z * z)
        val theta = fromRadians(atan2(y, x))
        val phi   = fromRadians(acos((z / r).coerceIn(-1.0, 1.0)))
        return Triple(r, theta, phi)
    }

    // ── Format a 3-D result ───────────────────────────────────────────────────

    fun format3D(label1: String, v1: Double, label2: String, v2: Double,
                 label3: String, v3: Double): String =
        "$label1 = ${fmt(v1)}\n$label2 = ${fmt(v2)}\n$label3 = ${fmt(v3)}"

    // ── Angle helpers (public so CalculusEngine/EquationSolver can reuse) ────

    fun toRadians(value: Double): Double = when (angleMode) {
        AngleMode.DEGREES  -> Math.toRadians(value)
        AngleMode.RADIANS  -> value
        AngleMode.GRADIANS -> value * PI / 200.0
    }

    fun fromRadians(radians: Double): Double = when (angleMode) {
        AngleMode.DEGREES  -> Math.toDegrees(radians)
        AngleMode.RADIANS  -> radians
        AngleMode.GRADIANS -> radians * 200.0 / PI
    }

    // ── Private parsers ───────────────────────────────────────────────────────

    private fun parseTwoArgs(expression: String, funcName: String): Pair<Double, Double> {
        val inner = expression.removePrefix("$funcName(").removeSuffix(")")
        val parts = inner.split(",")
        require(parts.size == 2) { "$funcName requires exactly 2 arguments: $funcName(a, b)" }
        return parts[0].trim().toDouble() to parts[1].trim().toDouble()
    }

    private fun fmt(v: Double): String {
        val r = (v * 1e10).toLong() / 1e10
        return if (r == r.toLong().toDouble()) r.toLong().toString()
               else "%.8f".format(r).trimEnd('0').trimEnd('.')
    }
}
