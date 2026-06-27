package com.example.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.calculator.databinding.ActivityMainBinding
import com.example.calculator.engine.AngleMode

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val vm: CalculatorViewModel by viewModels()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupObservers()
        setupAllButtons()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        vm.expression.observe(this) { b.tvExpression.text = it }

        vm.result.observe(this) { b.tvResult.text = it }

        vm.angleMode.observe(this) { b.btnAngleMode.text = vm.angleModeLabel() }

        vm.isShift.observe(this) { on ->
            b.btnShift.alpha = if (on) 1f else 0.7f
            // Tint goes bright when active
            b.btnShift.backgroundTintList = colorStateList(
                if (on) 0xFFFF8C00.toInt() else 0xFFAA5500.toInt()
            )
            updateShiftLabels(on)
        }

        vm.isAlpha.observe(this) { on ->
            b.btnAlpha.alpha = if (on) 1f else 0.7f
            b.btnAlpha.backgroundTintList = colorStateList(
                if (on) 0xFF6B5AE0.toInt() else 0xFF3D3280.toInt()
            )
        }
    }

    private fun updateShiftLabels(shiftOn: Boolean) {
        // Sin/Cos/Tan rows show inverse when SHIFT is active
        b.btnSin.text = if (shiftOn) "Sin⁻¹" else "Sin"
        b.btnCos.text = if (shiftOn) "Cos⁻¹" else "Cos"
        b.btnTan.text = if (shiftOn) "Tan⁻¹" else "Tan"
        b.btnLog.text = if (shiftOn) "10ˣ"   else "Log"
        b.btnLn.text  = if (shiftOn) "eˣ"    else "Ln"
        b.btnSqrt.text = if (shiftOn) "∛x"   else "√x"
        b.btnSq.text   = if (shiftOn) "x³"   else "x²"
        b.btnCalc.text  = if (shiftOn) "SOLVE" else "CALC"
    }

    // ── Button setup ──────────────────────────────────────────────────────────

    private fun setupAllButtons() {
        // Numbers
        mapOf(
            b.btn0 to "0", b.btn1 to "1", b.btn2 to "2", b.btn3 to "3",
            b.btn4 to "4", b.btn5 to "5", b.btn6 to "6", b.btn7 to "7",
            b.btn8 to "8", b.btn9 to "9", b.btnDot to ".", b.btnExpN to "×10^"
        ).forEach { (btn, text) -> btn.setOnClickListener { haptic(it); vm.input(text) } }

        // Basic operators
        b.btnAdd.setOnClickListener { haptic(it); vm.input("+") }
        b.btnSub.setOnClickListener { haptic(it); vm.input("-") }
        b.btnMul.setOnClickListener { haptic(it); vm.input("×") }
        b.btnDiv.setOnClickListener { haptic(it); vm.input("÷") }

        // Control
        b.btnEquals.setOnClickListener    { haptic(it); vm.evaluate() }
        b.btnDel.setOnClickListener       { haptic(it); vm.delete() }
        b.btnDel.setOnLongClickListener   { vm.clearAll(); true }
        b.btnAC.setOnClickListener        { haptic(it); vm.clearAll() }
        b.btnLeft.setOnClickListener      { haptic(it); vm.moveCursor(-1) }
        b.btnRight.setOnClickListener     { haptic(it); vm.moveCursor(+1) }
        b.btnAngleMode.setOnClickListener { haptic(it); vm.cycleAngleMode() }
        b.btnShift.setOnClickListener     { haptic(it); vm.toggleShift() }
        b.btnAlpha.setOnClickListener     { haptic(it); vm.toggleAlpha() }

        // Ans / Pi / e
        b.btnAns.setOnClickListener { haptic(it); vm.input("ans") }
        b.btnPi.setOnClickListener  { haptic(it); vm.input("π") }
        b.btnE.setOnClickListener   { haptic(it); vm.input("e") }

        // Parentheses
        b.btnOpen.setOnClickListener  { haptic(it); vm.input("(") }
        b.btnClose.setOnClickListener { haptic(it); vm.input(")") }

        // ── SHIFT-aware buttons ───────────────────────────────────────────
        b.btnSin.setOnClickListener { haptic(it)
            if (vm.isShift.value == true && vm.isHyp.value == true) vm.input("arcsinh(")
            else if (vm.isHyp.value == true)  vm.input("sinh(")
            else if (vm.isShift.value == true) vm.input("asin(")
            else                               vm.input("sin(")
        }
        b.btnCos.setOnClickListener { haptic(it)
            if (vm.isShift.value == true && vm.isHyp.value == true) vm.input("arccosh(")
            else if (vm.isHyp.value == true)  vm.input("cosh(")
            else if (vm.isShift.value == true) vm.input("acos(")
            else                               vm.input("cos(")
        }
        b.btnTan.setOnClickListener { haptic(it)
            if (vm.isShift.value == true && vm.isHyp.value == true) vm.input("arctanh(")
            else if (vm.isHyp.value == true)  vm.input("tanh(")
            else if (vm.isShift.value == true) vm.input("atan(")
            else                               vm.input("tan(")
        }
        b.btnLog.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("pow10(") else vm.input("log10(")
        }
        b.btnLn.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("exp(") else vm.input("ln(")
        }
        b.btnSqrt.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("cbrt(") else vm.input("sqrt(")
        }
        b.btnSq.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("^3") else vm.input("^2")
        }
        b.btnPow.setOnClickListener { haptic(it); vm.input("^") }
        b.btnFrac.setOnClickListener { haptic(it); vm.input("/") }
        b.btnRecip.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("!") else vm.input("^(-1)")
        }
        b.btnLogXY.setOnClickListener { haptic(it); vm.input("logb(") }

        b.btnNeg.setOnClickListener  { haptic(it); vm.input("(-") }
        b.btnHyp.setOnClickListener  { haptic(it); vm.toggleHyp() }

        // ── Row 1 extras ──────────────────────────────────────────────────
        b.btnCalc.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.input("solve(") else vm.input("ans")
        }
        b.btnIntegral.setOnClickListener { haptic(it); vm.input("integrate(") }
        b.btnUp.setOnClickListener   { haptic(it); vm.moveCursor(+1) }
        b.btnDown.setOnClickListener { haptic(it); vm.moveCursor(-1) }

        // ── Row 4 ─────────────────────────────────────────────────────────
        b.btnRCL.setOnClickListener  { haptic(it)
            if (vm.isShift.value == true) vm.memStore() else vm.memRecall()
        }
        b.btnENG.setOnClickListener  { haptic(it); vm.input("×10^") }
        b.btnStoD.setOnClickListener { haptic(it); vm.evaluate() }   // S⇔D: evaluate & toggle decimal
        b.btnMPlus.setOnClickListener { haptic(it)
            if (vm.isShift.value == true) vm.memMinus() else vm.memPlus()
        }
        b.btnDMS.setOnClickListener  { haptic(it); vm.input("°") }

        // ── Specialised function rows ──────────────────────────────────────
        b.btnMatrix.setOnClickListener  { haptic(it); showMatrixDialog() }
        b.btnVector.setOnClickListener  { haptic(it); showVectorDialog() }
        b.btnStat.setOnClickListener    { haptic(it); showStatDialog() }
        b.btnCmplx.setOnClickListener   { haptic(it); showComplexDialog() }
        b.btnBaseN.setOnClickListener   { haptic(it); showBaseNDialog() }
        b.btnConst.setOnClickListener   { haptic(it); showConstDialog() }
        b.btnConv.setOnClickListener    { haptic(it); showConvDialog() }

        b.btnNCr.setOnClickListener  { haptic(it); vm.input(" nCr ") }
        b.btnNPr.setOnClickListener  { haptic(it); vm.input(" nPr ") }
        b.btnPol.setOnClickListener  { haptic(it); vm.input("Pol(") }
        b.btnRec.setOnClickListener  { haptic(it); vm.input("Rec(") }
        b.btnRand.setOnClickListener { haptic(it); vm.input("rand") }

        b.btnSolve.setOnClickListener   { haptic(it); vm.input("solve(") }
        b.btnIntExpr.setOnClickListener { haptic(it); vm.input("∫(") }
        b.btnDiff.setOnClickListener    { haptic(it); vm.input("d/dx(") }
        b.btnStat1.setOnClickListener   { haptic(it); vm.input("stat1:") }
        b.btnStat2.setOnClickListener   { haptic(it); vm.input("stat2:") }
        b.btnGCD.setOnClickListener     { haptic(it); vm.input("gcd(") }

        b.btnAbs.setOnClickListener   { haptic(it); vm.input("abs(") }
        b.btnDet.setOnClickListener   { haptic(it); vm.input("det(") }
        b.btnTrans.setOnClickListener { haptic(it); vm.input("trans(") }

        b.btnHistory.setOnClickListener { haptic(it); showHistoryDialog() }

        // Long press display → copy result
        b.tvResult.setOnLongClickListener {
            val text = b.tvResult.text.toString()
            if (text.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("calc_result", text))
                Toast.makeText(this, "Copied: $text", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showHistoryDialog() {
        val items = vm.history.value.orEmpty().reversed().toTypedArray()
        if (items.isEmpty()) { toast("No history yet"); return }
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("History")
            .setItems(items) { _, i ->
                // Tap a history entry to insert its expression
                val expr = items[i].substringBefore("  =  ")
                vm.input(expr)
            }
            .setNegativeButton("Clear") { _, _ -> vm.history.value = emptyList() }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showMatrixDialog() {
        val options = arrayOf(
            "det([[a,b],[c,d]])",
            "trans([[a,b],[c,d]])",
            "inv([[a,b],[c,d]])",
            "trace([[a,b],[c,d]])",
            "rank([[a,b],[c,d]])",
            "[[a,b],[c,d]] * [[e,f],[g,h]]",
            "dot([a,b,c];[d,e,f])",
            "cross([a,b,c];[d,e,f])",
            "norm([a,b,c])"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Matrix / Vector")
            .setItems(options) { _, i -> vm.input(options[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showVectorDialog() {
        val options = arrayOf(
            "dot([a,b,c];[d,e,f])",
            "cross([a,b,c];[d,e,f])",
            "norm([a,b,c])",
            "angle([a,b,c];[d,e,f])",
            "proj([a,b];[c,d])"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Vector Operations")
            .setItems(options) { _, i -> vm.input(options[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showStatDialog() {
        val options = arrayOf(
            "stat1:  (e.g. stat1:1,2,3,4,5)",
            "stat2:  (e.g. stat2:(1,2),(3,5))"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Statistics")
            .setItems(options) { _, i ->
                if (i == 0) vm.input("stat1:") else vm.input("stat2:")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showComplexDialog() {
        val options = arrayOf(
            "(a+bi) + (c+di)  — add",
            "(a+bi) * (c+di)  — multiply",
            "abs(a+bi)        — modulus",
            "conj(a+bi)       — conjugate",
            "arg(a+bi)        — argument",
            "exp(i*π)         — Euler's identity"
        )
        val inserts = arrayOf(
            "(+i)+(+i)",
            "(+i)*(+i)",
            "abs(+i)",
            "conj(+i)",
            "arg(+i)",
            "exp(i*π)"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Complex Numbers")
            .setItems(options) { _, i -> vm.input(inserts[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showBaseNDialog() {
        val options = arrayOf(
            "Binary  0b…",
            "Octal   0o…",
            "Hex     0x…",
            "A AND B",
            "A OR B",
            "A XOR B",
            "NOT A",
            "A SHL n",
            "A SHR n"
        )
        val inserts = arrayOf(
            "0b", "0o", "0x",
            " AND ", " OR ", " XOR ", "NOT ", " SHL ", " SHR "
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Base-N / Bitwise")
            .setItems(options) { _, i -> vm.input(inserts[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showConstDialog() {
        val keys = listOf(
            "c"  to "Speed of light",
            "h"  to "Planck constant",
            "G"  to "Gravitational const",
            "Na" to "Avogadro number",
            "kb" to "Boltzmann constant",
            "e"  to "Elementary charge",
            "me" to "Electron mass",
            "R"  to "Gas constant",
            "g"  to "Standard gravity",
            "pi" to "Pi (π)",
            "phi" to "Golden ratio (φ)"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Physical Constants")
            .setItems(keys.map { "${it.first}  —  ${it.second}" }.toTypedArray()) { _, i ->
                vm.input("const:${keys[i].first}")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showConvDialog() {
        val examples = arrayOf(
            "100 km to mi",
            "1 kg to lb",
            "100 C to F",
            "1 atm to Pa",
            "1 kWh to J",
            "1024 MiB to GiB",
            "1 ly to km",
            "60 mph to km/h"
        )
        AlertDialog.Builder(this, R.style.Theme_Calculator)
            .setTitle("Unit Conversion  (convert: …)")
            .setItems(examples) { _, i -> vm.input("convert: ${examples[i]}") }
            .setNegativeButton("Cancel", null).show()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun haptic(v: android.view.View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun colorStateList(color: Int) =
        android.content.res.ColorStateList.valueOf(color)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
