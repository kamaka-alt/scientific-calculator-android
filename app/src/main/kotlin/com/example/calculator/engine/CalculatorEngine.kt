package com.example.calculator.engine

import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function as Exp4jFunction
import kotlin.math.*

/**
 * Central expression router and evaluator.
 *
 * Call [evaluate] with any raw string from the Android UI.  The engine inspects the
 * expression, routes it to the correct specialist module, and returns a formatted
 * [Result] ready to display.
 *
 * **Routing table (checked top-down)**
 * | Prefix / pattern                          | Engine                  |
 * |-------------------------------------------|-------------------------|
 * | `∫(…)` / `integrate(…)` / `d/dx(…)` / `diff(…)` | [CalculusEngine]  |
 * | `Pol(…)` / `Rec(…)`                       | [CoordinateEngine]      |
 * | `solve(…)`                                | [EquationSolver]        |
 * | `det(` / `trans(` / `inv(` / `cross(` …  | [MatrixEngine]          |
 * | `[[…]]` matrix literal                    | [MatrixEngine]          |
 * | `dot(` / `cross(` / `norm(` / `angle(`   | [MatrixEngine]          |
 * | `…nCr…` / `…nPr…` / `…!`                 | [CombinatoricsEngine]   |
 * | Complex expression (contains bare `i`)    | [ComplexEngine]         |
 * | `0b…` / `0o…` / `0x…` / `… AND …` …      | [BaseNEngine]           |
 * | `const:…`                                 | [ConstantsEngine]       |
 * | `convert:…`                               | [ConversionEngine]      |
 * | `stat1:…`                                 | [StatisticsEngine] 1-var|
 * | `stat2:…`                                 | [StatisticsEngine] 2-var|
 * | anything else                             | exp4j expression builder|
 *
 * **exp4j custom functions registered**
 * `sin cos tan asin acos atan atan2 arcsin arccos arctan`
 * `sec csc cot sinh cosh tanh asinh acosh atanh arcsinh arccosh arctanh`
 * `sqrt cbrt log10 ln log2 logb abs ceil floor round sign`
 * `exp pow10 factorial ncr npr rand randint`
 *
 * **Pre-processing replacements**
 * `×→*  ÷→/  π→PI  e→E  %→/100  √(→sqrt(  ∛(→cbrt(  5!→factorial(5)
 *  5 nCr 3→ncr(5,3)  5 nPr 3→npr(5,3)`
 */
class CalculatorEngine(initialAngleMode: AngleMode = AngleMode.RADIANS) {

    // ── Sub-engines ──────────────────────────────────────────────────────────

    val trigEngine        = TrigEngine(initialAngleMode)
    val calculusEngine    = CalculusEngine(this)
    val combinatorics     = CombinatoricsEngine()
    val complexEngine     = ComplexEngine()
    val statisticsEngine  = StatisticsEngine()
    val baseNEngine       = BaseNEngine()
    val matrixEngine      = MatrixEngine()
    val coordinateEngine  = CoordinateEngine(initialAngleMode)
    val conversionEngine  = ConversionEngine()
    val equationSolver    = EquationSolver(this, calculusEngine)

    var angleMode: AngleMode = initialAngleMode
        set(value) {
            field = value
            trigEngine.angleMode      = value
            coordinateEngine.angleMode = value
        }

    // Stores the last successfully evaluated result (accessed via `ans`)
    var lastAnswer: Double = 0.0

    // ── Public entry point ───────────────────────────────────────────────────

    sealed class Result {
        data class Value(val display: String, val numeric: Double?) : Result()
        data class Text(val display: String)                        : Result()
        data class Error(val message: String)                       : Result()
    }

    /**
     * Evaluate [expression] and return a [Result].
     *
     * The caller (ViewModel / Activity) should check the type:
     * ```kotlin
     * when (val r = engine.evaluate(input)) {
     *     is Result.Value -> showResult(r.display)
     *     is Result.Text  -> showText(r.display)
     *     is Result.Error -> showError(r.message)
     * }
     * ```
     */
    fun evaluate(expression: String): Result {
        val raw = expression.trim()
        if (raw.isEmpty()) return Result.Error("Empty expression")

        return try {
            when {
                // ── Calculus ──────────────────────────────────────────────
                raw.startsWith("∫")               ||
                raw.startsWith("integrate(",  true)||
                raw.startsWith("d/dx(",       true)||
                raw.startsWith("diff(",       true)  -> Result.Text(calculus(raw))

                // ── Coordinate conversion ──────────────────────────────────
                raw.startsWith("Pol(", true)          -> Result.Text(coordinateEngine.toPolar(raw))
                raw.startsWith("Rec(", true)          -> Result.Text(coordinateEngine.toRectangular(raw))

                // ── Equation solver ────────────────────────────────────────
                raw.startsWith("solve(", true)        -> Result.Text(equationSolver.solve(raw))

                // ── Matrix / vector ────────────────────────────────────────
                isMatrixExpression(raw)               -> Result.Text(matrixEngine.evaluate(raw))

                // ── Combinatorics (standalone) ─────────────────────────────
                isCombinatoricsExpr(raw)              -> Result.Text(combinatorics.evaluate(raw))

                // ── Complex numbers ────────────────────────────────────────
                isComplexExpression(raw)              -> Result.Text(complexEngine.evaluate(raw))

                // ── Base-N / bitwise ──────────────────────────────────────
                isBaseNExpression(raw)                -> Result.Text(baseNEngine.evaluate(raw))

                // ── Named prefixes ─────────────────────────────────────────
                raw.startsWith("const:",  true)       ->
                    Result.Text(ConstantsEngine.lookup(raw.removePrefix("const:").removePrefix("CONST:").trim()))

                raw.startsWith("convert:", true)      ->
                    Result.Text(conversionEngine.convert(raw.removePrefix("convert:").removePrefix("CONVERT:").trim()))

                raw.startsWith("stat1:",  true)       ->
                    Result.Text(statisticsEngine.oneStat(raw.removePrefix("stat1:").removePrefix("STAT1:").trim()))

                raw.startsWith("stat2:",  true)       ->
                    Result.Text(statisticsEngine.twoStat(raw.removePrefix("stat2:").removePrefix("STAT2:").trim()))

                // ── Basic expression (exp4j) ──────────────────────────────
                else -> {
                    val numeric = evaluateDouble(raw)
                    lastAnswer = numeric
                    Result.Value(formatResult(numeric), numeric)
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ── Core numeric evaluation (exp4j) ──────────────────────────────────────

    /**
     * Evaluate [expression] to a [Double].
     * Used by [CalculusEngine], [EquationSolver], and the basic evaluation path.
     *
     * Throws [ArithmeticException] / [IllegalArgumentException] on parse or math errors.
     */
    fun evaluateDouble(expression: String): Double {
        val processed = preprocess(expression)
        return buildExp4jExpression(processed).evaluate()
    }

    /** Format a double result for display (avoids spurious trailing decimals). */
    fun formatResult(value: Double): String {
        if (!value.isFinite()) return when {
            value.isNaN()              -> "Not a number"
            value == Double.POSITIVE_INFINITY -> "∞"
            else                       -> "-∞"
        }
        return when {
            value == 0.0 -> "0"
            abs(value) >= 1e15 || (abs(value) < 1e-9 && value != 0.0) ->
                "%.10e".format(value).trimEnd('0').replace(Regex("e\\+0*(\\d)"), "e+$1")
                                                  .replace(Regex("e-0*(\\d)"),  "e-$1")
            else -> {
                val rounded = (value * 1e10).roundToLong() / 1e10
                if (rounded == rounded.toLong().toDouble() && abs(rounded) < 1e13)
                    rounded.toLong().toString()
                else
                    "%.10f".format(rounded).trimEnd('0').trimEnd('.')
            }
        }
    }

    // ── Pre-processing ────────────────────────────────────────────────────────

    private fun preprocess(expr: String): String {
        var s = expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")   // Unicode minus
            .replace("π", PI.toString())
            // Protect 'e' in scientific notation: 1.5e10 → keep; lone 'e' → Euler's number
            .replace(Regex("""(?<![.\deE])\be\b(?![.\d])"""), E.toString())
            .replace("%", "/100")
            .replace("√(", "sqrt(")
            .replace("∛(", "cbrt(")
            .replace("²", "^2")
            .replace("³", "^3")
            .replace("⁻¹", "^(-1)")

        // Expand `n!` → `factorial(n)` (n is a number or closing paren)
        s = s.replace(Regex("""(\d+(?:\.\d+)?|(?<=\)))\s*!""")) { m ->
            "factorial(${m.groupValues[1]})"
        }

        // Expand `n nCr r` → `ncr(n,r)` and `n nPr r` → `npr(n,r)`
        s = s.replace(Regex("""(\d+)\s+nCr\s+(\d+)""", RegexOption.IGNORE_CASE)) { m ->
            "ncr(${m.groupValues[1]},${m.groupValues[2]})"
        }
        s = s.replace(Regex("""(\d+)\s+nPr\s+(\d+)""", RegexOption.IGNORE_CASE)) { m ->
            "npr(${m.groupValues[1]},${m.groupValues[2]})"
        }

        // Insert implicit multiplication: 2π → 2*π, 3sin(… → 3*sin(…, )(… → )*(…
        s = s.replace(Regex("""(\d)(sqrt|cbrt|sin|cos|tan|ln|log|exp|abs)""")) { m ->
            "${m.groupValues[1]}*${m.groupValues[2]}"
        }
        s = s.replace(Regex("""\)\("""), ")*(")
        s = s.replace(Regex("""(\d)\(""")) { m -> "${m.groupValues[1]}*(" }

        return s
    }

    // ── exp4j expression builder ──────────────────────────────────────────────

    private fun buildExp4jExpression(processed: String) =
        ExpressionBuilder(processed)
            .functions(
                // Trig (angle-mode aware)
                *trigEngine.getExp4jFunctions(),

                // Logarithm / exponential
                exp4jFn("log10", 1) { log10(it[0]) },
                exp4jFn("ln",    1) { ln(it[0]) },
                exp4jFn("log2",  1) { log2(it[0]) },
                exp4jFn("logb",  2) { log(it[1], it[0]) },  // logb(base, x)
                exp4jFn("exp",   1) { exp(it[0]) },
                exp4jFn("pow10", 1) { 10.0.pow(it[0]) },

                // Roots
                exp4jFn("sqrt", 1) { sqrt(it[0]) },
                exp4jFn("cbrt", 1) { v -> if (v[0] < 0) -Math.pow(-v[0], 1.0/3.0) else Math.pow(v[0], 1.0/3.0) },
                exp4jFn("nroot",2) { it[0].pow(1.0 / it[1]) },   // nroot(x, n) = x^(1/n)

                // Misc math
                exp4jFn("abs",   1) { abs(it[0]) },
                exp4jFn("ceil",  1) { ceil(it[0]) },
                exp4jFn("floor", 1) { floor(it[0]) },
                exp4jFn("round", 1) { it[0].roundToLong().toDouble() },
                exp4jFn("sign",  1) { sign(it[0]) },
                exp4jFn("min",   2) { minOf(it[0], it[1]) },
                exp4jFn("max",   2) { maxOf(it[0], it[1]) },
                exp4jFn("gcd",   2) { gcd(it[0].toLong(), it[1].toLong()).toDouble() },
                exp4jFn("lcm",   2) { lcm(it[0].toLong(), it[1].toLong()).toDouble() },
                exp4jFn("mod",   2) { it[0] % it[1] },
                exp4jFn("hypot", 2) { hypot(it[0], it[1]) },

                // Combinatorics (also available in standalone mode)
                exp4jFn("factorial", 1) { combinatorics.factorialDouble(it[0].toLong()) },
                exp4jFn("ncr",       2) { combinatorics.nCr(it[0].toLong(), it[1].toLong()).toDouble() },
                exp4jFn("npr",       2) { combinatorics.nPr(it[0].toLong(), it[1].toLong()).toDouble() },
                exp4jFn("gamma",     1) { combinatorics.gamma(it[0]) },
                exp4jFn("rand",      0) { Math.random() },
                exp4jFn("randint",   2) { (it[0].toInt()..it[1].toInt()).random().toDouble() }
            )
            .variables("ans", "Ans")
            .build()
            .setVariable("ans", lastAnswer)
            .setVariable("Ans", lastAnswer)

    // ── Routing predicates ────────────────────────────────────────────────────

    private fun isMatrixExpression(expr: String): Boolean {
        val low = expr.lowercase()
        return expr.trimStart().startsWith("[[") ||
               low.startsWith("det(")   || low.startsWith("trans(")  ||
               low.startsWith("inv(")   || low.startsWith("trace(")  ||
               low.startsWith("rank(")  || low.startsWith("eigen(")  ||
               low.startsWith("dot(")   || low.startsWith("cross(")  ||
               low.startsWith("norm(")  || low.startsWith("angle(")  ||
               low.startsWith("proj(")
    }

    private fun isCombinatoricsExpr(expr: String): Boolean {
        return (expr.trimEnd().endsWith("!") && !expr.contains("!=")) ||
               expr.contains("nCr", ignoreCase = true) ||
               expr.contains("nPr", ignoreCase = true)
    }

    /**
     * Heuristic: expression is complex if it contains a bare 'i' — that is,
     * 'i' not embedded inside a function name like "sin", "sinh", "inv", "pi".
     */
    private fun isComplexExpression(expr: String): Boolean {
        // Replace known function-name substrings to avoid false positives
        val masked = expr
            .replace(Regex("(sin|cos|tan|arcsin|arccos|arctan|sinh|cosh|tanh|inverse|inv|sqrt|cbrt|pi|Pi|PI)", RegexOption.IGNORE_CASE), "FN")
        return Regex("""(?<![a-zA-Z_\d])[+-]?(\d*\.?\d+)?i(?![a-zA-Z_\d])""").containsMatchIn(masked)
    }

    private fun isBaseNExpression(expr: String): Boolean {
        val up = expr.uppercase()
        return expr.startsWith("0b", true) || expr.startsWith("0o", true) ||
               expr.startsWith("0x", true) || expr.startsWith("toBin", true) ||
               expr.startsWith("toOct", true) || expr.startsWith("toHex", true) ||
               BaseNEngine.BITWISE_OPS.any { up.contains(" $it ") }
    }

    private fun calculus(raw: String): String =
        if (raw.startsWith("∫") || raw.startsWith("integrate", true))
            calculusEngine.integrate(raw)
        else
            calculusEngine.differentiate(raw)

    // ── Private utilities ────────────────────────────────────────────────────

    private fun exp4jFn(name: String, argc: Int, body: (DoubleArray) -> Double): Exp4jFunction =
        object : Exp4jFunction(name, argc) {
            override fun apply(vararg args: Double) = body(args)
        }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)
    private fun lcm(a: Long, b: Long): Long = abs(a / gcd(a, b) * b)
    private fun Double.pow(n: Double)       = Math.pow(this, n)
}

