package com.example.calculator.engine

import net.objecthunter.exp4j.function.Function
import kotlin.math.*

/**
 * Provides angle-mode-aware trigonometric and hyperbolic functions as exp4j [Function] objects
 * that can be registered into any [ExpressionBuilder].
 *
 * All forward trig functions (sin/cos/tan/sec/csc/cot) convert the input angle from the
 * active [AngleMode] to radians before computation.
 * All inverse trig functions (asin/acos/atan/…) convert their radian output back to the
 * active [AngleMode] before returning.
 * Hyperbolic functions operate on dimensionless ratios and are angle-mode-independent.
 */
class TrigEngine(var angleMode: AngleMode = AngleMode.RADIANS) {

    // ── Angle conversion helpers ────────────────────────────────────────────

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

    // ── exp4j Function factory ──────────────────────────────────────────────

    /**
     * Returns every trig/hyperbolic function as an [Array] of exp4j [Function] objects
     * ready to be spread into [ExpressionBuilder.functions()].
     */
    fun getExp4jFunctions(): Array<Function> = arrayOf(

        // ── Forward trig ──────────────────────────────────────────────────
        object : Function("sin", 1) {
            override fun apply(vararg args: Double) = sin(toRadians(args[0]))
        },
        object : Function("cos", 1) {
            override fun apply(vararg args: Double) = cos(toRadians(args[0]))
        },
        object : Function("tan", 1) {
            override fun apply(vararg args: Double): Double {
                val r = toRadians(args[0])
                return tan(r)
            }
        },
        object : Function("sec", 1) {
            override fun apply(vararg args: Double) = 1.0 / cos(toRadians(args[0]))
        },
        object : Function("csc", 1) {
            override fun apply(vararg args: Double) = 1.0 / sin(toRadians(args[0]))
        },
        object : Function("cot", 1) {
            override fun apply(vararg args: Double) = cos(toRadians(args[0])) / sin(toRadians(args[0]))
        },

        // ── Inverse trig ──────────────────────────────────────────────────
        object : Function("asin", 1) {
            override fun apply(vararg args: Double): Double {
                require(args[0] in -1.0..1.0) { "asin domain: [-1, 1]" }
                return fromRadians(asin(args[0]))
            }
        },
        object : Function("arcsin", 1) {   // alias
            override fun apply(vararg args: Double) = fromRadians(asin(args[0].coerceIn(-1.0, 1.0)))
        },
        object : Function("acos", 1) {
            override fun apply(vararg args: Double): Double {
                require(args[0] in -1.0..1.0) { "acos domain: [-1, 1]" }
                return fromRadians(acos(args[0]))
            }
        },
        object : Function("arccos", 1) {
            override fun apply(vararg args: Double) = fromRadians(acos(args[0].coerceIn(-1.0, 1.0)))
        },
        object : Function("atan", 1) {
            override fun apply(vararg args: Double) = fromRadians(atan(args[0]))
        },
        object : Function("arctan", 1) {
            override fun apply(vararg args: Double) = fromRadians(atan(args[0]))
        },
        object : Function("atan2", 2) {
            // atan2(y, x) – argument order matches kotlin.math.atan2
            override fun apply(vararg args: Double) = fromRadians(atan2(args[0], args[1]))
        },

        // ── Hyperbolic (angle-mode-independent) ───────────────────────────
        object : Function("sinh", 1) {
            override fun apply(vararg args: Double) = sinh(args[0])
        },
        object : Function("cosh", 1) {
            override fun apply(vararg args: Double) = cosh(args[0])
        },
        object : Function("tanh", 1) {
            override fun apply(vararg args: Double) = tanh(args[0])
        },

        // ── Inverse hyperbolic ────────────────────────────────────────────
        object : Function("asinh", 1) {
            override fun apply(vararg args: Double) = ln(args[0] + sqrt(args[0] * args[0] + 1.0))
        },
        object : Function("arcsinh", 1) {
            override fun apply(vararg args: Double) = ln(args[0] + sqrt(args[0] * args[0] + 1.0))
        },
        object : Function("acosh", 1) {
            override fun apply(vararg args: Double): Double {
                require(args[0] >= 1.0) { "acosh domain: [1, ∞)" }
                return ln(args[0] + sqrt(args[0] * args[0] - 1.0))
            }
        },
        object : Function("arccosh", 1) {
            override fun apply(vararg args: Double) = ln(args[0] + sqrt(args[0] * args[0] - 1.0))
        },
        object : Function("atanh", 1) {
            override fun apply(vararg args: Double): Double {
                require(abs(args[0]) < 1.0) { "atanh domain: (-1, 1)" }
                return 0.5 * ln((1.0 + args[0]) / (1.0 - args[0]))
            }
        },
        object : Function("arctanh", 1) {
            override fun apply(vararg args: Double) = 0.5 * ln((1.0 + args[0]) / (1.0 - args[0]))
        }
    )
}
