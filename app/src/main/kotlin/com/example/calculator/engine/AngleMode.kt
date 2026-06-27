package com.example.calculator.engine

/**
 * Angle measurement mode used by trigonometric and coordinate engines.
 * All engines that consume angles accept/emit values in the currently active mode.
 */
enum class AngleMode {
    DEGREES,   // 0–360°
    RADIANS,   // 0–2π  (default)
    GRADIANS   // 0–400 grad
}
