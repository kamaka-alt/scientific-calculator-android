package com.example.calculator.engine

/**
 * Base-N arithmetic and bitwise logic.
 *
 * **Supported literal prefixes**
 * | Prefix | Base        | Example      |
 * |--------|-------------|--------------|
 * | `0b`   | Binary (2)  | `0b1010`     |
 * | `0o`   | Octal  (8)  | `0o17`       |
 * | `0d`   | Decimal(10) | `0d42`       |
 * | `0x`   | Hex   (16)  | `0xFF`       |
 * | none   | Decimal     | `42`         |
 *
 * **Bitwise operators** (upper-case, space-separated)
 * `AND`, `OR`, `XOR`, `NAND`, `NOR`, `XNOR`, `NOT`, `SHL`, `SHR`, `ROL`, `ROR`
 *
 * **Conversion shorthand** (UI convenience)
 * `toBin(42)`, `toOct(42)`, `toHex(42)`, `toDec(0xFF)` etc.
 *
 * All computations use signed 64-bit integers ([Long]).
 * Results are displayed in all four bases simultaneously.
 */
class BaseNEngine {

    // ── Entry point ──────────────────────────────────────────────────────────

    fun evaluate(expression: String): String {
        val expr = expression.trim()
        return try {
            when {
                // Conversion helpers
                expr.matches(Regex("to(Bin|Oct|Dec|Hex)\\(.+\\)", RegexOption.IGNORE_CASE)) ->
                    convertHelper(expr)

                // Unary NOT
                Regex("^NOT\\s+", RegexOption.IGNORE_CASE).containsMatchIn(expr) ->
                    applyNot(expr.removePrefix("NOT").removePrefix("not").trim())

                // Binary bitwise ops
                BITWISE_OPS.any { expr.contains(" $it ", ignoreCase = true) } ->
                    applyBitwise(expr)

                // Shift with integer on RHS
                expr.contains(" SHL ", ignoreCase = true) || expr.contains(" SHR ", ignoreCase = true) ->
                    applyBitwise(expr)

                // Prefixed literals — convert and display
                expr.startsWith("0b", true) -> displayAll(parseLiteral(expr))
                expr.startsWith("0o", true) -> displayAll(parseLiteral(expr))
                expr.startsWith("0d", true) -> displayAll(parseLiteral(expr))
                expr.startsWith("0x", true) -> displayAll(parseLiteral(expr))

                // Plain decimal expression (e.g. "255")
                else -> displayAll(expr.toLong())
            }
        } catch (e: Exception) {
            "BaseN error: ${e.message}"
        }
    }

    // ── Public utilities ─────────────────────────────────────────────────────

    /**
     * Render [decimal] in all four bases.
     *
     * ```
     * DEC : 42
     * BIN : 101010
     * OCT : 52
     * HEX : 2A
     * ```
     */
    fun displayAll(decimal: Long): String = buildString {
        appendLine("DEC : $decimal")
        appendLine("BIN : ${decimal.toString(2)}")
        appendLine("OCT : ${decimal.toString(8)}")
        append("HEX : ${decimal.toString(16).uppercase()}")
    }

    /** Convert [value] from [fromBase] to [toBase], both in 2..36. */
    fun convert(value: String, fromBase: Int, toBase: Int): String {
        val decimal = value.lowercase().toLong(fromBase)
        return decimal.toString(toBase).uppercase()
    }

    /** Parse any supported literal (with or without prefix) and return its [Long] value. */
    fun parseLiteral(token: String): Long = when {
        token.startsWith("0b", true) -> token.substring(2).toLong(2)
        token.startsWith("0o", true) -> token.substring(2).toLong(8)
        token.startsWith("0d", true) -> token.substring(2).toLong(10)
        token.startsWith("0x", true) -> token.substring(2).toLong(16)
        else                          -> token.toLong()
    }

    // ── Bitwise operations ────────────────────────────────────────────────────

    private fun applyBitwise(expr: String): String {
        val op = BITWISE_OPS.first { expr.contains(" $it ", ignoreCase = true) }
        val parts = expr.split(Regex("\\s+${op}\\s+", RegexOption.IGNORE_CASE))
        require(parts.size == 2) { "Expected: operand $op operand" }
        val a = parseLiteral(parts[0].trim())
        val b = parseLiteral(parts[1].trim())
        val result = when (op.uppercase()) {
            "AND"  -> a and b
            "OR"   -> a or b
            "XOR"  -> a xor b
            "NAND" -> (a and b).inv()
            "NOR"  -> (a or b).inv()
            "XNOR" -> (a xor b).inv()
            "SHL"  -> a shl b.toInt()
            "SHR"  -> a shr b.toInt()
            "ROL"  -> java.lang.Long.rotateLeft(a, b.toInt())
            "ROR"  -> java.lang.Long.rotateRight(a, b.toInt())
            else   -> throw IllegalArgumentException("Unknown op $op")
        }
        return "${op.uppercase()}:\n${displayAll(result)}"
    }

    private fun applyNot(operand: String): String {
        val value = parseLiteral(operand)
        return "NOT:\n${displayAll(value.inv())}"
    }

    private fun convertHelper(expr: String): String {
        val base = expr.substring(2, 5).uppercase()   // "Bin", "Oct", "Dec", "Hex"
        val inner = expr.substringAfter('(').substringBefore(')')
        val value = parseLiteral(inner.trim())
        return when (base) {
            "BIN" -> "BIN: ${value.toString(2)}"
            "OCT" -> "OCT: ${value.toString(8)}"
            "HEX" -> "HEX: ${value.toString(16).uppercase()}"
            "DEC" -> "DEC: $value"
            else  -> throw IllegalArgumentException("Unknown base: $base")
        }
    }

    companion object {
        val BITWISE_OPS = listOf("AND", "OR", "XOR", "NAND", "NOR", "XNOR", "SHL", "SHR", "ROL", "ROR")
    }
}
