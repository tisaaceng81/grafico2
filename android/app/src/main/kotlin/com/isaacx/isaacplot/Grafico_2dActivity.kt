package com.isaacx.isaacplot

import android.os.Bundle
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.SpannableStringBuilder
import android.text.style.SuperscriptSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MathParser(private val expr: String) {
    private val expression = expr
        .replace("²", "^2")
        .replace("³", "^3")
        .replace("¹", "^1")
        .replace("⁴", "^4")
        .replace("**", "^")
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        pos++
        ch = if (pos < expression.length) expression[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(xVal: Double): Double {
        pos = -1
        nextChar()
        return parseExpression(xVal)
    }

    private fun parseExpression(xVal: Double): Double {
        var x = parseTerm(xVal)
        while (true) {
            if (eat('+'.code)) x += parseTerm(xVal)
            else if (eat('-'.code)) x -= parseTerm(xVal)
            else return x
        }
    }

    private fun parseTerm(xVal: Double): Double {
        var x = parseFactor(xVal)
        while (true) {
            if (eat('*'.code)) x *= parseFactor(xVal)
            else if (eat('/'.code)) x /= parseFactor(xVal)
            else return x
        }
    }

    private fun parseFactor(xVal: Double): Double {
        if (eat('+'.code)) return parseFactor(xVal)
        if (eat('-'.code)) return -parseFactor(xVal)

        val startPos = this.pos
        var x = 0.0

        if (eat('('.code)) {
            x = parseExpression(xVal)
            eat(')'.code)
        } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
            while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
            x = expression.substring(startPos, this.pos).toDoubleOrNull() ?: 0.0
        } else if ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) {
            while ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) nextChar()
            val func = expression.substring(startPos, this.pos).lowercase()
            if (func == "x") {
                x = xVal
            } else if (func == "and") {
                x = Math.E
            } else if (func == "pi") {
                x = Math.PI
            } else if (eat('('.code)) {
                val arg = parseExpression(xVal)
                eat(')'.code)
                x = when (func) {
                    "sin" -> Math.sin(arg)
                    "cos" -> Math.cos(arg)
                    "tan" -> Math.tan(arg)
                    "sqrt" -> Math.sqrt(arg)
                    "abs" -> Math.abs(arg)
                    "exp" -> Math.exp(arg)
                    "log", "ln" -> Math.log(arg)
                    else -> 0.0
                }
            }
        }

        if (eat('^'.code)) x = Math.pow(x, parseFactor(xVal))

        return x
    }
}

class Grafico_2dActivity : AppCompatActivity() {

    private var isUpdatingText = false

    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var currentXMin = -10.0
    private var currentXMax = 10.0
    private var currentYMin = -10.0
    private var currentYMax = 10.0

    
    private lateinit var scaleDetector: ScaleGestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("grafico_2d", "layout", packageName))

        try {
            val logoView = findViewById<ImageView>(resources.getIdentifier("logo2", "id", packageName))
            val inputStream = assets.open("logo2.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            logoView?.setImageBitmap(bitmap)
        } catch (and: Exception) {
            and.printStackTrace()
        }

        val edtFuncao = findViewById<EditText>(resources.getIdentifier("edtFuncao", "id", packageName))
        val btnGerarGrafico = findViewById<Button>(resources.getIdentifier("btnGerarGrafico", "id", packageName))
        val imgGraficoCanvas = findViewById<ImageView>(resources.getIdentifier("imgGraficoCanvas", "id", packageName))

        edtFuncao?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText || s == null) return
                isUpdatingText = true

                val rawText = s.toString()
                val spannable = SpannableStringBuilder(rawText)
                val pattern = Regex("(\\*\\*|\\^)([0-9xX]+)")
                val matches = pattern.findAll(rawText)

                for (match in matches) {
                    val opRange = match.groups[1]?.range ?: continue
                    val expRange = match.groups[2]?.range ?: continue

                    spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), opRange.first, opRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(RelativeSizeSpan(0.0f), opRange.first, opRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(SuperscriptSpan(), expRange.first, expRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(RelativeSizeSpan(0.75f), expRange.first, expRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val currentSelStart = edtFuncao.selectionStart
                val currentSelEnd = edtFuncao.selectionEnd
                edtFuncao.setText(spannable)
                try {
                    edtFuncao.setSelection(
                        Math.min(currentSelStart, spannable.length),
                        Math.min(currentSelEnd, spannable.length)
                    )
                } catch (and: Exception) {
                    edtFuncao.setSelection(edtFuncao.text?.length ?: 0)
                }

                isUpdatingText = false
            }
        })

        
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val spanX = currentXMax - currentXMin
                val spanY = currentYMax - currentYMin
                
                
                val newSpanX = spanX / factor
                val newSpanY = spanY / factor
                
                val midX = (currentXMax + currentXMin) / 2
                val midY = (currentYMax + currentYMin) / 2
                
                currentXMin = midX - newSpanX / 2
                currentXMax = midX + newSpanX / 2
                currentYMin = midY - newSpanY / 2
                currentYMax = midY + newSpanY / 2
                
                val expressao = edtFuncao?.text?.toString() ?: "x**2"
                imgGraficoCanvas?.setImageBitmap(desenharGrafico(expressao))
                return true
            }
        })

        
        imgGraficoCanvas?.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            
            if (!scaleDetector.isInProgress) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY

                        val width = imgGraficoCanvas.width.toFloat()
                        val height = imgGraficoCanvas.height.toFloat()
                        val margin = 60f

                        
                        val rangeX = currentXMax - currentXMin
                        val rangeY = currentYMax - currentYMin
                        
                        val mathDx = (dx / (width - 2 * margin)) * rangeX
                        val mathDy = (dy / (height - 2 * margin)) * rangeY

                        
                        currentXMin -= mathDx
                        currentXMax -= mathDx
                        currentYMin += mathDy 
                        currentYMax += mathDy

                        lastTouchX = event.x
                        lastTouchY = event.y

                        val expressao = edtFuncao?.text?.toString() ?: "x**2"
                        imgGraficoCanvas.setImageBitmap(desenharGrafico(expressao))
                    }
                }
            }
            true
        }

        btnGerarGrafico?.setOnClickListener {
            
            currentXMin = -10.0; currentXMax = 10.0
            currentYMin = -10.0; currentYMax = 10.0
            
            val expressao = edtFuncao?.text?.toString() ?: "x**2"
            val bitmapGrafico = desenharGrafico(expressao)
            imgGraficoCanvas?.setImageBitmap(bitmapGrafico)
        }

        val bitmapInicial = desenharGrafico("x**2 - 4")
        imgGraficoCanvas?.setImageBitmap(bitmapInicial)
    }

    private fun desenharGrafico(expressao: String): Bitmap {
        val width = 800
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintGrid = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f; isAntiAlias = true }
        val paintEixos = Paint().apply { color = Color.parseColor("#334155"); strokeWidth = 4f; isAntiAlias = true }
        val paintTexto = Paint().apply { color = Color.parseColor("#475569"); textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        
        val paintLinhaGrafico = Paint().apply { color = Color.parseColor("#0066FF"); strokeWidth = 5f; isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
        val paintPoligonoFill = Paint().apply { color = Color.parseColor("#330066FF"); style = Paint.Style.FILL; isAntiAlias = true }
        val paintPonto = Paint().apply { color = Color.parseColor("#DC2626"); style = Paint.Style.FILL; isAntiAlias = true }

        val margin = 60f
        
        
        fun proj(x: Double, y: Double): Pair<Float, Float> {
            val px = (margin + (x - currentXMin) / (currentXMax - currentXMin) * (width - 2 * margin)).toFloat()
            val py = ((height - margin) - (y - currentYMin) / (currentYMax - currentYMin) * (height - 2 * margin)).toFloat()
            return Pair(px, py)
        }

        
        val rangeX = currentXMax - currentXMin
        val stepX = Math.max(1.0, Math.round(rangeX / 10.0).toDouble())
        
        var startX = Math.floor(currentXMin / stepX) * stepX
        while (startX <= currentXMax) {
            val (px, _) = proj(startX, 0.0)
            if (px in margin..(width - margin)) {
                canvas.drawLine(px, margin, px, height - margin, paintGrid)
                if (startX != 0.0) {
                    val (_, py0) = proj(0.0, 0.0)
                    val safePy0 = py0.coerceIn(margin + 20f, height - margin - 10f)
                    canvas.drawText(String.format("%.1f", startX).replace(".0", ""), px, safePy0 + 30f, paintTexto)
                }
            }
            startX += stepX
        }

        val rangeY = currentYMax - currentYMin
        val stepY = Math.max(1.0, Math.round(rangeY / 10.0).toDouble())
        var startY = Math.floor(currentYMin / stepY) * stepY
        while (startY <= currentYMax) {
            val (_, py) = proj(0.0, startY)
            if (py in margin..(height - margin)) {
                canvas.drawLine(margin, py, width - margin, py, paintGrid)
                if (startY != 0.0) {
                    val (px0, _) = proj(0.0, 0.0)
                    val safePx0 = px0.coerceIn(margin + 30f, width - margin - 20f)
                    canvas.drawText(String.format("%.1f", startY).replace(".0", ""), safePx0 - 30f, py + 8f, paintTexto)
                }
            }
            startY += stepY
        }

        
        val (ox, oy) = proj(0.0, 0.0)
        if (oy in margin..(height - margin)) canvas.drawLine(margin, oy, width - margin, oy, paintEixos)
        if (ox in margin..(width - margin)) canvas.drawLine(ox, margin, ox, height - margin, paintEixos)

        
        canvas.clipRect(margin, margin, width - margin, height - margin)

        
        val regexPontos = Regex("\\(\\s*(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)\\s*\\)")
        val matchesPontos = regexPontos.findAll(expressao).toList()

        if (matchesPontos.isNotEmpty()) {
            
            val path = Path()
            var primeiroPonto = true

            for ((index, match) in matchesPontos.withIndex()) {
                val pxMath = match.groupValues[1].toDouble()
                val pyMath = match.groupValues[2].toDouble()
                val (pxScr, pyScr) = proj(pxMath, pyMath)

                if (primeiroPonto) {
                    path.moveTo(pxScr, pyScr)
                    primeiroPonto = false
                } else {
                    path.lineTo(pxScr, pyScr)
                }

                
                canvas.drawCircle(pxScr, pyScr, 8f, paintPonto)
                canvas.drawText(String.format("%c", 'A'.code + (index % 26)), pxScr + 15f, pyScr - 15f, paintTexto)
            }

            
            if (matchesPontos.size > 2) {
                path.close()
                canvas.drawPath(path, paintPoligonoFill)
            }
            canvas.drawPath(path, paintLinhaGrafico)

        } else {
            
            try {
                val parser = MathParser(expressao)
                var prevPx: Float? = null
                var prevPy: Float? = null

                val steps = 400
                for (step in 0..steps) {
                    val xVal = currentXMin + step * (currentXMax - currentXMin) / steps
                    val yVal = parser.parse(xVal)

                    if (!yVal.isNaN() && !yVal.isInfinite() && yVal >= currentYMin - (currentYMax-currentYMin) && yVal <= currentYMax + (currentYMax-currentYMin)) {
                        val (px, py) = proj(xVal, yVal)

                        if (prevPx != null && prevPy != null) {
                            canvas.drawLine(prevPx, prevPy, px, py, paintLinhaGrafico)
                        }
                        prevPx = px
                        prevPy = py
                    } else {
                        prevPx = null
                        prevPy = null
                    }
                }
            } catch (and: Exception) {
                and.printStackTrace()
            }
        }

        return bitmap
    }
}