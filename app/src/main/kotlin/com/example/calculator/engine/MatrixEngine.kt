package com.example.calculator.engine

import kotlin.math.*

// ────────────────────────────────────────────────────────────────────────────
// Matrix
// ────────────────────────────────────────────────────────────────────────────

/**
 * Mutable dense matrix stored as an [Array] of [DoubleArray] rows.
 *
 * | Operation  | Method           | Notes                              |
 * |------------|------------------|------------------------------------|
 * | Add / sub  | `+`, `-`         | Must be same dimensions            |
 * | Multiply   | `*` (Matrix)     | Inner dimensions must match        |
 * | Scalar     | `*` (Double)     | element-wise                       |
 * | Transpose  | [transpose]      | O(m·n)                             |
 * | Determinant| [determinant]    | LU decomposition for n ≥ 4        |
 * | Inverse    | [inverse]        | Gauss–Jordan on augmented [A|I]   |
 * | Rank       | [rank]           | Row-echelon form                   |
 * | Trace      | [trace]          | Square matrices only               |
 */
class Matrix(val data: Array<DoubleArray>) {

    val rows: Int = data.size
    val cols: Int = data.firstOrNull()?.size ?: 0

    init { require(data.all { it.size == cols }) { "All rows must have the same length" } }

    operator fun get(i: Int, j: Int)                   = data[i][j]
    operator fun set(i: Int, j: Int, v: Double)        { data[i][j] = v }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    operator fun plus(other: Matrix): Matrix {
        requireSameDims(other, "addition")
        return Matrix(Array(rows) { i -> DoubleArray(cols) { j -> data[i][j] + other[i][j] } })
    }

    operator fun minus(other: Matrix): Matrix {
        requireSameDims(other, "subtraction")
        return Matrix(Array(rows) { i -> DoubleArray(cols) { j -> data[i][j] - other[i][j] } })
    }

    operator fun times(other: Matrix): Matrix {
        require(cols == other.rows) {
            "Incompatible dimensions for multiplication: ${rows}×${cols} × ${other.rows}×${other.cols}"
        }
        return Matrix(Array(rows) { i ->
            DoubleArray(other.cols) { j ->
                (0 until cols).sumOf { k -> data[i][k] * other[k][j] }
            }
        })
    }

    operator fun times(scalar: Double): Matrix =
        Matrix(Array(rows) { i -> DoubleArray(cols) { j -> data[i][j] * scalar } })

    operator fun unaryMinus(): Matrix =
        Matrix(Array(rows) { i -> DoubleArray(cols) { j -> -data[i][j] } })

    // ── Transpose ────────────────────────────────────────────────────────────

    fun transpose(): Matrix =
        Matrix(Array(cols) { i -> DoubleArray(rows) { j -> data[j][i] } })

    // ── Determinant ──────────────────────────────────────────────────────────

    fun determinant(): Double {
        requireSquare("determinant")
        return when (rows) {
            1 -> data[0][0]
            2 -> data[0][0] * data[1][1] - data[0][1] * data[1][0]
            3 -> det3()
            else -> luDeterminant()
        }
    }

    private fun det3() =
        data[0][0] * (data[1][1] * data[2][2] - data[1][2] * data[2][1]) -
        data[0][1] * (data[1][0] * data[2][2] - data[1][2] * data[2][0]) +
        data[0][2] * (data[1][0] * data[2][1] - data[1][1] * data[2][0])

    private fun luDeterminant(): Double {
        val lu = copyData()
        var det = 1.0
        for (col in 0 until rows) {
            val maxRow = (col until rows).maxByOrNull { abs(lu[it][col]) } ?: col
            if (abs(lu[maxRow][col]) < EPSILON) return 0.0
            if (maxRow != col) { lu.swap(col, maxRow); det = -det }
            det *= lu[col][col]
            for (row in col + 1 until rows) {
                val f = lu[row][col] / lu[col][col]
                for (k in col until rows) lu[row][k] -= f * lu[col][k]
            }
        }
        return det
    }

    // ── Inverse ──────────────────────────────────────────────────────────────

    fun inverse(): Matrix {
        requireSquare("inverse")
        val det = determinant()
        require(abs(det) > EPSILON) { "Matrix is singular (det = 0); no inverse exists" }
        val n = rows
        // Build [A | I]
        val aug = Array(n) { i ->
            DoubleArray(2 * n).also { row ->
                data[i].copyInto(row)
                row[n + i] = 1.0
            }
        }
        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            val pivot = (col until n).maxByOrNull { abs(aug[it][col]) } ?: col
            aug.swap(col, pivot)
            val p = aug[col][col]
            require(abs(p) > EPSILON) { "Singular matrix during Gauss–Jordan elimination" }
            for (k in 0 until 2 * n) aug[col][k] /= p
            for (row in 0 until n) {
                if (row == col) continue
                val f = aug[row][col]
                for (k in 0 until 2 * n) aug[row][k] -= f * aug[col][k]
            }
        }
        return Matrix(Array(n) { i -> DoubleArray(n) { j -> aug[i][n + j] } })
    }

    // ── Trace ────────────────────────────────────────────────────────────────

    fun trace(): Double {
        requireSquare("trace")
        return (0 until rows).sumOf { data[it][it] }
    }

    // ── Rank ─────────────────────────────────────────────────────────────────

    fun rank(): Int {
        val mat = copyData()
        var rank = 0
        var col  = 0
        for (row in 0 until rows) {
            if (col >= cols) break
            // Find pivot
            var pivRow = row
            while (pivRow < rows && abs(mat[pivRow][col]) < EPSILON) pivRow++
            if (pivRow == rows) { col++; continue }
            mat.swap(row, pivRow)
            rank++
            val piv = mat[row][col]
            for (k in col until cols) mat[row][k] /= piv
            for (r in 0 until rows) {
                if (r == row) continue
                val f = mat[r][col]
                for (k in col until cols) mat[r][k] -= f * mat[row][k]
            }
            col++
        }
        return rank
    }

    // ── Eigenvalues (2×2 analytic, 3×3 characteristic polynomial) ────────────

    /**
     * Analytic eigenvalues for 2×2 matrices.
     * Returns a [List] of [Complex] eigenvalues.
     */
    fun eigenvalues2x2(): List<Complex> {
        require(rows == 2 && cols == 2) { "eigenvalues2x2 requires a 2×2 matrix" }
        val tr   = trace()
        val det  = determinant()
        val disc = tr * tr - 4 * det
        return if (disc >= 0) {
            listOf(Complex((tr + sqrt(disc)) / 2, 0.0), Complex((tr - sqrt(disc)) / 2, 0.0))
        } else {
            listOf(Complex(tr / 2,  sqrt(-disc) / 2), Complex(tr / 2, -sqrt(-disc) / 2))
        }
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    override fun toString(): String = buildString {
        data.forEach { row ->
            append("│ ")
            append(row.joinToString("  ") { fmtCell(it) })
            appendLine(" │")
        }
    }.trimEnd()

    fun toCsv(): String = data.joinToString("\n") { row -> row.joinToString(",") { "%.6g".format(it) } }

    private fun fmtCell(v: Double): String {
        val r = (v * 1e8).toLong() / 1e8        // round to 8 sig figs
        return if (r == r.toLong().toDouble()) "%-10d".format(r.toLong())
               else "%-10.4f".format(r)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun copyData()                  = Array(rows) { data[it].copyOf() }
    private fun Array<DoubleArray>.swap(i: Int, j: Int) { val t = this[i]; this[i] = this[j]; this[j] = t }
    private fun requireSquare(op: String)   = require(rows == cols) { "$op requires a square matrix (got ${rows}×${cols})" }
    private fun requireSameDims(other: Matrix, op: String) =
        require(rows == other.rows && cols == other.cols) {
            "$op requires identical dimensions (${rows}×${cols} vs ${other.rows}×${other.cols})"
        }

    companion object {
        const val EPSILON = 1e-12

        fun identity(n: Int): Matrix =
            Matrix(Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } })

        fun zeros(rows: Int, cols: Int): Matrix =
            Matrix(Array(rows) { DoubleArray(cols) })

        fun ofRows(vararg rows: DoubleArray): Matrix = Matrix(arrayOf(*rows))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// MatrixEngine
// ────────────────────────────────────────────────────────────────────────────

/**
 * Parses and evaluates matrix / vector expressions from the UI string.
 *
 * **Matrix syntax**: `[[1,2,3],[4,5,6]]`  (rows separated by `],[`)
 *
 * **Supported expression formats**
 * | Input                           | Operation                  |
 * |---------------------------------|----------------------------|
 * | `det([[…]])`                    | Determinant (scalar)       |
 * | `trans([[…]])`                  | Transpose                  |
 * | `inv([[…]])`                    | Inverse                    |
 * | `trace([[…]])`                  | Trace                      |
 * | `rank([[…]])`                   | Rank                       |
 * | `eigen([[…]])`                  | Eigenvalues (2×2 only)     |
 * | `[[…]] + [[…]]`                 | Matrix addition            |
 * | `[[…]] - [[…]]`                 | Matrix subtraction         |
 * | `[[…]] * [[…]]`                 | Matrix multiplication      |
 * | `[[…]] * 3`                     | Scalar multiplication      |
 * | `dot([1,2,3];[4,5,6])`          | Dot product (vectors)      |
 * | `cross([1,2,3];[4,5,6])`        | Cross product (3D vectors) |
 * | `norm([1,2,3])`                 | Vector norm (‖v‖)          |
 * | `angle([1,0,0];[0,1,0])`        | Angle between vectors      |
 * | `proj([1,2];[3,4])`             | Vector projection          |
 */
class MatrixEngine {

    fun evaluate(expression: String): String {
        val expr = expression.trim()
        return try {
            when {
                expr.startsWith("det(",   ignoreCase = true) ->
                    "det = ${fmtS(parseInner(expr, "det").determinant())}"

                expr.startsWith("trans(",  ignoreCase = true) ->
                    parseInner(expr, "trans").transpose().toString()

                expr.startsWith("inv(",    ignoreCase = true) ->
                    parseInner(expr, "inv").inverse().toString()

                expr.startsWith("trace(",  ignoreCase = true) ->
                    "trace = ${fmtS(parseInner(expr, "trace").trace())}"

                expr.startsWith("rank(",   ignoreCase = true) ->
                    "rank = ${parseInner(expr, "rank").rank()}"

                expr.startsWith("eigen(",  ignoreCase = true) -> {
                    val evs = parseInner(expr, "eigen").eigenvalues2x2()
                    evs.mapIndexed { i, v -> "λ${i + 1} = $v" }.joinToString("\n")
                }

                // Vector ops with semicolon separator inside parens
                expr.startsWith("dot(",    ignoreCase = true) -> {
                    val (v1, v2) = parseVectorPair(expr, "dot")
                    "dot product = ${fmtS(dot(v1, v2))}"
                }
                expr.startsWith("cross(",  ignoreCase = true) -> {
                    val (v1, v2) = parseVectorPair(expr, "cross")
                    val r = cross(v1, v2)
                    "[${r[0]}, ${r[1]}, ${r[2]}]"
                }
                expr.startsWith("norm(",   ignoreCase = true) -> {
                    val v = parseVector(expr.removeFunc("norm"))
                    "‖v‖ = ${fmtS(norm(v))}"
                }
                expr.startsWith("angle(",  ignoreCase = true) -> {
                    val (v1, v2) = parseVectorPair(expr, "angle")
                    "angle = ${fmtS(Math.toDegrees(angleBetween(v1, v2)))}°"
                }
                expr.startsWith("proj(",   ignoreCase = true) -> {
                    val (v, onto) = parseVectorPair(expr, "proj")
                    val scalar    = dot(v, onto) / dot(onto, onto)
                    "[${onto.map { fmtS(it * scalar) }.joinToString(", ")}]"
                }

                // Binary matrix ops: detect by presence of top-level operator
                else -> evalBinaryMatrix(expr)
            }
        } catch (e: Exception) {
            "Matrix error: ${e.message}"
        }
    }

    // ── Matrix expression parser ──────────────────────────────────────────────

    /**
     * Parse `[[row0],[row1],…]` or `[elem0, elem1,…]` (single-row / vector).
     */
    fun parseMatrix(input: String): Matrix {
        val s = input.trim()
        require(s.startsWith('[') && s.endsWith(']')) { "Matrix must be wrapped in [ ]" }

        return if (s.contains("],[") || s.contains("], [")) {
            // Multi-row: [[…],[…]]
            val inner = s.removePrefix("[").removeSuffix("]")
            val rows  = inner.split("],[", "], [").map { row ->
                row.trim().removePrefix("[").removeSuffix("]")
                    .split(",").map { it.trim().toDouble() }.toDoubleArray()
            }
            Matrix(rows.toTypedArray())
        } else {
            // Single row / vector: [1,2,3]
            val elems = s.removePrefix("[").removeSuffix("]")
                .split(",").map { it.trim().toDouble() }.toDoubleArray()
            Matrix(arrayOf(elems))
        }
    }

    fun parseVector(input: String): DoubleArray {
        val s = input.trim().removePrefix("[").removeSuffix("]")
        return s.split(",").map { it.trim().toDouble() }.toDoubleArray()
    }

    // ── Vector operations ─────────────────────────────────────────────────────

    fun dot(v1: DoubleArray, v2: DoubleArray): Double {
        require(v1.size == v2.size) { "Dot product: vectors must be the same dimension" }
        return v1.zip(v2.toList()).sumOf { (a, b) -> a * b }
    }

    fun cross(v1: DoubleArray, v2: DoubleArray): DoubleArray {
        require(v1.size == 3 && v2.size == 3) { "Cross product is defined only for 3D vectors" }
        return doubleArrayOf(
            v1[1] * v2[2] - v1[2] * v2[1],
            v1[2] * v2[0] - v1[0] * v2[2],
            v1[0] * v2[1] - v1[1] * v2[0]
        )
    }

    fun norm(v: DoubleArray): Double = sqrt(v.sumOf { it * it })

    fun angleBetween(v1: DoubleArray, v2: DoubleArray): Double =
        acos((dot(v1, v2) / (norm(v1) * norm(v2))).coerceIn(-1.0, 1.0))

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun parseInner(expr: String, func: String): Matrix {
        val inner = expr.drop(func.length + 1).dropLast(1)
        return parseMatrix(inner.trim())
    }

    private fun parseVectorPair(expr: String, func: String): Pair<DoubleArray, DoubleArray> {
        val inner = expr.drop(func.length + 1).dropLast(1)
        val parts = inner.split(";")
        require(parts.size == 2) { "$func requires two vectors separated by ';'" }
        return parseVector(parts[0].trim()) to parseVector(parts[1].trim())
    }

    private fun evalBinaryMatrix(expr: String): String {
        // Detect the first top-level +, -, *
        val op = findTopLevelOp(expr) ?: return parseMatrix(expr).toString()
        val (lhsStr, rhsStr) = expr.splitAt(op.first, op.second)
        val lhs = parseMatrix(lhsStr.trim())
        val opChar = expr[op.first]
        return when {
            opChar == '+' -> (lhs + parseMatrix(rhsStr.trim())).toString()
            opChar == '-' -> (lhs - parseMatrix(rhsStr.trim())).toString()
            opChar == '*' -> {
                val rhs = rhsStr.trim()
                if (rhs.first().isDigit() || rhs.first() == '-')
                    (lhs * rhs.toDouble()).toString()
                else
                    (lhs * parseMatrix(rhs)).toString()
            }
            else -> "Unsupported matrix operator: $opChar"
        }
    }

    /** Find the position of the first top-level binary operator (+,-,*) outside brackets. */
    private fun findTopLevelOp(expr: String): Pair<Int, Int>? {
        var depth = 0
        // Scan right-to-left so +/- precedence is respected
        for (i in expr.indices) {
            when (expr[i]) {
                '[' -> depth++; ']' -> depth--
                '+', '-', '*' -> if (depth == 0 && i > 0) return i to (i + 1)
            }
        }
        return null
    }

    private fun String.splitAt(start: Int, end: Int) = substring(0, start) to substring(end)
    private fun String.removeFunc(func: String) = removePrefix(func).removePrefix("(").removeSuffix(")")
    private fun fmtS(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString()
                                   else "%.6g".format(v)
}
