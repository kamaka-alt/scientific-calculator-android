package com.example.calculator.engine

import kotlin.math.*

// ────────────────────────────────────────────────────────────────────────────
// Complex number
// ────────────────────────────────────────────────────────────────────────────

/**
 * Immutable complex number a + bi.
 *
 * Arithmetic operators (+, -, *, /) are provided as operator overloads.
 * Standard complex functions (exp, ln, sin, cos, pow, sqrt) follow the
 * principal-value branch convention.
 */
data class Complex(val re: Double, val im: Double) {

    // ── Arithmetic ────────────────────────────────────────────────────────────
    operator fun plus (other: Complex) = Complex(re + other.re, im + other.im)
    operator fun minus(other: Complex) = Complex(re - other.re, im - other.im)
    operator fun times(other: Complex) = Complex(
        re * other.re - im * other.im,
        re * other.im + im * other.re
    )
    operator fun div(other: Complex): Complex {
        val d = other.re * other.re + other.im * other.im
        require(d > 0) { "Division by zero" }
        return Complex(
            (re * other.re + im * other.im) / d,
            (im * other.re - re * other.im) / d
        )
    }
    operator fun unaryMinus() = Complex(-re, -im)

    // ── Properties ────────────────────────────────────────────────────────────
    /** |z| — modulus (absolute value). */
    fun abs()       = sqrt(re * re + im * im)
    /** Arg(z) — principal argument in (-π, π]. */
    fun arg()       = atan2(im, re)
    /** Complex conjugate z̄ = a - bi. */
    fun conjugate() = Complex(re, -im)

    // ── Transcendental functions ──────────────────────────────────────────────
    fun exp()  = Complex(exp(re) * cos(im), exp(re) * sin(im))
    fun ln()   = Complex(ln(abs()), arg())
    fun sqrt() = pow(0.5)

    /** Principal value z^n, n real. */
    fun pow(n: Double): Complex {
        val r     = abs().pow(n)
        val theta = arg() * n
        return Complex(r * cos(theta), r * sin(theta))
    }
    /** Complex power z^w = exp(w * ln(z)). */
    fun pow(w: Complex) = (w * ln()).exp()

    fun sin()  = Complex(sin(re) * cosh(im),  cos(re) * sinh(im))
    fun cos()  = Complex(cos(re) * cosh(im), -sin(re) * sinh(im))
    fun tan()  = sin() / cos()
    fun sinh() = Complex(sinh(re) * cos(im), cosh(re) * sin(im))
    fun cosh() = Complex(cosh(re) * cos(im), sinh(re) * sin(im))
    fun tanh() = sinh() / cosh()
    fun asin() = -Complex(0.0, 1.0) * ((Complex(0.0, 1.0) * this + (ONE - this * this).sqrt()).ln())
    fun acos() = Complex(PI / 2, 0.0) - this.asin()
    fun atan() = Complex(0.0, 0.5) * ((ONE - Complex(0.0, 1.0) * this).ln() -
                                       (ONE + Complex(0.0, 1.0) * this).ln())

    // ── Formatting ────────────────────────────────────────────────────────────
    override fun toString(): String {
        val rStr = fmt(re)
        val iStr = fmt(abs(im))
        return when {
            abs(im) < 1e-12 -> rStr
            abs(re) < 1e-12 -> "${if (im < 0) "-" else ""}${iStr}i"
            im < 0           -> "$rStr - ${iStr}i"
            else             -> "$rStr + ${iStr}i"
        }
    }

    fun toPolar(): String = "r = ${fmt(abs())}, θ = ${fmt(arg())} rad"

    private fun fmt(v: Double): String {
        val rounded = Math.round(v * 1e10) / 1e10
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
               else "%.8f".format(rounded).trimEnd('0').trimEnd('.')
    }

    companion object {
        val ZERO  = Complex(0.0, 0.0)
        val ONE   = Complex(1.0, 0.0)
        val I     = Complex(0.0, 1.0)
        fun real(r: Double) = Complex(r, 0.0)
        fun fromPolar(r: Double, theta: Double) = Complex(r * cos(theta), r * sin(theta))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ComplexEngine
// ────────────────────────────────────────────────────────────────────────────

/**
 * Evaluates complex-number expressions given as strings.
 *
 * A lightweight recursive-descent parser handles:
 * - Integer and decimal literals            `3`, `2.5`
 * - Imaginary unit                          `i`
 * - Complex literals                        `3+4i`, `2.5-1.2i`, `-i`
 * - Parenthesised sub-expressions          `(1+i)^2`
 * - Binary operators                        `+  -  *  /  ^`
 * - Unary minus                             `-(2+3i)`
 * - Named functions                         `sin cos tan exp ln sqrt abs conj arg Re Im`
 *
 * **Usage:**
 * ```kotlin
 * val ce = ComplexEngine()
 * ce.evaluate("(2+3i)*(1-2i)")   // → "8 - i"
 * ce.evaluate("exp(i*π)")        // → "-1 + 0i"  (Euler's identity)
 * ce.evaluate("sqrt(-1)")        // → "i"
 * ```
 */
class ComplexEngine {

    fun evaluate(expression: String): String {
        return try {
            val normalised = expression
                .replace("π", PI.toString())
                .replace("e", E.toString())
                .replace(" ", "")
            ComplexParser(normalised).parse().toString()
        } catch (e: Exception) {
            "Complex error: ${e.message}"
        }
    }

    // ── Recursive-descent parser ─────────────────────────────────────────────

    private inner class ComplexParser(private val src: String) {
        private var pos = 0

        fun parse(): Complex = parseExpr().also {
            if (pos != src.length) error("Unexpected character '${src[pos]}' at position $pos")
        }

        // expr → term (('+' | '-') term)*
        private fun parseExpr(): Complex {
            var z = parseTerm()
            while (pos < src.length) {
                z = when {
                    eat('+') -> z + parseTerm()
                    eat('-') -> z - parseTerm()
                    else     -> return z
                }
            }
            return z
        }

        // term → pow (('*' | '/') pow)*
        private fun parseTerm(): Complex {
            var z = parsePow()
            while (pos < src.length) {
                z = when {
                    eat('*') -> z * parsePow()
                    eat('/') -> z / parsePow()
                    else     -> return z
                }
            }
            return z
        }

        // pow → unary ('^' unary)?
        private fun parsePow(): Complex {
            val base = parseUnary()
            return if (eat('^')) base.pow(parseUnary()) else base
        }

        // unary → '-' unary | atom
        private fun parseUnary(): Complex {
            if (eat('-')) return -parseUnary()
            if (eat('+')) return parseUnary()
            return parseAtom()
        }

        // atom → '(' expr ')' | func '(' expr ')' | number 'i'? | 'i'
        private fun parseAtom(): Complex {
            if (eat('(')) {
                val z = parseExpr()
                require(eat(')')) { "Expected ')'" }
                return z
            }

            // Named functions
            val func = FUNCS.firstOrNull { src.startsWith(it, pos) }
            if (func != null) {
                pos += func.length
                require(eat('(')) { "Expected '(' after '$func'" }
                val arg = parseExpr()
                require(eat(')')) { "Expected ')'" }
                return applyFunc(func, arg)
            }

            // Bare imaginary unit
            if (pos < src.length && src[pos] == 'i' &&
                (pos + 1 >= src.length || !src[pos + 1].isLetterOrDigit())) {
                pos++
                return Complex.I
            }

            // Number (possibly followed by 'i')
            val numStart = pos
            if (pos < src.length && src[pos].isDigit()) {
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
                if (pos < src.length && src[pos] == 'e') {
                    pos++
                    if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                    while (pos < src.length && src[pos].isDigit()) pos++
                }
                val num = src.substring(numStart, pos).toDouble()
                if (pos < src.length && src[pos] == 'i') { pos++; return Complex(0.0, num) }
                return Complex(num, 0.0)
            }

            error("Expected number or expression at position $pos (near '${src.drop(pos).take(10)}')")
        }

        private fun applyFunc(name: String, z: Complex): Complex = when (name) {
            "sin"  -> z.sin()
            "cos"  -> z.cos()
            "tan"  -> z.tan()
            "sinh" -> z.sinh()
            "cosh" -> z.cosh()
            "tanh" -> z.tanh()
            "exp"  -> z.exp()
            "ln"   -> z.ln()
            "log"  -> z.ln() / Complex(ln(10.0), 0.0)
            "sqrt" -> z.sqrt()
            "abs"  -> Complex.real(z.abs())
            "conj" -> z.conjugate()
            "arg"  -> Complex.real(z.arg())
            "Re"   -> Complex.real(z.re)
            "Im"   -> Complex.real(z.im)
            else   -> error("Unknown function: $name")
        }

        private fun eat(ch: Char): Boolean {
            if (pos < src.length && src[pos] == ch) { pos++; return true }
            return false
        }
    }

    companion object {
        // Ordered longest-first so "sinh" matches before "sin"
        private val FUNCS = listOf("sinh", "cosh", "tanh", "asin", "acos", "atan",
                                   "sin", "cos", "tan", "exp", "ln", "log",
                                   "sqrt", "abs", "conj", "arg", "Re", "Im")
    }
}
