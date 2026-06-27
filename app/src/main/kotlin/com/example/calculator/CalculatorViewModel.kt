package com.example.calculator

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.engine.AngleMode
import com.example.calculator.engine.CalculatorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalculatorViewModel : ViewModel() {

    // ── Engine ────────────────────────────────────────────────────────────────
    val engine = CalculatorEngine(AngleMode.DEGREES)

    // ── Observable state ──────────────────────────────────────────────────────
    val expression  = MutableLiveData("")
    val result      = MutableLiveData("")
    val isShift     = MutableLiveData(false)
    val isAlpha     = MutableLiveData(false)
    val isHyp       = MutableLiveData(false)
    val angleMode   = MutableLiveData(AngleMode.DEGREES)
    val history     = MutableLiveData<List<String>>(emptyList())
    val memory      = MutableLiveData(0.0)

    // ── Internal state ────────────────────────────────────────────────────────
    private var cursorPos      = 0
    private var justEvaluated  = false
    private var memValue       = 0.0

    // ── Expression editing ────────────────────────────────────────────────────

    /** Append text at current cursor position. */
    fun input(text: String) {
        val old = expression.value ?: ""
        if (justEvaluated && text.first().isDigit()) {
            // Fresh expression after a result
            expression.value = text
            cursorPos = text.length
        } else {
            val new = old.substring(0, cursorPos) + text + old.substring(cursorPos)
            expression.value = new
            cursorPos += text.length
        }
        justEvaluated = false
        clearModifiers()
        livePreview()
    }

    fun delete() {
        val old = expression.value ?: ""
        if (cursorPos > 0) {
            expression.value = old.removeRange(cursorPos - 1, cursorPos)
            cursorPos--
        }
        justEvaluated = false
        livePreview()
    }

    fun clearAll() {
        expression.value = ""
        result.value     = ""
        cursorPos        = 0
        justEvaluated    = false
        clearModifiers()
    }

    fun moveCursor(delta: Int) {
        val len = expression.value?.length ?: 0
        cursorPos = (cursorPos + delta).coerceIn(0, len)
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    fun evaluate() {
        val expr = expression.value?.trim() ?: return
        if (expr.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val r = engine.evaluate(expr)
            withContext(Dispatchers.Main) {
                val display = when (r) {
                    is CalculatorEngine.Result.Value -> r.display
                    is CalculatorEngine.Result.Text  -> r.display
                    is CalculatorEngine.Result.Error -> "Error: ${r.message}"
                }
                result.value = display
                if (r !is CalculatorEngine.Result.Error) {
                    history.value = (history.value.orEmpty() +
                            "$expr  =  $display").takeLast(50)
                    justEvaluated = true
                    cursorPos = expression.value?.length ?: 0
                }
            }
        }
    }

    /** Live preview while typing (only for short, likely-complete expressions). */
    private fun livePreview() {
        val expr = expression.value ?: return
        if (expr.length < 2) { result.value = ""; return }
        viewModelScope.launch(Dispatchers.Default) {
            val r = try { engine.evaluate(expr) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                if (r is CalculatorEngine.Result.Value) result.value = "= ${r.display}"
                else if (r == null) result.value = ""
            }
        }
    }

    // ── Angle mode ────────────────────────────────────────────────────────────

    fun cycleAngleMode() {
        val next = when (engine.angleMode) {
            AngleMode.DEGREES  -> AngleMode.RADIANS
            AngleMode.RADIANS  -> AngleMode.GRADIANS
            AngleMode.GRADIANS -> AngleMode.DEGREES
        }
        engine.angleMode = next
        angleMode.value  = next
    }

    fun angleModeLabel() = when (engine.angleMode) {
        AngleMode.DEGREES  -> "DEG"
        AngleMode.RADIANS  -> "RAD"
        AngleMode.GRADIANS -> "GRAD"
    }

    // ── Modifier toggles ──────────────────────────────────────────────────────

    fun toggleShift() {
        val on = !(isShift.value ?: false)
        isShift.value = on
        if (on) { isAlpha.value = false; isHyp.value = false }
    }

    fun toggleAlpha() {
        val on = !(isAlpha.value ?: false)
        isAlpha.value = on
        if (on) { isShift.value = false; isHyp.value = false }
    }

    fun toggleHyp() {
        isHyp.value = !(isHyp.value ?: false)
        isShift.value = false
    }

    fun clearModifiers() {
        isShift.value = false
        isAlpha.value = false
        isHyp.value   = false
    }

    // ── Memory ────────────────────────────────────────────────────────────────

    fun memStore() {
        memValue = engine.lastAnswer
        memory.value = memValue
    }

    fun memRecall() { input(memValue.toString()) }

    fun memPlus() {
        memValue += engine.lastAnswer
        memory.value = memValue
    }

    fun memMinus() {
        memValue -= engine.lastAnswer
        memory.value = memValue
    }
}
