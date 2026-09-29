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
import android.text.style.SuperscriptSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MathParser3D(private val expr: String) {
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

    fun parse(xVal: Double, yVal: Double): Double {
        return parse(xVal, yVal, 0.0)
    }

    fun parse(xVal: Double, yVal: Double, zVal: Double): Double {
        pos = -1
        nextChar()
        return parseExpression(xVal, yVal, zVal)
    }

    private fun parseExpression(xVal: Double, yVal: Double, zVal: Double): Double {
        var x = parseTerm(xVal, yVal, zVal)
        while (true) {
            if (eat('+'.code)) x += parseTerm(xVal, yVal, zVal)
            else if (eat('-'.code)) x -= parseTerm(xVal, yVal, zVal)
            else return x
        }
    }

    private fun parseTerm(xVal: Double, yVal: Double, zVal: Double): Double {
        var x = parseFactor(xVal, yVal, zVal)
        while (true) {
            if (eat('*'.code)) x *= parseFactor(xVal, yVal, zVal)
            else if (eat('/'.code)) x /= parseFactor(xVal, yVal, zVal)
            else return x
        }
    }

    private fun parseFactor(xVal: Double, yVal: Double, zVal: Double): Double {
        if (eat('+'.code)) return parseFactor(xVal, yVal, zVal)
        if (eat('-'.code)) return -parseFactor(xVal, yVal, zVal)

        val startPos = this.pos
        var x = 0.0

        if (eat('('.code)) {
            x = parseExpression(xVal, yVal, zVal)
            eat(')'.code)
        } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
            while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
            x = expression.substring(startPos, this.pos).toDoubleOrNull() ?: 0.0
        } else if ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) {
            while ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) nextChar()
            val func = expression.substring(startPos, this.pos).lowercase()
            if (func == "x" || func == "u") {
                x = xVal
            } else if (func == "y" || func == "v") {
                x = yVal
            } else if (func == "z") {
                x = zVal
            } else if (func == "and") {
                x = Math.E
            } else if (func == "pi") {
                x = Math.PI
            } else if (eat('('.code)) {
                val arg = parseExpression(xVal, yVal, zVal)
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

        if (eat('^'.code)) x = Math.pow(x, parseFactor(xVal, yVal, zVal))

        return x
    }
}

private data class Point3D(val x: Double, val y: Double, val z: Double)
private data class Face3D(val p1: Point3D, val p2: Point3D, val p3: Point3D, val p4: Point3D)

private data class Quad3D(
    val p1x: Float, val p1y: Float,
    val p2x: Float, val p2y: Float,
    val p3x: Float, val p3y: Float,
    val p4x: Float, val p4y: Float,
    val avgZ: Float,
    val rawZ: Float
)

class Grafico_3dActivity : AppCompatActivity() {

    private var isUpdatingText = false
    
    private var rotX = 35f
    private var rotY = 45f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    private var globalScale = 55.0
    private lateinit var scaleDetector: ScaleGestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("grafico_3d", "layout", packageName))

        try {
            val logoView = findViewById<ImageView>(resources.getIdentifier("logo3", "id", packageName))
            val inputStream = assets.open("logo3.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            logoView?.setImageBitmap(bitmap)
        } catch (and: Exception) {
            and.printStackTrace()
        }

        val edtFuncao3D = findViewById<EditText>(resources.getIdentifier("edtFuncao3D", "id", packageName))
        val btnGerarGrafico3D = findViewById<Button>(resources.getIdentifier("btnGerarGrafico3D", "id", packageName))
        val imgGraficoCanvas3D = findViewById<ImageView>(resources.getIdentifier("imgGraficoCanvas3D", "id", packageName))

        edtFuncao3D?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText || s == null) return
                isUpdatingText = true

                try {
                    val spansSup = s.getSpans(0, s.length, SuperscriptSpan::class.java)
                    for (span in spansSup) s.removeSpan(span)
                    val spansSize = s.getSpans(0, s.length, RelativeSizeSpan::class.java)
                    for (span in spansSize) s.removeSpan(span)
                    val spansColor = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                    for (span in spansColor) s.removeSpan(span)

                    val rawText = s.toString()
                    val pattern = Regex("(\\*\\*|\\^)([0-9xXyYuUvVzZ]+)")
                    val matches = pattern.findAll(rawText)

                    for (match in matches) {
                        val opRange = match.groups[1]?.range ?: continue
                        val expRange = match.groups[2]?.range ?: continue

                        s.setSpan(ForegroundColorSpan(Color.TRANSPARENT), opRange.first, opRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(RelativeSizeSpan(0.0f), opRange.first, opRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                        s.setSpan(SuperscriptSpan(), expRange.first, expRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(RelativeSizeSpan(0.75f), expRange.first, expRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } catch (and: Exception) {
                    and.printStackTrace()
                }

                isUpdatingText = false
            }
        })

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                globalScale *= detector.scaleFactor
                globalScale = globalScale.coerceIn(10.0, 300.0) 

                val expressao = edtFuncao3D?.text?.toString() ?: "sphere"
                imgGraficoCanvas3D?.setImageBitmap(desenharSuperficie3D(expressao))
                return true
            }
        })

        imgGraficoCanvas3D?.setOnTouchListener { _, event ->
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
                        
                        rotY -= dx * 0.4f
                        rotX -= dy * 0.4f
                        
                        rotX = rotX.coerceIn(-85f, 85f)

                        lastTouchX = event.x
                        lastTouchY = event.y

                        val expressao = edtFuncao3D?.text?.toString() ?: "sphere"
                        imgGraficoCanvas3D.setImageBitmap(desenharSuperficie3D(expressao))
                    }
                }
            }
            true
        }

        btnGerarGrafico3D?.setOnClickListener {
            rotX = 35f
            rotY = 45f
            globalScale = 55.0
            val expressao = edtFuncao3D?.text?.toString() ?: "sphere"
            val bitmapGrafico = desenharSuperficie3D(expressao)
            imgGraficoCanvas3D?.setImageBitmap(bitmapGrafico)
        }

        val bitmapInicial = desenharSuperficie3D("sphere")
        imgGraficoCanvas3D?.setImageBitmap(bitmapInicial)
    }

    private fun desenharSuperficie3D(expressao: String): Bitmap {
        val width = 900
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val gridSize = 40
        val exprClean = expressao.lowercase().trim()

        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        val scale = globalScale 
        val centerX = width / 2.0
        val centerY = height / 2.0 + 40.0

        fun project(x: Double, y: Double, z: Double): Triple<Float, Float, Float> {
            val rZ = Math.toRadians(rotY.toDouble())
            val cosZ = Math.cos(rZ)
            val sinZ = Math.sin(rZ)
            
            val x1 = x * cosZ - y * sinZ
            val y1 = x * sinZ + y * cosZ
            
            val rX = Math.toRadians(rotX.toDouble())
            val cosX = Math.cos(rX)
            val sinX = Math.sin(rX)
            
            val projY = y1 * cosX - z * sinX
            val depth = y1 * sinX + z * cosX 

            val px = (centerX + x1 * scale).toFloat()
            val py = (centerY - projY * scale).toFloat() 
            return Triple(px, py, depth.toFloat())
        }

        val paintAxisX = Paint().apply { color = Color.parseColor("#EF4444"); strokeWidth = 3f; isAntiAlias = true }
        val paintAxisY = Paint().apply { color = Color.parseColor("#10B981"); strokeWidth = 3f; isAntiAlias = true }
        val paintAxisZ = Paint().apply { color = Color.parseColor("#3B82F6"); strokeWidth = 3.5f; isAntiAlias = true }
        val paintGridPlane = Paint().apply { color = Color.parseColor("#64748B"); strokeWidth = 1.5f; style = Paint.Style.STROKE; isAntiAlias = true }
        val paintAxisText = Paint().apply { color = Color.parseColor("#1E293B"); textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        val regexPontos3D = Regex("\\(\\s*(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)\\s*\\)")
        val matches3D = regexPontos3D.findAll(exprClean).toList()

        if (matches3D.isNotEmpty()) {
            val path3D = Path()
            var primeiroPonto = true
            val paintPonto3D = Paint().apply { color = Color.parseColor("#DC2626"); style = Paint.Style.FILL; isAntiAlias = true }
            val paintPoligono3D = Paint().apply { color = Color.parseColor("#443B82F6"); style = Paint.Style.FILL; isAntiAlias = true }
            val paintAresta3D = Paint().apply { color = Color.parseColor("#1E293B"); strokeWidth = 4f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }

            minZ = -5.0
            maxZ = 5.0
            val floorZ = minZ - 1.2
            val gridSteps = 8

            for (i in 0..gridSteps) {
                val currVal = -5.0 + i * (10.0 / gridSteps)
                val pStart = project(currVal, -5.0, floorZ)
                val pEnd = project(currVal, 5.0, floorZ)
                canvas.drawLine(pStart.first, pStart.second, pEnd.first, pEnd.second, paintGridPlane)

                val pStart2 = project(-5.0, currVal, floorZ)
                val pEnd2 = project(5.0, currVal, floorZ)
                canvas.drawLine(pStart2.first, pStart2.second, pEnd2.first, pEnd2.second, paintGridPlane)
            }

            val originBottom = project(0.0, 0.0, floorZ)
            val originTop = project(0.0, 0.0, maxZ + 1.5)
            canvas.drawLine(originBottom.first, originBottom.second, originTop.first, originTop.second, paintAxisZ)

            val xAxisStart = project(-5.0, 0.0, floorZ)
            val xAxisEnd = project(5.0, 0.0, floorZ)
            canvas.drawLine(xAxisStart.first, xAxisStart.second, xAxisEnd.first, xAxisEnd.second, paintAxisX)

            val yAxisStart = project(0.0, -5.0, floorZ)
            val yAxisEnd = project(0.0, 5.0, floorZ)
            canvas.drawLine(yAxisStart.first, yAxisStart.second, yAxisEnd.first, yAxisEnd.second, paintAxisY)

            for ((index, match) in matches3D.withIndex()) {
                val x = match.groupValues[1].toDouble()
                val y = match.groupValues[2].toDouble()
                val z = match.groupValues[3].toDouble()

                val projecao = project(x, y, z)

                if (primeiroPonto) {
                    path3D.moveTo(projecao.first, projecao.second)
                    primeiroPonto = false
                } else {
                    path3D.lineTo(projecao.first, projecao.second)
                }

                canvas.drawCircle(projecao.first, projecao.second, 8f, paintPonto3D)
                canvas.drawText(String.format("%c", 'A'.code + (index % 26)), projecao.first + 18f, projecao.second - 10f, paintAxisText)
            }

            if (matches3D.size > 2) {
                path3D.close()
                canvas.drawPath(path3D, paintPoligono3D)
            }
            canvas.drawPath(path3D, paintAresta3D)

            return bitmap
        }

        val rawFaces = mutableListOf<Face3D>()

        if (exprClean == "cubo") {
            val s = 3.0
            val p0 = Point3D(-s, -s, -s); val p1 = Point3D(s, -s, -s); val p2 = Point3D(s, s, -s); val p3 = Point3D(-s, s, -s)
            val p4 = Point3D(-s, -s, s); val p5 = Point3D(s, -s, s); val p6 = Point3D(s, s, s); val p7 = Point3D(-s, s, s)
            
            rawFaces.add(Face3D(p0, p1, p2, p3)) 
            rawFaces.add(Face3D(p4, p5, p6, p7)) 
            rawFaces.add(Face3D(p0, p1, p5, p4)) 
            rawFaces.add(Face3D(p1, p2, p6, p5)) 
            rawFaces.add(Face3D(p2, p3, p7, p6)) 
            rawFaces.add(Face3D(p3, p0, p4, p7)) 
            
            minZ = -s; maxZ = s
        } 
        else if (exprClean == "paralelepipedo") {
            val sx = 4.0; val sy = 2.0; val sz = 3.0
            val p0 = Point3D(-sx, -sy, -sz); val p1 = Point3D(sx, -sy, -sz); val p2 = Point3D(sx, sy, -sz); val p3 = Point3D(-sx, sy, -sz)
            val p4 = Point3D(-sx, -sy, sz); val p5 = Point3D(sx, -sy, sz); val p6 = Point3D(sx, sy, sz); val p7 = Point3D(-sx, sy, sz)
            
            rawFaces.add(Face3D(p0, p1, p2, p3)); rawFaces.add(Face3D(p4, p5, p6, p7))
            rawFaces.add(Face3D(p0, p1, p5, p4)); rawFaces.add(Face3D(p1, p2, p6, p5))
            rawFaces.add(Face3D(p2, p3, p7, p6)); rawFaces.add(Face3D(p3, p0, p4, p7))
            
            minZ = -sz; maxZ = sz
        }
        else {
            val gridX = Array(gridSize + 1) { DoubleArray(gridSize + 1) }
            val gridY = Array(gridSize + 1) { DoubleArray(gridSize + 1) }
            val gridZ = Array(gridSize + 1) { DoubleArray(gridSize + 1) }

            if (exprClean.contains("sphere") || exprClean.contains("esfera")) {
                val radius = 4.0
                for (i in 0..gridSize) {
                    val u = i * Math.PI / gridSize
                    for (j in 0..gridSize) {
                        val v = j * 2 * Math.PI / gridSize
                        gridX[i][j] = radius * Math.sin(u) * Math.cos(v)
                        gridY[i][j] = radius * Math.sin(u) * Math.sin(v)
                        gridZ[i][j] = radius * Math.cos(u)
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            } else if (exprClean.contains("torus") || exprClean.contains("toroide")) {
                val R = 3.0
                val r = 1.2
                for (i in 0..gridSize) {
                    val u = i * 2 * Math.PI / gridSize
                    for (j in 0..gridSize) {
                        val v = j * 2 * Math.PI / gridSize
                        gridX[i][j] = (R + r * Math.cos(v)) * Math.cos(u)
                        gridY[i][j] = (R + r * Math.cos(v)) * Math.sin(u)
                        gridZ[i][j] = r * Math.sin(v)
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            } else if (exprClean.contains("cylinder") || exprClean.contains("cilindro")) {
                val radius = 3.0
                for (i in 0..gridSize) {
                    val u = -4.0 + i * 8.0 / gridSize
                    for (j in 0..gridSize) {
                        val v = j * 2 * Math.PI / gridSize
                        gridX[i][j] = radius * Math.cos(v)
                        gridY[i][j] = radius * Math.sin(v)
                        gridZ[i][j] = u
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            } else if (exprClean.contains("cone") && !exprClean.contains("z")) {
                for (i in 0..gridSize) {
                    val u = i * 4.0 / gridSize
                    for (j in 0..gridSize) {
                        val v = j * 2 * Math.PI / gridSize
                        gridX[i][j] = u * Math.cos(v)
                        gridY[i][j] = u * Math.sin(v)
                        gridZ[i][j] = u - 2.0
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            } else if (exprClean.contains(";") && exprClean.count { it == ';' } >= 2) {
                val partes = exprClean.split(";")
                val parserX = MathParser3D(partes[0].trim())
                val parserY = MathParser3D(partes[1].trim())
                val parserZ = MathParser3D(partes[2].trim())

                for (i in 0..gridSize) {
                    val u = -Math.PI + i * (2 * Math.PI) / gridSize
                    for (j in 0..gridSize) {
                        val v = -Math.PI + j * (2 * Math.PI) / gridSize
                        val x = parserX.parse(u, v)
                        val y = parserY.parse(u, v)
                        val z = parserZ.parse(u, v)
                        gridX[i][j] = if (x.isNaN() || x.isInfinite()) 0.0 else x
                        gridY[i][j] = if (y.isNaN() || y.isInfinite()) 0.0 else y
                        gridZ[i][j] = if (z.isNaN() || z.isInfinite()) 0.0 else z
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            } else if (exprClean.contains("z")) {
                // Implementação para funções implícitas de 3 variáveis f(x, y, z) = 0
                val parser3V = MathParser3D(expressao)

                val gridX1 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }
                val gridY1 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }
                val gridZ1 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }

                val gridX2 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }
                val gridY2 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }
                val gridZ2 = Array(gridSize + 1) { DoubleArray(gridSize + 1) { Double.NaN } }

                for (i in 0..gridSize) {
                    val x = -5.0 + i * 10.0 / gridSize
                    for (j in 0..gridSize) {
                        val y = -5.0 + j * 10.0 / gridSize

                        val zRoots = mutableListOf<Double>()
                        var prevZ = -6.0
                        var prevVal = try { parser3V.parse(x, y, prevZ) } catch (e: Exception) { Double.NaN }
                        var currZ = -5.9
                        while (currZ <= 6.0) {
                            val currVal = try { parser3V.parse(x, y, currZ) } catch (e: Exception) { Double.NaN }
                            if (!prevVal.isNaN() && !currVal.isNaN() && !prevVal.isInfinite() && !currVal.isInfinite()) {
                                if (prevVal * currVal <= 0.0 || Math.abs(currVal) < 0.05) {
                                    val rootZ = if (Math.abs(currVal - prevVal) > 1e-5) {
                                        prevZ - prevVal * (currZ - prevZ) / (currVal - prevVal)
                                    } else {
                                        currZ
                                    }
                                    if (!rootZ.isNaN() && !rootZ.isInfinite() && rootZ in -6.0..6.0) {
                                        if (zRoots.isEmpty() || Math.abs(zRoots.last() - rootZ) > 0.2) {
                                            zRoots.add(rootZ)
                                        }
                                    }
                                }
                            }
                            prevZ = currZ
                            prevVal = currVal
                            currZ += 0.1
                        }

                        if (zRoots.size >= 1) {
                            gridX1[i][j] = x
                            gridY1[i][j] = y
                            gridZ1[i][j] = zRoots[0]
                        }
                        if (zRoots.size >= 2) {
                            gridX2[i][j] = x
                            gridY2[i][j] = y
                            gridZ2[i][j] = zRoots[1]
                        }
                    }
                }

                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ1[i][j]
                        val z21 = gridZ1[i + 1][j]
                        val z22 = gridZ1[i + 1][j + 1]
                        val z12 = gridZ1[i][j + 1]

                        if (!z11.isNaN() && !z21.isNaN() && !z22.isNaN() && !z12.isNaN()) {
                            if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                            if (z21 < minZ) minZ = z21; if (z21 > maxZ) maxZ = z21
                            if (z22 < minZ) minZ = z22; if (z22 > maxZ) maxZ = z22
                            if (z12 < minZ) minZ = z12; if (z12 > maxZ) maxZ = z12

                            val p1 = Point3D(gridX1[i][j], gridY1[i][j], z11)
                            val p2 = Point3D(gridX1[i + 1][j], gridY1[i + 1][j], z21)
                            val p3 = Point3D(gridX1[i + 1][j + 1], gridY1[i + 1][j + 1], z22)
                            val p4 = Point3D(gridX1[i][j + 1], gridY1[i][j + 1], z12)
                            rawFaces.add(Face3D(p1, p2, p3, p4))
                        }
                    }
                }

                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ2[i][j]
                        val z21 = gridZ2[i + 1][j]
                        val z22 = gridZ2[i + 1][j + 1]
                        val z12 = gridZ2[i][j + 1]

                        if (!z11.isNaN() && !z21.isNaN() && !z22.isNaN() && !z12.isNaN()) {
                            if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11
                            if (z21 < minZ) minZ = z21; if (z21 > maxZ) maxZ = z21
                            if (z22 < minZ) minZ = z22; if (z22 > maxZ) maxZ = z22
                            if (z12 < minZ) minZ = z12; if (z12 > maxZ) maxZ = z12

                            val p1 = Point3D(gridX2[i][j], gridY2[i][j], z11)
                            val p2 = Point3D(gridX2[i + 1][j], gridY2[i + 1][j], z21)
                            val p3 = Point3D(gridX2[i + 1][j + 1], gridY2[i + 1][j + 1], z22)
                            val p4 = Point3D(gridX2[i][j + 1], gridY2[i][j + 1], z12)
                            rawFaces.add(Face3D(p1, p2, p3, p4))
                        }
                    }
                }
            } else {
                val parser = MathParser3D(expressao)
                for (i in 0..gridSize) {
                    val x = -5.0 + i * 10.0 / gridSize
                    for (j in 0..gridSize) {
                        val y = -5.0 + j * 10.0 / gridSize
                        gridX[i][j] = x
                        gridY[i][j] = y
                        var z = try { parser.parse(x, y) } catch (and: Exception) { 0.0 }
                        gridZ[i][j] = if (z.isNaN() || z.isInfinite()) 0.0 else z
                    }
                }
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        val z11 = gridZ[i][j]
                        val z21 = gridZ[i + 1][j]
                        val z22 = gridZ[i + 1][j + 1]
                        val z12 = gridZ[i][j + 1]
                        
                        if (z11 < minZ) minZ = z11; if (z11 > maxZ) maxZ = z11

                        val p1 = Point3D(gridX[i][j], gridY[i][j], z11)
                        val p2 = Point3D(gridX[i + 1][j], gridY[i + 1][j], z21)
                        val p3 = Point3D(gridX[i + 1][j + 1], gridY[i + 1][j + 1], z22)
                        val p4 = Point3D(gridX[i][j + 1], gridY[i][j + 1], z12)
                        
                        rawFaces.add(Face3D(p1, p2, p3, p4))
                    }
                }
            }
        }

        if (maxZ == minZ) maxZ = minZ + 1.0

        val floorZ = minZ - 1.2
        val gridSteps = 8

        for (i in 0..gridSteps) {
            val currVal = -5.0 + i * (10.0 / gridSteps)
            val pStart = project(currVal, -5.0, floorZ)
            val pEnd = project(currVal, 5.0, floorZ)
            canvas.drawLine(pStart.first, pStart.second, pEnd.first, pEnd.second, paintGridPlane)

            val pStart2 = project(-5.0, currVal, floorZ)
            val pEnd2 = project(5.0, currVal, floorZ)
            canvas.drawLine(pStart2.first, pStart2.second, pEnd2.first, pEnd2.second, paintGridPlane)
        }

        val originBottom = project(0.0, 0.0, floorZ)
        val originTop = project(0.0, 0.0, maxZ + 1.5)
        canvas.drawLine(originBottom.first, originBottom.second, originTop.first, originTop.second, paintAxisZ)

        val xAxisStart = project(-5.0, 0.0, floorZ)
        val xAxisEnd = project(5.0, 0.0, floorZ)
        canvas.drawLine(xAxisStart.first, xAxisStart.second, xAxisEnd.first, xAxisEnd.second, paintAxisX)

        val yAxisStart = project(0.0, -5.0, floorZ)
        val yAxisEnd = project(0.0, 5.0, floorZ)
        canvas.drawLine(yAxisStart.first, yAxisStart.second, yAxisEnd.first, yAxisEnd.second, paintAxisY)

        val tickSteps = 4
        for (k in -tickSteps..tickSteps) {
            if (k == 0) continue
            val valCoord = k * (5.0 / tickSteps)
            val pX = project(valCoord, 0.0, floorZ)
            canvas.drawText(valCoord.toInt().toString(), pX.first, pX.second + 22f, paintAxisText)

            val pY = project(0.0, valCoord, floorZ)
            canvas.drawText(valCoord.toInt().toString(), pY.first - 18f, pY.second + 6f, paintAxisText)
        }

        val zSteps = 3
        val zRange = maxZ - minZ
        for (k in 1..zSteps) {
            val zVal = minZ + k * (zRange / zSteps)
            val pZ = project(0.0, 0.0, zVal)
            canvas.drawText(String.format("%.1f", zVal), pZ.first - 30f, pZ.second + 6f, paintAxisText)
        }

        val quads = mutableListOf<Quad3D>()
        for (face in rawFaces) {
            val p1 = project(face.p1.x, face.p1.y, face.p1.z)
            val p2 = project(face.p2.x, face.p2.y, face.p2.z)
            val p3 = project(face.p3.x, face.p3.y, face.p3.z)
            val p4 = project(face.p4.x, face.p4.y, face.p4.z)

            val avgDepth = (p1.third + p2.third + p3.third + p4.third) / 4f
            val avgRawZ = ((face.p1.z + face.p2.z + face.p3.z + face.p4.z) / 4.0).toFloat()

            quads.add(Quad3D(
                p1.first, p1.second, p2.first, p2.second, 
                p3.first, p3.second, p4.first, p4.second, 
                avgDepth, avgRawZ
            ))
        }

        quads.sortBy { it.avgZ }

        val paintFill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val paintStroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.0f; color = Color.parseColor("#1E293B"); isAntiAlias = true }
        val path = Path()

        for (quad in quads) {
            val normZ = ((quad.rawZ - minZ) / (maxZ - minZ)).coerceIn(0.0, 1.0).toFloat()
            val hue = (240f - normZ * 240f)
            val colorVal = Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))

            paintFill.color = colorVal

            path.reset()
            path.moveTo(quad.p1x, quad.p1y)
            path.lineTo(quad.p2x, quad.p2y)
            path.lineTo(quad.p3x, quad.p3y)
            path.lineTo(quad.p4x, quad.p4y)
            path.close()

            canvas.drawPath(path, paintFill)
            canvas.drawPath(path, paintStroke)
        }

        return bitmap
    }
}