package com.example.calculator.engine

import kotlin.math.abs

/**
 * NIST CODATA 2022-aligned physical and mathematical constants.
 *
 * **UI lookup syntax** (routed from [CalculatorEngine]):
 * ```
 * const:c          → "Speed of light in vacuum (c) = 2.997924e+08 m/s"
 * const:list       → full categorised listing
 * const:list:Atomic → all constants in the Atomic category
 * ```
 *
 * Each constant also exposes its raw [Double] value via [getValue] so other
 * engines (e.g. [ConversionEngine]) can use it in calculations.
 */
object ConstantsEngine {

    data class PhysicalConstant(
        val key: String,
        val name: String,
        val symbol: String,
        val value: Double,
        val unit: String,
        val category: String,
        val uncertainty: Double = 0.0   // absolute standard uncertainty; 0 = exact
    )

    // ── Constant catalogue ───────────────────────────────────────────────────

    private val catalogue: List<PhysicalConstant> = listOf(

        // ── Universal ────────────────────────────────────────────────────────
        PhysicalConstant("c",     "Speed of light in vacuum",         "c",   299_792_458.0,       "m/s",        "Universal", 0.0),
        PhysicalConstant("h",     "Planck constant",                  "h",   6.62607015e-34,      "J·s",        "Universal", 0.0),
        PhysicalConstant("hbar",  "Reduced Planck constant",          "ℏ",   1.054571817e-34,     "J·s",        "Universal", 0.0),
        PhysicalConstant("G",     "Newtonian gravitational constant",  "G",   6.67430e-11,         "N·m²/kg²",   "Universal", 1.5e-15),
        PhysicalConstant("mu0",   "Vacuum magnetic permeability",     "μ₀",  1.25663706212e-6,    "N/A²",       "Universal", 1.9e-16),
        PhysicalConstant("eps0",  "Vacuum electric permittivity",     "ε₀",  8.8541878128e-12,    "F/m",        "Universal", 1.3e-21),
        PhysicalConstant("Z0",    "Characteristic impedance of vacuum","Z₀", 376.730313668,       "Ω",          "Universal", 0.0),

        // ── Electromagnetic ───────────────────────────────────────────────────
        PhysicalConstant("e",     "Elementary charge",                "e",   1.602176634e-19,     "C",          "Electromagnetic", 0.0),
        PhysicalConstant("F",     "Faraday constant",                 "F",   96_485.33212,        "C/mol",      "Electromagnetic", 0.0),
        PhysicalConstant("alpha", "Fine-structure constant",          "α",   7.2973525693e-3,     "",           "Electromagnetic", 1.1e-12),
        PhysicalConstant("muB",   "Bohr magneton",                    "μ_B", 9.2740100783e-24,    "J/T",        "Electromagnetic", 2.8e-33),
        PhysicalConstant("muN",   "Nuclear magneton",                 "μ_N", 5.0507837461e-27,    "J/T",        "Electromagnetic", 1.5e-36),

        // ── Atomic ────────────────────────────────────────────────────────────
        PhysicalConstant("me",    "Electron mass",                    "mₑ",  9.1093837015e-31,    "kg",         "Atomic", 2.8e-40),
        PhysicalConstant("mp",    "Proton mass",                      "mₚ",  1.67262192369e-27,   "kg",         "Atomic", 5.1e-37),
        PhysicalConstant("mn",    "Neutron mass",                     "mₙ",  1.67492749804e-27,   "kg",         "Atomic", 9.5e-37),
        PhysicalConstant("a0",    "Bohr radius",                      "a₀",  5.29177210903e-11,   "m",          "Atomic", 8.0e-21),
        PhysicalConstant("Ry",    "Rydberg constant",                 "R∞",  10_973_731.568160,   "m⁻¹",        "Atomic", 2.1e-5),
        PhysicalConstant("Eh",    "Hartree energy",                   "Eₕ",  4.3597447222071e-18, "J",          "Atomic", 8.5e-30),
        PhysicalConstant("re",    "Classical electron radius",        "rₑ",  2.8179403227e-15,    "m",          "Atomic", 1.9e-24),

        // ── Thermodynamic ─────────────────────────────────────────────────────
        PhysicalConstant("Na",    "Avogadro constant",                "Nₐ",  6.02214076e23,       "mol⁻¹",      "Thermodynamic", 0.0),
        PhysicalConstant("kb",    "Boltzmann constant",               "k_B", 1.380649e-23,        "J/K",        "Thermodynamic", 0.0),
        PhysicalConstant("R",     "Molar gas constant",               "R",   8.314462618,         "J/(mol·K)",  "Thermodynamic", 0.0),
        PhysicalConstant("sigma", "Stefan–Boltzmann constant",        "σ",   5.670374419e-8,      "W/(m²·K⁴)", "Thermodynamic", 0.0),
        PhysicalConstant("Vm",    "Molar volume of ideal gas (STP)",  "Vₘ",  22.41396954e-3,      "m³/mol",     "Thermodynamic", 0.0),

        // ── Mechanics / Geophysical ───────────────────────────────────────────
        PhysicalConstant("g",     "Standard acceleration of gravity", "g",   9.80665,             "m/s²",       "Mechanics", 0.0),
        PhysicalConstant("atm",   "Standard atmosphere",              "atm", 101_325.0,           "Pa",         "Mechanics", 0.0),

        // ── Mathematical ─────────────────────────────────────────────────────
        PhysicalConstant("pi",    "Pi",                               "π",   Math.PI,             "",           "Mathematical", 0.0),
        PhysicalConstant("eu",    "Euler's number",                   "e",   Math.E,              "",           "Mathematical", 0.0),
        PhysicalConstant("phi",   "Golden ratio",                     "φ",   1.6180339887498948,  "",           "Mathematical", 0.0),
        PhysicalConstant("gamma", "Euler–Mascheroni constant",        "γ",   0.5772156649015329,  "",           "Mathematical", 0.0),
        PhysicalConstant("sqrt2", "Square root of 2",                 "√2",  1.4142135623730951,  "",           "Mathematical", 0.0)
    )

    private val byKey: Map<String, PhysicalConstant> = catalogue.associateBy { it.key }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Look up a constant by its key and return a human-readable string.
     * Also accepts `list` or `list:<Category>`.
     */
    fun lookup(query: String): String {
        val q = query.trim()
        return when {
            q.equals("list", ignoreCase = true) -> listAll()
            q.startsWith("list:", ignoreCase = true) ->
                listByCategory(q.removePrefix("list:").removePrefix("LIST:").trim())
            else -> {
                val c = byKey[q]
                    ?: catalogue.firstOrNull { it.name.equals(q, ignoreCase = true) }
                    ?: return searchByFragment(q)
                formatConstant(c)
            }
        }
    }

    /** Return the raw [Double] value for use in calculations. */
    fun getValue(key: String): Double =
        byKey[key.trim()]?.value
            ?: throw IllegalArgumentException("Unknown constant key: '$key'. Use const:list to browse.")

    /** All keys this engine recognises. */
    val keys: Set<String> get() = byKey.keys

    // ── Private formatting ────────────────────────────────────────────────────

    private fun formatConstant(c: PhysicalConstant): String = buildString {
        append("${c.name} (${c.symbol}) = ${fmtSci(c.value)}")
        if (c.unit.isNotEmpty()) append(" ${c.unit}")
        if (c.uncertainty > 0) append("  [u = ${fmtSci(c.uncertainty)}]")
    }

    private fun listAll(): String =
        catalogue.groupBy { it.category }
                 .entries.joinToString("\n\n") { (cat, consts) ->
                     "── $cat ──\n" + consts.joinToString("\n") { "  [${it.key}]  ${formatConstant(it)}" }
                 }

    private fun listByCategory(cat: String): String {
        val consts = catalogue.filter { it.category.equals(cat, ignoreCase = true) }
        if (consts.isEmpty()) return "No constants found in category '$cat'"
        return "── $cat ──\n" + consts.joinToString("\n") { "  [${it.key}]  ${formatConstant(it)}" }
    }

    private fun searchByFragment(q: String): String {
        val hits = catalogue.filter {
            it.name.contains(q, ignoreCase = true) ||
            it.symbol.contains(q, ignoreCase = true) ||
            it.key.contains(q, ignoreCase = true)
        }
        return if (hits.isEmpty()) "Constant not found: '$q'. Try 'const:list'."
               else hits.joinToString("\n") { "  [${it.key}]  ${formatConstant(it)}" }
    }

    private fun fmtSci(v: Double): String {
        if (v == 0.0) return "0"
        return if (abs(v) >= 1e4 || (abs(v) < 1e-3 && v != 0.0))
            "%.8e".format(v)
        else
            "%.8f".format(v).trimEnd('0').trimEnd('.')
    }
}
