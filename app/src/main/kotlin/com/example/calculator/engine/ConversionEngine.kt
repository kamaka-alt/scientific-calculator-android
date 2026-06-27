package com.example.calculator.engine

import kotlin.math.abs

/**
 * Metric and imperial unit conversions.
 *
 * **Syntax** (routed via `convert:` prefix in [CalculatorEngine]):
 * ```
 * convert: 100 km to mi          → "100 km = 62.137119 mi"
 * convert: 37 C to F             → "37°C = 98.6°F"
 * convert: 1024 MiB to GiB       → "1024 MiB = 1 GiB"
 * convert: list                  → full unit table
 * convert: list:energy           → energy units only
 * ```
 *
 * All non-temperature conversions use a multiplicative factor to/from SI.
 * Temperature uses affine functions ([convertTemperature]).
 *
 * Categories: length · mass · time · energy · power · pressure · speed
 *             · force · area · volume · angle · data · fuel_economy
 */
class ConversionEngine {

    data class UnitDef(
        val name: String,
        val toSI: Double,        // value × toSI = SI value
        val siUnit: String,
        val aliases: List<String> = emptyList()
    )

    // ── Unit tables (SI base per category) ───────────────────────────────────

    private val tables: Map<String, Map<String, UnitDef>> = mapOf(

        "length" to mapOf(   // base: metre
            "m"    to UnitDef("metre",                1.0,              "m"),
            "km"   to UnitDef("kilometre",            1_000.0,          "m"),
            "cm"   to UnitDef("centimetre",           0.01,             "m"),
            "mm"   to UnitDef("millimetre",           1e-3,             "m"),
            "um"   to UnitDef("micrometre",           1e-6,             "m", listOf("µm")),
            "nm"   to UnitDef("nanometre",            1e-9,             "m"),
            "pm"   to UnitDef("picometre",            1e-12,            "m"),
            "fm"   to UnitDef("femtometre",           1e-15,            "m"),
            "in"   to UnitDef("inch",                 0.0254,           "m", listOf("inch")),
            "ft"   to UnitDef("foot",                 0.3048,           "m", listOf("feet")),
            "yd"   to UnitDef("yard",                 0.9144,           "m"),
            "mi"   to UnitDef("mile",                 1_609.344,        "m", listOf("mile")),
            "nmi"  to UnitDef("nautical mile",        1_852.0,          "m"),
            "au"   to UnitDef("astronomical unit",    1.495978707e11,   "m"),
            "ly"   to UnitDef("light-year",           9.4607304725808e15,"m"),
            "pc"   to UnitDef("parsec",               3.085677581e16,   "m"),
            "Å"    to UnitDef("ångström",             1e-10,            "m", listOf("angstrom"))
        ),

        "mass" to mapOf(   // base: kilogram
            "kg"   to UnitDef("kilogram",             1.0,              "kg"),
            "g"    to UnitDef("gram",                 1e-3,             "kg"),
            "mg"   to UnitDef("milligram",            1e-6,             "kg"),
            "ug"   to UnitDef("microgram",            1e-9,             "kg", listOf("µg")),
            "t"    to UnitDef("metric tonne",         1_000.0,          "kg"),
            "lb"   to UnitDef("pound",                0.45359237,       "kg", listOf("lbs")),
            "oz"   to UnitDef("ounce",                0.028349523125,   "kg"),
            "st"   to UnitDef("stone",                6.35029318,       "kg"),
            "ton"  to UnitDef("short ton (US)",       907.18474,        "kg"),
            "lton" to UnitDef("long ton (UK)",        1_016.0469088,    "kg"),
            "u"    to UnitDef("atomic mass unit",     1.66053906660e-27,"kg", listOf("Da","amu"))
        ),

        "time" to mapOf(   // base: second
            "s"    to UnitDef("second",               1.0,              "s", listOf("sec")),
            "ms"   to UnitDef("millisecond",          1e-3,             "s"),
            "us"   to UnitDef("microsecond",          1e-6,             "s", listOf("µs")),
            "ns"   to UnitDef("nanosecond",           1e-9,             "s"),
            "ps"   to UnitDef("picosecond",           1e-12,            "s"),
            "min"  to UnitDef("minute",               60.0,             "s"),
            "h"    to UnitDef("hour",                 3_600.0,          "s", listOf("hr")),
            "d"    to UnitDef("day",                  86_400.0,         "s"),
            "wk"   to UnitDef("week",                 604_800.0,        "s"),
            "mo"   to UnitDef("month (30 d)",         2_592_000.0,      "s"),
            "yr"   to UnitDef("Julian year",          31_557_600.0,     "s", listOf("year"))
        ),

        "energy" to mapOf(   // base: joule
            "J"    to UnitDef("joule",                1.0,              "J"),
            "kJ"   to UnitDef("kilojoule",            1_000.0,          "J"),
            "MJ"   to UnitDef("megajoule",            1e6,              "J"),
            "cal"  to UnitDef("calorie (thermochem)", 4.184,            "J"),
            "kcal" to UnitDef("kilocalorie",          4_184.0,          "J", listOf("Cal")),
            "Wh"   to UnitDef("watt-hour",            3_600.0,          "J"),
            "kWh"  to UnitDef("kilowatt-hour",        3.6e6,            "J"),
            "eV"   to UnitDef("electron-volt",        1.602176634e-19,  "J"),
            "keV"  to UnitDef("kilo-electron-volt",   1.602176634e-16,  "J"),
            "MeV"  to UnitDef("mega-electron-volt",   1.602176634e-13,  "J"),
            "BTU"  to UnitDef("British thermal unit", 1_055.05585262,   "J"),
            "erg"  to UnitDef("erg",                  1e-7,             "J"),
            "ft_lb" to UnitDef("foot-pound",          1.3558179483314,  "J")
        ),

        "power" to mapOf(   // base: watt
            "W"    to UnitDef("watt",                 1.0,              "W"),
            "kW"   to UnitDef("kilowatt",             1_000.0,          "W"),
            "MW"   to UnitDef("megawatt",             1e6,              "W"),
            "GW"   to UnitDef("gigawatt",             1e9,              "W"),
            "hp"   to UnitDef("horsepower (mech)",    745.69987158227,  "W"),
            "PS"   to UnitDef("metric horsepower",    735.49875,        "W"),
            "BTU_h" to UnitDef("BTU/hour",            0.29307107017,    "W")
        ),

        "pressure" to mapOf(   // base: pascal
            "Pa"   to UnitDef("pascal",               1.0,              "Pa"),
            "kPa"  to UnitDef("kilopascal",           1_000.0,          "Pa"),
            "MPa"  to UnitDef("megapascal",           1e6,              "Pa"),
            "GPa"  to UnitDef("gigapascal",           1e9,              "Pa"),
            "bar"  to UnitDef("bar",                  1e5,              "Pa"),
            "mbar" to UnitDef("millibar",             100.0,            "Pa"),
            "atm"  to UnitDef("atmosphere",           101_325.0,        "Pa"),
            "torr" to UnitDef("torr",                 133.32236842105,  "Pa"),
            "mmHg" to UnitDef("mm of mercury",        133.322387415,    "Pa"),
            "psi"  to UnitDef("pound/sq inch",        6_894.757293168,  "Pa"),
            "inHg" to UnitDef("inch of mercury",      3_386.388640341,  "Pa")
        ),

        "speed" to mapOf(   // base: m/s
            "m/s"  to UnitDef("metres per second",    1.0,              "m/s"),
            "km/h" to UnitDef("kilometres per hour",  1.0 / 3.6,        "m/s", listOf("kph")),
            "mph"  to UnitDef("miles per hour",       0.44704,          "m/s"),
            "kn"   to UnitDef("knot",                 0.514444444,      "m/s", listOf("kt")),
            "ft/s" to UnitDef("feet per second",      0.3048,           "m/s"),
            "c"    to UnitDef("speed of light",       299_792_458.0,    "m/s")
        ),

        "area" to mapOf(   // base: m²
            "m2"   to UnitDef("square metre",         1.0,              "m²", listOf("m²")),
            "cm2"  to UnitDef("square centimetre",    1e-4,             "m²", listOf("cm²")),
            "km2"  to UnitDef("square kilometre",     1e6,              "m²", listOf("km²")),
            "ha"   to UnitDef("hectare",              10_000.0,         "m²"),
            "ac"   to UnitDef("acre",                 4_046.8564224,    "m²"),
            "ft2"  to UnitDef("square foot",          0.09290304,       "m²", listOf("ft²")),
            "in2"  to UnitDef("square inch",          6.4516e-4,        "m²", listOf("in²")),
            "mi2"  to UnitDef("square mile",          2_589_988.110336, "m²", listOf("mi²"))
        ),

        "volume" to mapOf(   // base: litre
            "L"    to UnitDef("litre",                1.0,              "L", listOf("l")),
            "mL"   to UnitDef("millilitre",           1e-3,             "L", listOf("ml")),
            "m3"   to UnitDef("cubic metre",          1_000.0,          "L", listOf("m³")),
            "cm3"  to UnitDef("cubic centimetre",     1e-3,             "L", listOf("cm³","cc")),
            "ft3"  to UnitDef("cubic foot",           28.316846592,     "L", listOf("ft³")),
            "in3"  to UnitDef("cubic inch",           0.016387064,      "L", listOf("in³")),
            "gal"  to UnitDef("US gallon",            3.785411784,      "L"),
            "qt"   to UnitDef("US quart",             0.946352946,      "L"),
            "pt"   to UnitDef("US pint",              0.473176473,      "L"),
            "cup"  to UnitDef("US cup",               0.2365882365,     "L"),
            "floz" to UnitDef("US fluid ounce",       0.0295735295625,  "L", listOf("fl_oz")),
            "tbsp" to UnitDef("tablespoon (US)",      0.01478676478125, "L"),
            "tsp"  to UnitDef("teaspoon (US)",        0.00492892159375, "L"),
            "imp_gal" to UnitDef("Imperial gallon",   4.54609,          "L")
        ),

        "angle" to mapOf(   // base: radian
            "rad"    to UnitDef("radian",             1.0,              "rad"),
            "deg"    to UnitDef("degree",             Math.PI / 180,    "rad", listOf("°")),
            "grad"   to UnitDef("gradian",            Math.PI / 200,    "rad", listOf("gon")),
            "arcmin" to UnitDef("arcminute",          Math.PI / 10_800, "rad", listOf("'")),
            "arcsec" to UnitDef("arcsecond",          Math.PI / 648_000,"rad", listOf("\"")),
            "rev"    to UnitDef("revolution",         2 * Math.PI,      "rad", listOf("turn"))
        ),

        "data" to mapOf(   // base: byte
            "B"    to UnitDef("byte",                 1.0,              "B"),
            "KB"   to UnitDef("kilobyte (10³)",       1_000.0,          "B"),
            "MB"   to UnitDef("megabyte (10⁶)",       1e6,              "B"),
            "GB"   to UnitDef("gigabyte (10⁹)",       1e9,              "B"),
            "TB"   to UnitDef("terabyte (10¹²)",      1e12,             "B"),
            "KiB"  to UnitDef("kibibyte (2¹⁰)",       1_024.0,          "B"),
            "MiB"  to UnitDef("mebibyte (2²⁰)",       1_048_576.0,      "B"),
            "GiB"  to UnitDef("gibibyte (2³⁰)",       1_073_741_824.0,  "B"),
            "TiB"  to UnitDef("tebibyte (2⁴⁰)",       1_099_511_627_776.0,"B"),
            "bit"  to UnitDef("bit",                  0.125,            "B")
        )
    )

    // Flat index: unit symbol (and aliases) → (UnitDef, categoryName)
    private val flatIndex: Map<String, Pair<UnitDef, String>> by lazy {
        buildMap {
            tables.forEach { (cat, units) ->
                units.forEach { (sym, def) ->
                    put(sym, def to cat)
                    def.aliases.forEach { alias -> put(alias, def to cat) }
                }
            }
        }
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    /**
     * Parse and execute a conversion expression.
     * Format: `value fromUnit to toUnit`
     * Temperature shorthand: `value C to F` (no leading zero required)
     */
    fun convert(expression: String): String {
        val expr = expression.trim()

        if (expr.equals("list", ignoreCase = true)) return listAll()
        if (expr.startsWith("list:", ignoreCase = true))
            return listCategory(expr.substringAfter(':').trim())

        val match = PATTERN.matchEntire(expr)
            ?: return "Format: <value> <fromUnit> to <toUnit>  (e.g. '100 km to mi')"

        val value    = match.groupValues[1].toDouble()
        val fromSym  = match.groupValues[2]
        val toSym    = match.groupValues[3]

        // Temperature branch (affine, not multiplicative)
        if (fromSym in TEMP_UNITS && toSym in TEMP_UNITS)
            return convertTemperature(value, fromSym, toSym)

        val (fromDef, fromCat) = flatIndex[fromSym]
            ?: return "Unknown unit: '$fromSym'. Try 'convert: list'"
        val (toDef, toCat) = flatIndex[toSym]
            ?: return "Unknown unit: '$toSym'. Try 'convert: list'"

        if (fromCat != toCat)
            return "Incompatible categories: '$fromSym' ($fromCat) vs '$toSym' ($toCat)"

        val siValue = value * fromDef.toSI
        val result  = siValue / toDef.toSI
        return "${fmt(value)} $fromSym = ${fmt(result)} $toSym"
    }

    // ── Temperature (affine) ─────────────────────────────────────────────────

    fun convertTemperature(value: Double, from: String, to: String): String {
        val celsius = when (from.uppercase()) {
            "C" -> value
            "F" -> (value - 32) * 5.0 / 9.0
            "K" -> value - 273.15
            "R" -> (value - 491.67) * 5.0 / 9.0   // Rankine
            else -> throw IllegalArgumentException("Unknown temperature unit: $from")
        }
        val result = when (to.uppercase()) {
            "C" -> celsius
            "F" -> celsius * 9.0 / 5.0 + 32
            "K" -> celsius + 273.15
            "R" -> (celsius + 273.15) * 9.0 / 5.0  // Rankine
            else -> throw IllegalArgumentException("Unknown temperature unit: $to")
        }
        val fromLabel = if (from == "K" || from == "R") from else "°$from"
        val toLabel   = if (to   == "K" || to   == "R") to   else "°$to"
        return "${fmt(value)}$fromLabel = ${fmt(result)}$toLabel"
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    fun listAll(): String =
        tables.entries.joinToString("\n\n") { (cat, units) ->
            "── ${cat.replaceFirstChar { it.uppercase() }} ──\n" +
            units.entries.joinToString("\n") { (sym, def) ->
                val aliases = if (def.aliases.isEmpty()) "" else "  (aliases: ${def.aliases.joinToString()})"
                "  $sym → ${def.name}$aliases"
            }
        }

    fun listCategory(cat: String): String {
        val units = tables.entries.firstOrNull { it.key.equals(cat, ignoreCase = true) }?.value
            ?: return "Unknown category '$cat'. Available: ${tables.keys.joinToString()}"
        return "── ${cat.replaceFirstChar { it.uppercase() }} ──\n" +
               units.entries.joinToString("\n") { (sym, def) -> "  $sym → ${def.name}" }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fmt(v: Double): String {
        if (v == 0.0) return "0"
        val rounded = Math.round(v * 1e10) / 1e10
        return if (abs(rounded) >= 1e10 || (abs(rounded) < 1e-4 && rounded != 0.0))
            "%.6e".format(rounded)
        else "%.8f".format(rounded).trimEnd('0').trimEnd('.')
    }

    companion object {
        // Matches: "100.5 km/h to mph" or "100 km to mi"
        private val PATTERN = Regex("""^([+-]?[\d.eE+\-]+)\s+(\S+)\s+to\s+(\S+)$""")
        private val TEMP_UNITS = setOf("C", "F", "K", "R", "c", "f", "k", "r")
    }
}
