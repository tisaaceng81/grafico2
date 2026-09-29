package com.isaacx.isaacplot

import android.os.Bundle
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Grafico_estatisticoActivity : AppCompatActivity() {

    
    private lateinit var scaleDetector: ScaleGestureDetector
    private val matrix = Matrix()
    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("grafico_estatistico", "layout", packageName))

        try {
            val logoView = findViewById<ImageView>(resources.getIdentifier("logo4", "id", packageName))
            val inputStream = assets.open("logo4.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            logoView?.setImageBitmap(bitmap)
        } catch (and: Exception) {
            and.printStackTrace()
        }

        val edtDados = findViewById<EditText>(resources.getIdentifier("edtDadosEstatisticos", "id", packageName))
        val btnCalcular = findViewById<Button>(resources.getIdentifier("btnCalcularEstatistica", "id", packageName))
        val txtResultados = findViewById<TextView>(resources.getIdentifier("txtResultados", "id", packageName))
        val imgCanvas = findViewById<ImageView>(resources.getIdentifier("imgGraficoCanvasEstatistico", "id", packageName))

        
        imgCanvas?.scaleType = ImageView.ScaleType.MATRIX

        
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1.0f, 6.0f) 
                
                matrix.setScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                imgCanvas?.imageMatrix = matrix
                return true
            }
        })

        
        imgCanvas?.setOnTouchListener { _, event ->
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
                        matrix.postTranslate(dx, dy)
                        imgCanvas.imageMatrix = matrix
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
            true
        }

        btnCalcular?.setOnClickListener {
            
            scaleFactor = 1.0f
            matrix.reset()
            imgCanvas?.imageMatrix = matrix

            val entradaTexto = edtDados?.text?.toString() ?: ""
            processarEMostrarGrafico(entradaTexto, txtResultados, imgCanvas)
        }

        val amostraPadrao = "12, 15, 14, 10, 18, 22, 15, 16, 17, 19, 13, 15, 14, 16, 20, 21, 11, 14, 16, 17"
        edtDados?.setText(amostraPadrao)
        processarEMostrarGrafico(amostraPadrao, txtResultados, imgCanvas)
    }

    private fun processarEMostrarGrafico(
        str: String,
        txtResultados: TextView?,
        imgCanvas: ImageView?
    ) {
        val numeros = str.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }

        if (numeros.size < 2) {
            txtResultados?.setText("Insira pelo menos 2 numeros validos separados por virgula.")
            val emptyBitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(emptyBitmap)
            canvas.drawColor(Color.WHITE)
            imgCanvas?.setImageBitmap(emptyBitmap)
            return
        }

        val n = numeros.size
        val media = numeros.average()
        
        
        val esperanca = media
        
        val somaQuadradosDiferenca = numeros.sumOf { Math.pow(it - media, 2.0) }
        val variancia = somaQuadradosDiferenca / (n - 1)
        val desvioPadrao = Math.sqrt(variancia)

        
        val frequencias = numeros.groupingBy { it }.eachCount()
        val maxFreq = frequencias.values.maxOrNull() ?: 0
        val modas = frequencias.filter { it.value == maxFreq }.keys
        val modaStr = if (modas.size == numeros.size) "Amodal" else modas.joinToString(", ") { String.format("%.1f", it) }

        
        val desvioPadraoPop = Math.sqrt(numeros.sumOf { Math.pow(it - media, 2.0) } / n)
        val curtose = if (desvioPadraoPop > 0.0) {
            (numeros.sumOf { Math.pow(it - media, 4.0) } / n) / Math.pow(desvioPadraoPop, 4.0)
        } else 0.0
        
        val tipoCurtose = when {
            Math.abs(curtose - 3.0) < 0.2 -> "Mesocúrtica"
            curtose > 3.0 -> "Leptocúrtica"
            else -> "Platicúrtica"
        }

        val textoRes = String.format(
            "Esperança E[X]: %.2f | Var: %.2f | Desvio P: %.2f\nModa: %s | Curtose: %.2f (%s)", 
            esperanca, variancia, desvioPadrao, modaStr, curtose, tipoCurtose
        )
        txtResultados?.setText(textoRes)

        val bitmap = desenharHistogramaEGauss(numeros, media, desvioPadrao)
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun desenharHistogramaEGauss(
        dados: List<Double>,
        media: Double,
        desvioPadrao: Double
    ): Bitmap {
        val width = 900
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 80f
        val renderWidth = width - 2 * margin
        val renderHeight = height - 2 * margin

        val minData = dados.minOrNull() ?: 0.0
        val maxData = dados.maxOrNull() ?: 1.0

        val rangeVal = if (maxData == minData) 1.0 else maxData - minData
        val numBins = Math.min(10, Math.max(5, Math.sqrt(dados.size.toDouble()).toInt()))
        val binWidthData = rangeVal / numBins

        val counts = IntArray(numBins)
        for (v in dados) {
            var binIndex = ((v - minData) / binWidthData).toInt()
            if (binIndex >= numBins) binIndex = numBins - 1
            if (binIndex < 0) binIndex = 0
            counts[binIndex]++
        }

        val maxCount = (counts.maxOrNull() ?: 1).toDouble()

        val paintGrid = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 2f
            isAntiAlias = true
        }

        val paintAxis = Paint().apply {
            color = Color.parseColor("#334155")
            strokeWidth = 4f
            isAntiAlias = true
        }

        val paintText = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 20f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val paintBarFill = Paint().apply {
            color = Color.parseColor("#93C5FD")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintBarStroke = Paint().apply {
            color = Color.parseColor("#2563EB")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val paintGaussLine = Paint().apply {
            color = Color.parseColor("#DC2626")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val paintMeanLine = Paint().apply {
            color = Color.parseColor("#16A34A")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val yTicks = 5
        for (i in 0..yTicks) {
            val yVal = (maxCount * i / yTicks)
            val py = height - margin - (i.toFloat() / yTicks) * renderHeight
            canvas.drawLine(margin, py, width - margin, py, paintGrid)

            val paintTextLeft = Paint(paintText).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(String.format("%.0f", yVal), margin - 15f, py + 6f, paintTextLeft)
        }

        val barWidthPx = renderWidth / numBins
        for (i in 0 until numBins) {
            val left = margin + i * barWidthPx
            val right = left + barWidthPx
            val barHeightPx = (counts[i] / maxCount * renderHeight).toFloat()
            val top = height - margin - barHeightPx
            val bottom = height - margin

            canvas.drawRect(left, top, right, bottom, paintBarFill)
            canvas.drawRect(left, top, right, bottom, paintBarStroke)

            val binCenterData = minData + (i + 0.5) * binWidthData
            canvas.drawText(String.format("%.1f", binCenterData), left + barWidthPx / 2f, height - margin + 35f, paintText)
        }

        canvas.drawLine(margin, height - margin, width - margin, height - margin, paintAxis)
        canvas.drawLine(margin, margin, margin, height - margin, paintAxis)

        if (desvioPadrao > 0.0) {
            val totalAreaHistogram = dados.size * binWidthData
            val pathGauss = Path()
            var started = false

            val steps = 300
            val xStartData = minData - binWidthData
            val xEndData = maxData + binWidthData

            for (s in 0..steps) {
                val xVal = xStartData + s * (xEndData - xStartData) / steps
                val exponent = -0.5 * Math.pow((xVal - media) / desvioPadrao, 2.0)
                val pdf = (1.0 / (desvioPadrao * Math.sqrt(2.0 * Math.PI))) * Math.exp(exponent)
                val expectedCountDensity = pdf * totalAreaHistogram / binWidthData

                val px = (margin + ((xVal - minData) / rangeVal) * renderWidth).toFloat()
                val py = (height - margin - (expectedCountDensity / maxCount) * renderHeight).toFloat()

                if (px in margin..(width - margin)) {
                    if (!started) {
                        pathGauss.moveTo(px, py.coerceIn(margin, height - margin))
                        started = true
                    } else {
                        pathGauss.lineTo(px, py.coerceIn(margin, height - margin))
                    }
                }
            }
            canvas.drawPath(pathGauss, paintGaussLine)
        }

        val meanPx = (margin + ((media - minData) / rangeVal) * renderWidth).toFloat()
        if (meanPx in margin..(width - margin)) {
            canvas.drawLine(meanPx, margin, meanPx, height - margin, paintMeanLine)
            val paintMeanText = Paint(paintText).apply {
                color = Color.parseColor("#16A34A")
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(String.format("E[X] = %.2f", media), meanPx, margin - 15f, paintMeanText)
        }

        return bitmap
    }
}