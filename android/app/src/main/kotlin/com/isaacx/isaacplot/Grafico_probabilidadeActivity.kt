package com.isaacx.isaacplot

import android.app.AlertDialog
import android.os.Bundle
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

class Grafico_probabilidadeActivity : AppCompatActivity() {

    private var scaleDetector: ScaleGestureDetector? = null
    private val matrix = Matrix()
    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("grafico_probabilidade", "layout", packageName))

        try {
            val logoView = findViewById<ImageView>(resources.getIdentifier("logo5", "id", packageName))
            val inputStream = assets.open("logo5.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                logoView?.setImageBitmap(bitmap)
            }
        } catch (and: Exception) {
            and.printStackTrace()
        }

        val edtProblema = findViewById<EditText>(resources.getIdentifier("edtProblemaProbabilidade", "id", packageName))
        val btnCalcular = findViewById<Button>(resources.getIdentifier("btnCalcularProbabilidade", "id", packageName))
        val txtResultado = findViewById<TextView>(resources.getIdentifier("txtResultadoProbabilidade", "id", packageName))
        val imgCanvas = findViewById<ImageView>(resources.getIdentifier("imgCanvasProbabilidade", "id", packageName))
        val txtTituloApp = findViewById<TextView>(resources.getIdentifier("txtTituloApp", "id", packageName))
        val edtSeletorModulo = findViewById<EditText>(resources.getIdentifier("edtSeletorModulo", "id", packageName))

        val listaModulos = arrayOf(
            "Distribuição Normal (Gaussiana)",
            "Distribuição Binomial",
            "Distribuição de Poisson",
            "Teorema de Bayes",
            "Probabilidade Condicional",
            "Análise Combinatória",
            "Binômio de Newton",
            "Cadeias de Markov",
            "Espaço Amostral Discreto"
        )
        
        edtSeletorModulo?.setText(listaModulos[8])

        edtSeletorModulo?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Selecione o Módulo")
                .setItems(listaModulos) { _, which ->
                    edtSeletorModulo?.setText(listaModulos[which])
                }
                .show()
        }

        imgCanvas?.scaleType = ImageView.ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1.0f, 6.0f)
                val factor = scaleFactor / prevScale
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imgCanvas?.imageMatrix = matrix
                return true
            }
        })

        imgCanvas?.setOnTouchListener { view, event ->
            view?.parent?.requestDisallowInterceptTouchEvent(true)

            scaleDetector?.onTouchEvent(event)
            if (scaleDetector?.isInProgress == false) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        matrix.postTranslate(dx, dy)
                        imgCanvas?.imageMatrix = matrix
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view?.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            true
        }

        btnCalcular?.setOnClickListener {
            val textoDigitado = edtProblema?.text?.toString() ?: ""
            val moduloSelecionado = edtSeletorModulo?.text?.toString() ?: "Espaço Amostral Discreto"
            
            resolverProblemaTextoInteligente(textoDigitado, moduloSelecionado, txtResultado, imgCanvas, txtTituloApp)

            imgCanvas?.post {
                val vWidth = imgCanvas?.width?.toFloat() ?: 0f
                val vHeight = imgCanvas?.height?.toFloat() ?: 0f
                val bWidth = 900f 
                val bHeight = 800f 

                if (vWidth > 0f && vHeight > 0f) {
                    scaleFactor = Math.min(vWidth / bWidth, vHeight / bHeight)
                    matrix.reset()
                    matrix.postScale(scaleFactor, scaleFactor)
                    
                    val dx = (vWidth - (bWidth * scaleFactor)) / 2f
                    val dy = (vHeight - (bHeight * scaleFactor)) / 2f
                    matrix.postTranslate(dx, dy)
                    
                    imgCanvas?.imageMatrix = matrix
                }
            }
        }

        val exemploInicial = "Qual a probabilidade de ao lançar 2 dados obter uma soma igual a 7?"
        edtProblema?.setText(exemploInicial)
    }

    private fun isConexaoInternetDisponivel(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun requisitarGroqRender(textoBruto: String, moduloSelecionado: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://isaacplot-backend.onrender.com/parsear")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val jsonInput = JSONObject().apply {
                    put("str", textoBruto)
                    put("modulo", moduloSelecionado)
                }

                conn.outputStream.use { os ->
                    val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode == 200) {
                    val resposta = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(resposta)
                    if (jsonResponse.getBoolean("sucesso")) {
                        return@withContext jsonResponse.getJSONObject("dados").toString()
                    }
                }
                null
            } catch (and: Exception) {
                and.printStackTrace()
                null
            }
        }
    }

    private fun resolverProblemaTextoInteligente(str: String, moduloSelecionado: String, txtResultado: TextView?, imgCanvas: ImageView?, txtTituloApp: TextView?) {
        txtTituloApp?.text = moduloSelecionado

        if (isConexaoInternetDisponivel()) {
            txtResultado?.text = "Processando com inteligência avançada (Groq/Render)..."
            lifecycleScope.launch {
                val jsonNuvem = requisitarGroqRender(str, moduloSelecionado)
                if (jsonNuvem != null) {
                    processarResultadoNuvem(jsonNuvem, moduloSelecionado, txtResultado, imgCanvas)
                } else {
                    executarCalculoLocal(str, moduloSelecionado, txtResultado, imgCanvas)
                }
            }
        } else {
            executarCalculoLocal(str, moduloSelecionado, txtResultado, imgCanvas)
        }
    }

    private fun executarCalculoLocal(str: String, moduloSelecionado: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        var textoFormatado = str.lowercase()
        textoFormatado = textoFormatado.replace(Regex("\\b(dois)\\b"), "2")
        textoFormatado = textoFormatado.replace(Regex("\\b(três|tres)\\b"), "3")
        textoFormatado = textoFormatado.replace(Regex("\\bquatro\\b"), "4")
        textoFormatado = textoFormatado.replace(Regex("\\bcinco\\b"), "5")
        textoFormatado = textoFormatado.replace(Regex("\\bseis\\b"), "6")
        textoFormatado = textoFormatado.replace(Regex("\\bsete\\b"), "7")
        textoFormatado = textoFormatado.replace(Regex("\\boito\\b"), "8")
        textoFormatado = textoFormatado.replace(Regex("\\bnove\\b"), "9")
        textoFormatado = textoFormatado.replace(Regex("\\bdez\\b"), "10")

        try {
            when (moduloSelecionado) {
                "Distribuição Normal (Gaussiana)" -> resolverProbabilidadeGaussiana(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Distribuição Binomial" -> resolverBinomialUniversal(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Distribuição de Poisson" -> resolverPoissonUniversal(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Teorema de Bayes" -> resolverTeoremaDeBayes(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Probabilidade Condicional" -> resolverProbabilidadeCondicionalConjuntos(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Análise Combinatória" -> resolverCombinatoriaPura(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Binômio de Newton" -> resolverBinomioNewton(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                "Cadeias de Markov" -> resolverProcessoEstocastico(textoFormatado, txtResultado, imgCanvas)
                "Espaço Amostral Discreto" -> resolverProbabilidadeDiscretaAvancada(textoFormatado, textoFormatado, txtResultado, imgCanvas)
                else -> resolverProbabilidadeDiscretaAvancada(textoFormatado, textoFormatado, txtResultado, imgCanvas)
            }
        } catch (and: Exception) {
            txtResultado?.text = "Erro ao processar cálculo: ${and.message}"
        }
    }

    private fun processarResultadoNuvem(jsonStr: String, moduloSelecionado: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        try {
            val json = JSONObject(jsonStr)
            when (moduloSelecionado) {
                "Distribuição Normal (Gaussiana)" -> {
                    val m = json.optDouble("media", 500.0)
                    val dp = json.optDouble("desvio", 3.2)
                    val x = json.optDouble("x", 495.0)
                    val maior = json.optBoolean("ehMaior", false)
                    val tSint = "media $m desvio $dp x $x ${if (maior) "maior" else "menor"}"
                    resolverProbabilidadeGaussiana(tSint, tSint, txtResultado, imgCanvas)
                }
                "Distribuição Binomial" -> {
                    val n = json.optInt("n", 10)
                    val k = json.optInt("k", 3)
                    val p = json.optDouble("p", 0.5)
                    val tSint = "ensaios $n sucessos $k probabilidade $p"
                    resolverBinomialUniversal(tSint, tSint, txtResultado, imgCanvas)
                }
                "Distribuição de Poisson" -> {
                    val lambda = json.optDouble("lambda", 3.0)
                    val k = json.optInt("k", 1)
                    val tSint = "taxa $lambda exatamente $k"
                    resolverPoissonUniversal(tSint, tSint, txtResultado, imgCanvas)
                }
                "Teorema de Bayes" -> {
                    val pA1 = json.optDouble("pA1", 0.4)
                    val pA2 = json.optDouble("pA2", 0.6)
                    val pBA1 = json.optDouble("pBA1", 0.05)
                    val pBA2 = json.optDouble("pBA2", 0.02)
                    val tSint = "maquina a $pA1 maquina b $pA2 defeito a $pBA1 defeito b $pBA2"
                    resolverTeoremaDeBayes(tSint, tSint, txtResultado, imgCanvas)
                }
                "Probabilidade Condicional" -> {
                    val total = json.optDouble("total", 100.0)
                    val inter = json.optDouble("inter", 20.0)
                    val cond = json.optDouble("condicao", 50.0)
                    val tSint = "total $total ambas $inter dado que $cond"
                    resolverProbabilidadeCondicionalConjuntos(tSint, tSint, txtResultado, imgCanvas)
                }
                "Análise Combinatória" -> {
                    val n = json.optInt("n", 5)
                    val k = json.optInt("k", 2)
                    val type = json.optString("type", "Combinacao")
                    val tSint = "$type total $n tomados $k"
                    resolverCombinatoriaPura(tSint, tSint, txtResultado, imgCanvas)
                }
                "Binômio de Newton" -> {
                    val n = json.optInt("n", 5)
                    val termo = json.optInt("termo", 3)
                    val tSint = "grau $n termo $termo"
                    resolverBinomioNewton(tSint, tSint, txtResultado, imgCanvas)
                }
                "Cadeias de Markov" -> {
                    val p11 = json.optDouble("p11", 0.8)
                    val p12 = json.optDouble("p12", 0.2)
                    val p21 = json.optDouble("p21", 0.4)
                    val p22 = json.optDouble("p22", 0.6)
                    val passos = json.optInt("nPassos", 2)
                    val tSint = "continue $p11 sol for chuva $p12 chuva for sol $p21 continue chuvoso $p22 passos $passos"
                    resolverProcessoEstocastico(tSint, txtResultado, imgCanvas)
                }
                "Espaço Amostral Discreto" -> {
                    val qtd = json.optInt("quantidade", 2)
                    val alvo = json.optDouble("alvo", 7.0)
                    val tipoSistema = json.optString("tipoSistema", "dado")
                    val totalElementos = json.optInt("total_elementos", 0)
                    val alvosDisponiveis = json.optInt("alvos_disponiveis", 0)
                    resolverDiscretoDireto(qtd, alvo, tipoSistema, totalElementos, alvosDisponiveis, txtResultado, imgCanvas)
                }
                else -> {
                    resolverProbabilidadeDiscretaAvancada("", "", txtResultado, imgCanvas)
                }
            }
        } catch (and: Exception) {
            txtResultado?.text = "Erro ao interpretar resposta da nuvem: ${and.message}"
        }
    }

    private fun exibirHtmlFormatado(txtResultado: TextView?, htmlContent: String) {
        if (txtResultado != null) {
            txtResultado?.text = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    }

    private fun resolverDiscretoDireto(quantidadeItens: Int, alvo: Double, tipoSistema: String, totalElementos: Int = 0, alvosDisponiveis: Int = 0, txtResultado: TextView?, imgCanvas: ImageView?) {
        val casosFavoraveis: Double
        val totalEspacoAmostral: Double
        val sistemaStr: String

        
        if (totalElementos > 0) {
            sistemaStr = "${tipoSistema.uppercase()} (${totalElementos} itens)"
            
            
            totalEspacoAmostral = combinacao(totalElementos, quantidadeItens)
            
            val alvosDesejados = alvo.toInt()
            val restantesNoAmbiente = totalElementos - alvosDisponiveis
            val restantesAExtrair = quantidadeItens - alvosDesejados
            
            
            casosFavoraveis = if (alvosDesejados <= alvosDisponiveis && restantesAExtrair <= restantesNoAmbiente && restantesAExtrair >= 0) {
                combinacao(alvosDisponiveis, alvosDesejados) * combinacao(restantesNoAmbiente, restantesAExtrair)
            } else {
                0.0
            }
        } else {
            
            val isMoeda = tipoSistema.contains("moeda", true)
            val faces = if (isMoeda) 2 else 6
            sistemaStr = "Lançamento de ${quantidadeItens} ${if (faces == 2) "moedas" else "dados"}"
            totalEspacoAmostral = faces.toDouble().pow(quantidadeItens.toDouble())
            
            val resultadoSimulacao = simularRecursivoAvancado(quantidadeItens, faces, null) { valores ->
                if (isMoeda) {
                    
                    val qtdCaras = valores.count { it == 1 }.toDouble()
                    qtdCaras == alvo
                } else {
                    
                    val metrica = if (quantidadeItens == 1) valores[0].toDouble() else valores.sum().toDouble()
                    metrica == alvo
                }
            }
            casosFavoraveis = resultadoSimulacao.first.toDouble()
        }
        
        val probabilidade = if (totalEspacoAmostral > 0) (casosFavoraveis / totalEspacoAmostral) * 100.0 else 0.0
        
        val favStr = casosFavoraveis.toLong().toString()
        val totalStr = totalEspacoAmostral.toLong().toString()
        val probStr = String.format("%.2f", probabilidade)

        val html = """
            <b>ESPAÇO AMOSTRAL DISCRETO (INTELIGENTE)</b><br>
            ─────────────────────────────<br>
            Sistema: ${sistemaStr}<br>
            Alvo Buscado: ${alvo.toInt()}<br><br>
            <b>Resolução Direta via Nuvem:</b><br>
            • Casos Favoráveis: ${favStr}<br>
            • Total do Espaço Amostral: ${totalStr}<br><br>
            Cálculo: P = ${favStr} / ${totalStr}<br><br>
            ➔ <b>Probabilidade Final:</b> <b>${probStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(casosFavoraveis, totalEspacoAmostral, "Favoráveis")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverProcessoEstocastico(str: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        val p11 = if (numeros.isNotEmpty()) (if (numeros[0] > 1.0) numeros[0] / 100.0 else numeros[0]) else 0.7
        val p12 = if (numeros.size > 1) (if (numeros[1] > 1.0) numeros[1] / 100.0 else numeros[1]) else 0.3
        val p21 = if (numeros.size > 2) (if (numeros[2] > 1.0) numeros[2] / 100.0 else numeros[2]) else 0.4
        val p22 = if (numeros.size > 3) (if (numeros[3] > 1.0) numeros[3] / 100.0 else numeros[3]) else 0.6
        val n = if (numeros.size > 4) numeros[4].toInt() else 2

        var v1 = 1.0
        var v2 = 0.0

        val passosDetalhados = StringBuilder()
        passosDetalhados.append("<b>Passo a Passo da Evolução Estocástica:</b><br>")
        for (i in 1..n) {
            val novoV1 = v1 * p11 + v2 * p21
            val novoV2 = v1 * p12 + v2 * p22
            val strV1 = String.format("%.4f", novoV1)
            val strV2 = String.format("%.4f", novoV2)
            passosDetalhados.append("• Passo ${i}: v<sub>${i}</sub> = [${strV1} , ${strV2}]<br>")
            v1 = novoV1
            v2 = novoV2
        }

        val htmlP11 = String.format("%.2f", p11)
        val htmlP12 = String.format("%.2f", p12)
        val htmlP21 = String.format("%.2f", p21)
        val htmlP22 = String.format("%.2f", p22)
        val probA = String.format("%.2f", v1 * 100.0)
        val probB = String.format("%.2f", v2 * 100.0)

        val html = """
            <b>CADEIAS DE MARKOV (2 Estados)</b><br>
            ─────────────────────────────<br>
            <b>Matriz de Transição (P):</b><br>
            &nbsp;&nbsp;| ${htmlP11} &nbsp;&nbsp; ${htmlP12} |<br>
            &nbsp;&nbsp;| ${htmlP21} &nbsp;&nbsp; ${htmlP22} |<br><br>
            Estado Inicial: v<sub>0</sub> = [1.00 , 0.00] | Passos: <b>${n}</b><br>
            Equação iterativa: v<sup>(k)</sup> = v<sup>(k-1)</sup> · P<br><br>
            ${passosDetalhados}<br>
            ➔ <b>Prob. Final Estado A:</b> ${probA}%<br>
            ➔ <b>Prob. Final Estado B:</b> ${probB}%
        """.trimIndent()
        
        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(v1 * 100.0, 100.0, "Prob. Estado A")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverBinomioNewton(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        var n: Int? = null
        for (match in matchResults) {
            val numVal = match.value.toInt()
            val startIdx = match.range.first
            val subContexto = textoLower.substring(Math.max(0, startIdx - 25), startIdx)
            if (subContexto.contains("grau") || subContexto.contains("elevado") || subContexto.contains("expoente")) {
                n = numVal
                break
            }
        }
        if (n == null) {
            n = numeros.filter { it > 1.0 }.maxOrNull()?.toInt() ?: numeros.firstOrNull()?.toInt() ?: 5
        }

        val termoTexto = when {
            textoLower.contains("primeiro") -> 1
            textoLower.contains("segundo") -> 2
            textoLower.contains("terceiro") -> 3
            textoLower.contains("quarto") -> 4
            textoLower.contains("quinto") -> 5
            textoLower.contains("sexto") -> 6
            textoLower.contains("sétimo") || textoLower.contains("setimo") -> 7
            textoLower.contains("oitavo") -> 8
            textoLower.contains("nono") -> 9
            textoLower.contains("décimo") || textoLower.contains("decimo") -> 10
            else -> null
        }

        var termoPedido = termoTexto
        if (termoPedido == null) {
            for (match in matchResults) {
                val numVal = match.value.toInt()
                val startIdx = match.range.first
                val subContexto = textoLower.substring(Math.max(0, startIdx - 25), startIdx)
                if (subContexto.contains("termo")) {
                    termoPedido = numVal
                    break
                }
            }
        }
        if (termoPedido == null) {
            val restantes = numeros.map { it.toInt() }.filter { it != n }
            termoPedido = restantes.firstOrNull() ?: 3
        }

        val k = if (termoPedido > 0) termoPedido - 1 else 0
        val coeficiente = combinacao(n, k)

        val nomeTermo = when (termoPedido) {
            1 -> "primeiro termo"
            2 -> "segundo termo"
            3 -> "terceiro termo"
            4 -> "quarto termo"
            5 -> "quinto termo"
            6 -> "sexto termo"
            7 -> "sétimo termo"
            8 -> "oitavo termo"
            9 -> "nono termo"
            10 -> "décimo termo"
            else -> "termo ${termoPedido}"
        }

        val nFat = fatorial(n).toLong()
        val kFat = fatorial(k).toLong()
        val nMinK = n - k
        val nMinKFat = fatorial(nMinK).toLong()
        val coefStr = coeficiente.toLong().toString()
        val kPlus1 = k + 1

        val html = """
            <b>BINÔMIO DE NEWTON</b><br>
            ─────────────────────────────<br>
            Forma: (x + y)<sup>${n}</sup> | Grau n = ${n}<br>
            Termo Desejado: <b>${nomeTermo}</b> (índice k = ${k})<br><br>
            <b>Fórmula Detalhada do Termo Geral:</b><br>
            T<sub>k+1</sub> = C(n, k) · x<sup>n-k</sup> · y<sup>k</sup><br>
            T<sub>${kPlus1}</sub> = C(${n}, ${k}) · x<sup>${n}-${k}</sup> · y<sup>${k}</sup><br><br>
            <b>Detalhamento do Coeficiente Binomial:</b><br>
            • Fatorial de n: ${n}! = ${nFat}<br>
            • Fatorial de k: ${k}! = ${kFat}<br>
            • Fatorial de (n - k): (${n} - ${k})! = ${nMinKFat}<br><br>
            Cálculo:<br>
            &nbsp;&nbsp;<u>&nbsp;&nbsp;<b>${n}!</b>&nbsp;&nbsp;</u><br>
            &nbsp;&nbsp;<b>${k}! · (${nMinK})!</b><br><br>
            ➔ <b>Coeficiente Final C(${n},${k}):</b> <b>${coefStr}</b>
        """.trimIndent()
        
        exibirHtmlFormatado(txtResultado, html)

        val bitmap = Bitmap.createBitmap(900, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.parseColor("#334155"); textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("Coeficiente: ${coefStr}", 450f, 400f, paint)
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverCombinatoriaPura(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        val n = numeros.maxOrNull()?.toInt() ?: 5
        val k = if (numeros.size > 1) {
            numeros.filter { it.toInt() != n }.minOrNull()?.toInt() ?: n
        } else {
            n
        }

        val resultadoCombinatorio: Double
        val formulaHtml: String
        val nome: String
        
        val nFat = fatorial(n).toLong()
        val kFat = fatorial(k).toLong()
        val nMinK = n - k
        val nMinKFat = fatorial(nMinK).toLong()

        if (textoLower.contains("arranjo") || textoLower.contains("senhas")) {
            nome = "Arranjo Simples"
            formulaHtml = "A(${n},${k}) = n! / (n - k)! = ${n}! / (${n} - ${k})!<br>Fatores calculados passo a passo."
            resultadoCombinatorio = if (n >= k) fatorial(n) / fatorial(n - k) else 0.0
        } else if (textoLower.contains("permutação") || textoLower.contains("permutacao") || textoLower.contains("anagrama")) {
            nome = "Permutação Simples"
            formulaHtml = "P(${n}) = ${n}! = ${nFat}"
            resultadoCombinatorio = fatorial(n)
        } else {
            nome = "Combinação Simples"
            formulaHtml = "C(${n},${k}) = n! / [k! · (n - k)!]<br>C(${n},${k}) = ${n}! / [${k}! · (${n} - ${k})!]"
            resultadoCombinatorio = combinacao(n, k)
        }
        
        val resultadoStr = resultadoCombinatorio.toLong().toString()

        val html = """
            <b>ANÁLISE COMBINATÓRIA</b><br>
            ─────────────────────────────<br>
            Tipo Identificado: <b>${nome}</b><br>
            Parâmetros: n = ${n} | k = ${k}<br><br>
            <b>Resolução Passo a Passo:</b><br>
            ${formulaHtml}<br>
            • Fatorial de n (${n}!): ${nFat}<br>
            • Fatorial de k (${k}!): ${kFat}<br>
            • Fatorial da diferença (${nMinK}!): ${nMinKFat}<br><br>
            ➔ <b>Total de Formas Possíveis:</b> <b>${resultadoStr}</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)

        val bitmap = Bitmap.createBitmap(900, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.parseColor("#2563EB"); textSize = 50f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(String.format("Total = %.0f", resultadoCombinatorio), 450f, 400f, paint)
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverPoissonUniversal(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        var lambda: Double? = null
        var k: Int? = null

        for (match in matchResults) {
            val numVal = match.value.toDouble()
            val startIdx = match.range.first
            val subContexto = textoLower.substring(Math.max(0, startIdx - 30), startIdx)
            if ((subContexto.contains("taxa") || subContexto.contains("média") || subContexto.contains("media")) && lambda == null) {
                lambda = numVal
            } else if ((subContexto.contains("exatamente") || subContexto.contains("vezes") || subContexto.contains("chamadas") || subContexto.contains("acidente") || subContexto.contains("acontecer")) && k == null) {
                k = numVal.toInt()
            }
        }

        val lambdaFinal = lambda ?: numeros.getOrNull(0) ?: 3.0
        val kFinal = k ?: numeros.filter { it != lambdaFinal }.firstOrNull()?.toInt() ?: 1

        val euler = exp(-lambdaFinal)
        val lambdaPow = lambdaFinal.pow(kFinal.toDouble())
        val fatK = fatorial(kFinal)
        val pPoisson = (euler * lambdaPow) / fatK
        val percentual = pPoisson * 100.0
        
        val eulerStr = String.format("%.5f", euler)
        val lambdaPowStr = String.format("%.4f", lambdaPow)
        val fatKStr = fatK.toLong().toString()
        val numeradorStr = String.format("%.5f", euler * lambdaPow)
        val pPoissonStr = String.format("%.5f", pPoisson)
        val percentualStr = String.format("%.2f", percentual)

        val html = """
            <b>DISTRIBUIÇÃO DE POISSON</b><br>
            ─────────────────────────────<br>
            Taxa Média (λ) = ${lambdaFinal} | Ocorrências desejadas (k) = ${kFinal}<br><br>
            <b>Fórmula Geral Detalhada:</b><br>
            P(X = k) = (and<sup>-λ</sup> · λ<sup>k</sup>) / k!<br><br>
            <b>Passo a Passo Completo:</b><br>
            1) Constante de Euler and<sup>-${lambdaFinal}</sup> ≈ ${eulerStr}<br>
            2) Potência da taxa ${lambdaFinal}<sup>${kFinal}</sup> = ${lambdaPowStr}<br>
            3) Fatorial do número de ocorrências ${kFinal}! = ${fatKStr}<br>
            4) Multiplicação do numerador: ${numeradorStr}<br>
            5) Divisão pelo denominador (${fatKStr}): ${pPoissonStr}<br><br>
            ➔ <b>Probabilidade Final:</b> <b>${percentualStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(percentual, 100.0, "Probabilidade")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverBinomialUniversal(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        var n = 10
        var k = 3
        var p = 0.5

        if (textoLower.contains("alternativas")) {
            val alternativas = numeros.find { it in 2.0..10.0 } ?: 4.0
            p = 1.0 / alternativas
            val restantes = numeros.filter { it != alternativas }
            n = restantes.maxOrNull()?.toInt() ?: 5
            k = restantes.minOrNull()?.toInt() ?: 2
        } else {
            val pMatch = numeros.find { (it > 0.0 && it < 1.0) || (it > 1.0 && it < 100.0 && textoLower.contains("%")) }
            if (pMatch != null) {
                p = if (pMatch > 1.0) pMatch / 100.0 else pMatch
                val restantes = numeros.filter { it != pMatch }
                if (restantes.size >= 2) {
                    n = restantes.maxOrNull()?.toInt() ?: 10
                    k = restantes.minOrNull()?.toInt() ?: 3
                } else if (restantes.size == 1) {
                    n = restantes[0].toInt()
                }
            } else if (numeros.size >= 2) {
                n = numeros.maxOrNull()?.toInt() ?: 10
                k = numeros.minOrNull()?.toInt() ?: 3
            }
        }
        
        if (p > 1.0) p /= 100.0
        val q = 1.0 - p

        val comb = combinacao(n, k)
        val pSucesso = p.pow(k.toDouble())
        val pFracasso = q.pow((n - k).toDouble())
        val pBinomial = comb * pSucesso * pFracasso
        val percentual = pBinomial * 100.0
        
        val combStr = comb.toLong().toString()
        val pSucessoStr = String.format("%.5f", pSucesso)
        val pFracassoStr = String.format("%.5f", pFracasso)
        val percentualStr = String.format("%.2f", percentual)
        val nMinK = n - k

        val html = """
            <b>DISTRIBUIÇÃO BINOMIAL</b><br>
            ─────────────────────────────<br>
            Ensaios (n) = ${n} | Sucessos (k) = ${k} | Prob. Sucesso (p) = ${p} | Prob. Fracasso (q) = ${q}<br><br>
            <b>Fórmula de Bernoulli Detalhada:</b><br>
            P(X = k) = C(n, k) · p<sup>k</sup> · q<sup>n-k</sup><br><br>
            <b>Passo a Passo Completo:</b><br>
            1) Combinação C(${n}, ${k}) = ${combStr}<br>
            2) Probabilidade de sucessos p<sup>${k}</sup> ≈ ${pSucessoStr}<br>
            3) Probabilidade de fracassos q<sup>${nMinK}</sup> ≈ ${pFracassoStr}<br>
            4) Produto dos termos: ${combStr} · ${pSucessoStr} · ${pFracassoStr}<br><br>
            ➔ <b>Probabilidade Final:</b> <b>${percentualStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(percentual, 100.0, "Probabilidade")
        imgCanvas?.setImageBitmap(bitmap)
    }
    
    private fun resolverProbabilidadeCondicionalConjuntos(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }.distinct()

        if (numeros.size < 3) {
            txtResultado?.text = "Erro: O problema precisa conter 3 números (Total, Intersecção and Condição)."
            return
        }

        val totalAlunos = numeros.maxOrNull() ?: return
        val subconjuntos = numeros.filter { it != totalAlunos }.sorted()

        val nInterFinal = subconjuntos.getOrNull(0) ?: return
        val nCondicaoFinal = subconjuntos.getOrNull(1) ?: return

        val prob = if (nCondicaoFinal > 0) nInterFinal / nCondicaoFinal else 0.0
        val percentual = prob * 100.0

        val interStr = String.format("%.0f", nInterFinal)
        val condStr = String.format("%.0f", nCondicaoFinal)
        val totalStr = String.format("%.0f", totalAlunos)
        val probStr = String.format("%.2f", percentual)

        val html = """
            <b>PROBABILIDADE CONDICIONAL (CONJUNTOS)</b><br>
            ─────────────────────────────<br>
            Total do Espaço Amostral (N) = ${totalStr}<br>
            Intersecção (Ambas) = ${interStr} | Condição (Dado que) = ${condStr}<br><br>
            <b>Fórmula da Probabilidade Condicional:</b><br>
            P(B | A) = n(A ∩ B) / n(A)<br><br>
            <b>Passo a Passo Detalhado:</b><br>
            1) Casos favoráveis (intersecção): ${interStr}<br>
            2) Espaço amostral reduzido (condição): ${condStr}<br>
            3) Divisão: ${interStr} / ${condStr} = ${String.format("%.4f", prob)}<br><br>
            ➔ <b>Probabilidade Final:</b> <b>${probStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(percentual, 100.0, "Condicional")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverTeoremaDeBayes(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        val pA1 = if (numeros.isNotEmpty()) (if (numeros[0] > 1.0) numeros[0] / 100.0 else numeros[0]) else 0.2
        val pA2 = if (numeros.size > 1) (if (numeros[1] > 1.0) numeros[1] / 100.0 else numeros[1]) else 0.8
        val pBA1 = if (numeros.size > 2) (if (numeros[2] > 1.0) numeros[2] / 100.0 else numeros[2]) else 0.95
        val pBA2 = if (numeros.size > 3) (if (numeros[3] > 1.0) numeros[3] / 100.0 else numeros[3]) else 0.10

        val interA1 = pA1 * pBA1
        val interA2 = pA2 * pBA2
        val pB = interA1 + interA2
        val pA1GivenB = if (pB > 0) interA1 / pB else 0.0
        val probabilidadePercentual = pA1GivenB * 100.0
        
        val interA1Str = String.format("%.4f", interA1)
        val interA2Str = String.format("%.4f", interA2)
        val pBStr = String.format("%.4f", pB)
        val probPosterioriStr = String.format("%.2f", probabilidadePercentual)

        val html = """
            <b>TEOREMA DE BAYES</b><br>
            ─────────────────────────────<br>
            P(A<sub>1</sub>) = ${pA1} | P(A<sub>2</sub>) = ${pA2}<br>
            P(B|A<sub>1</sub>) = ${pBA1} | P(B|A<sub>2</sub>) = ${pBA2}<br><br>
            <b>Passo a Passo Detalhado:</b><br>
            1) Intersecção 1: P(A<sub>1</sub>) · P(B|A<sub>1</sub>) = ${pA1} · ${pBA1} = ${interA1Str}<br>
            2) Intersecção 2: P(A<sub>2</sub>) · P(B|A<sub>2</sub>) = ${pA2} · ${pBA2} = ${interA2Str}<br>
            3) Probabilidade Total P(B) = ${interA1Str} + ${interA2Str} = <b>${pBStr}</b><br><br>
            <b>Fórmula de Bayes P(A<sub>1</sub>|B):</b><br>
            &nbsp;&nbsp;<u>P(A<sub>1</sub>) · P(B|A<sub>1</sub>)</u><br>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;P(B)<br><br>
            ➔ <b>Probabilidade A Posteriori:</b> <b>${probPosterioriStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(pA1GivenB * 100.0, 100.0, "A Posteriori")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverProbabilidadeGaussiana(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()

        if (matchResults.size < 3) {
            txtResultado?.text = "Erro: O str contínuo precisa conter pelo menos 3 números (Média, Desvio Padrão and o Valor X)."
            val emptyBitmap = Bitmap.createBitmap(900, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(emptyBitmap)
            canvas.drawColor(Color.WHITE)
            imgCanvas?.setImageBitmap(emptyBitmap)
            return
        }

        val numeros = matchResults.map { it.value.toDouble() }
        var media: Double? = null
        var desvioPadrao: Double? = null
        var x: Double? = null

        for (match in matchResults) {
            val numVal = match.value.toDouble()
            val startIdx = match.range.first
            val subContexto = textoLower.substring(Math.max(0, startIdx - 35), startIdx)

            if ((subContexto.contains("media") || subContexto.contains("mu") || subContexto.contains("média")) && media == null) {
                media = numVal
            } else if ((subContexto.contains("desvio") || subContexto.contains("sigma") || subContexto.contains("dp")) && desvioPadrao == null) {
                desvioPadrao = numVal
            } else if ((subContexto.contains("menor") || subContexto.contains("maior") || subContexto.contains("igual") || subContexto.contains("que") || subContexto.contains("valor") || subContexto.contains("x") || subContexto.contains("ml") || subContexto.contains("quilos") || subContexto.contains("acima") || subContexto.contains("abaixo")) && x == null) {
                x = numVal
            }
        }

        val m = media ?: numeros[0]
        val dp = desvioPadrao ?: numeros[1]
        val valorX = x ?: numeros.getOrNull(2) ?: numeros.last()

        if (dp <= 0) {
            txtResultado?.text = "Erro: O desvio padrão deve ser estritamente maior que zero."
            return
        }

        val ehMaior = textoLower.contains("maior") || textoLower.contains("acima") || textoLower.contains("superior") || textoLower.contains("mais que")

        val zScore = (valorX - m) / dp
        val cdf = calcularCDFNormal(zScore)
        val probabilidade = if (ehMaior) (1.0 - cdf) * 100.0 else cdf * 100.0
        val tipoCondicao = if (ehMaior) "X ≥" else "X ≤"
        
        val diff = valorX - m
        val zScoreStr = String.format("%.3f", zScore)
        val probStr = String.format("%.2f", probabilidade)

        val html = """
            <b>DISTRIBUIÇÃO NORMAL (GAUSSIANA)</b><br>
            ─────────────────────────────<br>
            Média (μ) = ${m} | Desvio Padrão (σ) = ${dp} | Valor X = ${valorX}<br><br>
            <b>Passo a Passo do Escore Z:</b><br>
            1) Subtração da média: X - μ = ${valorX} - ${m} = ${diff}<br>
            2) Divisão pelo desvio padrão: Z = (X - μ) / σ = ${diff} / ${dp} = <b>${zScoreStr}</b><br>
            3) Consulta da Função de Distribuição Acumulada (CDF) for Z = ${zScoreStr}<br><br>
            ➔ <b>Probabilidade (${tipoCondicao} ${valorX}):</b> <b>${probStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharCurvaGaussiana(m, dp, valorX, ehMaior)
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun resolverProbabilidadeDiscretaAvancada(str: String, textoLower: String, txtResultado: TextView?, imgCanvas: ImageView?) {
        val regex = Regex("[-+]?\\d*\\.\\d+|\\d+")
        val matchResults = regex.findAll(str).toList()
        val numeros = matchResults.map { it.value.toDouble() }

        val ehMoeda = textoLower.contains("moeda") || textoLower.contains("moedas") || textoLower.contains("cara") || textoLower.contains("coroa")
        val ehDado = textoLower.contains("dado") || textoLower.contains("dados")
        val ehUrna = textoLower.contains("urna") || textoLower.contains("bola") || textoLower.contains("bolas") || textoLower.contains("balas") || textoLower.contains("pedras") || textoLower.contains("cartas") || textoLower.contains("fichas") || textoLower.contains("peças") || textoLower.contains("pessoas")

        var quantidadeItens = 1
        if (!ehUrna) {
            for (i in 0 until matchResults.size) {
                val num = matchResults[i].value.toInt()
                val endIdx = matchResults[i].range.last
                val proxContexto = textoLower.substring(endIdx, Math.min(textoLower.length, endIdx + 20))
                if (proxContexto.contains("dado") || proxContexto.contains("dados") || 
                    proxContexto.contains("moeda") || proxContexto.contains("moedas")) {
                    quantidadeItens = num
                    break
                }
            }
        } else {
            quantidadeItens = numeros.lastOrNull()?.toInt() ?: 3
        }

        val alvo = numeros.lastOrNull() ?: 1.0

        val temCondicao = textoLower.contains("sabendo") || textoLower.contains("dado que") || textoLower.contains("constatado")
        var restricaoPrimeiroItem: Int? = null
        if (temCondicao && numeros.size >= 2) {
            for (num in numeros) {
                if (num in 1.0..6.0 && !num.equals(alvo)) {
                    restricaoPrimeiroItem = num.toInt()
                    break
                }
            }
        }

        val ehMaiorOuIgual = textoLower.contains("maior or igual") || textoLower.contains("no mínimo") || textoLower.contains("ao menos") || textoLower.contains("pelo menos")
        val ehMenorOuIgual = textoLower.contains("menor or igual") || textoLower.contains("no máximo") || textoLower.contains("até")
        val ehMaior = (textoLower.contains("maior") || textoLower.contains("acima") || textoLower.contains("superior") || textoLower.contains("mais que")) && !ehMaiorOuIgual
        val ehMenor = (textoLower.contains("menor") || textoLower.contains("abaixo") || textoLower.contains("inferior") || textoLower.contains("menos que")) && !ehMenorOuIgual

        var totalEspacoAmostral = 1.0
        var casosFavoraveis = 0.0
        val tipoSistema = if (ehMoeda) "Lançamento de Moedas" else if (ehDado) "Lançamento de Dados" else "Retirada de Urna/Combinação"

        when {
            ehMoeda -> {
                val faces = 2
                totalEspacoAmostral = faces.toDouble().pow(quantidadeItens.toDouble())
                casosFavoraveis = simularRecursivoAvancado(quantidadeItens, faces, restricaoPrimeiroItem, { valores ->
                    val qtdCaras = valores.count { it == 1 }.toDouble()
                    avaliarCondicao(qtdCaras, alvo, ehMaior, ehMenor, ehMaiorOuIgual, ehMenorOuIgual)
                }).first.toDouble()
                val espacoReduzido = simularRecursivoAvancado(quantidadeItens, faces, restricaoPrimeiroItem, { _ -> true }).second.toDouble()
                if (espacoReduzido > 0) totalEspacoAmostral = espacoReduzido
            }
            ehDado -> {
                val faces = 6
                totalEspacoAmostral = faces.toDouble().pow(quantidadeItens.toDouble())
                val resultadoSimulacao = simularRecursivoAvancado(quantidadeItens, faces, restricaoPrimeiroItem, { valores ->
                    val metrica = if (quantidadeItens == 1) valores[0].toDouble() else valores.sum().toDouble()
                    avaliarCondicao(metrica, alvo, ehMaior, ehMenor, ehMaiorOuIgual, ehMenorOuIgual)
                })
                casosFavoraveis = resultadoSimulacao.first.toDouble()
                val espacoReduzido = resultadoSimulacao.second.toDouble()
                if (espacoReduzido > 0) {
                    totalEspacoAmostral = espacoReduzido
                }
            }
            ehUrna && numeros.size >= 3 -> {
                val kRetiradasUrna = quantidadeItens
                val cores = numeros.dropLast(1)
                val totalBolas = cores.sum().toInt()
            
                totalEspacoAmostral = combinacao(totalBolas, kRetiradasUrna)
                casosFavoraveis = 0.0
            
                if (textoLower.contains("diferentes") || textoLower.contains("distintas") || textoLower.contains("uma de cada")) {
                    var comb = 1.0
                    for (qtd in cores) {
                        comb *= combinacao(qtd.toInt(), 1)
                    }
                    casosFavoraveis = comb
                } else if (textoLower.contains("mesma") || textoLower.contains("iguais")) {
                    for (qtd in cores) {
                        if (qtd >= kRetiradasUrna.toDouble()) {
                            casosFavoraveis += combinacao(qtd.toInt(), kRetiradasUrna)
                        }
                    }
                } else {
                    casosFavoraveis = if (totalEspacoAmostral > 0) totalEspacoAmostral * 0.5 else 1.0
                }
            }
            else -> {
                val nTotal = if (numeros.isNotEmpty()) numeros[0] else 10.0
                val kRetiradas = if (numeros.size > 1) numeros[1] else 2.0
                totalEspacoAmostral = combinacao(nTotal.toInt(), kRetiradas.toInt())
                casosFavoraveis = if (totalEspacoAmostral > 0) totalEspacoAmostral * 0.5 else 1.0
            }
        }

        if (totalEspacoAmostral <= 0) totalEspacoAmostral = 1.0
        val probabilidade = (casosFavoraveis / totalEspacoAmostral) * 100.0
        
        val favStr = casosFavoraveis.toLong().toString()
        val totalStr = totalEspacoAmostral.toLong().toString()
        val probStr = String.format("%.2f", probabilidade)

        val html = """
            <b>ESPAÇO AMOSTRAL DISCRETO</b><br>
            ─────────────────────────────<br>
            Sistema: ${tipoSistema}<br>
            Quantidade Retirada: ${quantidadeItens} | Cores Mapeadas: ${if (ehUrna && numeros.size >= 3) numeros.dropLast(1).joinToString(", ") else "${quantidadeItens}"}<br><br>
            <b>Passo a Passo Detalhado (Lei de Laplace):</b><br>
            1) Mapeamento completo do espaço amostral gerado.<br>
            2) Filtragem dos casos que satisfazem a condição estabelecida.<br>
            • Casos Favoráveis: ${favStr}<br>
            • Total do Espaço Amostral: ${totalStr}<br><br>
            Cálculo: P = ${favStr} / ${totalStr}<br><br>
            ➔ <b>Probabilidade Final:</b> <b>${probStr}%</b>
        """.trimIndent()

        exibirHtmlFormatado(txtResultado, html)
        val bitmap = desenharGraficoBarrasDiscreto(casosFavoraveis, totalEspacoAmostral, "Favoráveis")
        imgCanvas?.setImageBitmap(bitmap)
    }

    private fun simularRecursivoAvancado(n: Int, faces: Int, restricaoFalta: Int?, condicao: (List<Int>) -> Boolean): Pair<Int, Int> {
        var favoraveis = 0
        var totalValidos = 0

        fun rodar(atual: MutableList<Int>) {
            if (atual.size == n) {
                if (restricaoFalta == null || atual[0] == restricaoFalta) {
                    totalValidos++
                    if (condicao(atual)) {
                        favoraveis++
                    }
                }
                return
            }
            for (i in 1..faces) {
                atual.add(i)
                rodar(atual)
                atual.removeAt(atual.size - 1)
            }
        }

        rodar(mutableListOf())
        return Pair(favoraveis, totalValidos)
    }

    private fun avaliarCondicao(valor: Double, alvo: Double, ehMaior: Boolean, ehMenor: Boolean, ehMaiorOuIgual: Boolean, ehMenorOuIgual: Boolean): Boolean {
        return when {
            ehMaior -> valor > alvo
            ehMenor -> valor < alvo
            ehMaiorOuIgual -> valor >= alvo
            ehMenorOuIgual -> valor <= alvo
            else -> valor == alvo
        }
    }

    private fun fatorial(n: Int): Double {
        if (n <= 1) return 1.0
        var fat = 1.0
        for (i in 2..n) {
            fat *= i.toDouble()
        }
        return fat
    }

    private fun combinacao(n: Int, k: Int): Double {
        if (k > n || k < 0) return 0.0
        return fatorial(n) / (fatorial(k) * fatorial(n - k))
    }

    private fun calcularCDFNormal(z: Double): Double {
        val zMod = Math.abs(z) / Math.sqrt(2.0)
        val t = 1.0 / (1.0 + 0.5 * zMod)
        val ans = t * exp(-zMod * zMod - 1.26551223 +
                t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 +
                t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                t * (-0.82215223 + t * 0.17087277)))))))))
        val erf = 1.0 - ans
        val res = if (z >= 0) erf else -erf
        return 0.5 * (1.0 + res)
    }

    private fun desenharCurvaGaussiana(media: Double, desvioPadrao: Double, valorX: Double, ehMaior: Boolean): Bitmap {
        val width = 900
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 80f
        val renderWidth = width - 2 * margin
        val renderHeight = height - 2 * margin

        val minX = media - 4 * desvioPadrao
        val maxX = media + 4 * desvioPadrao
        val rangeX = maxX - minX

        val maxDensity = 1.0 / (desvioPadrao * sqrt(2.0 * Math.PI))

        val paintGrid = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f; isAntiAlias = true }
        val paintAxis = Paint().apply { color = Color.parseColor("#334155"); strokeWidth = 4f; isAntiAlias = true }
        val paintGaussLine = Paint().apply { color = Color.parseColor("#DC2626"); strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true }
        val paintAreaFill = Paint().apply { color = Color.parseColor("#66DC2626"); style = Paint.Style.FILL; isAntiAlias = true }
        val paintText = Paint().apply { color = Color.parseColor("#475569"); textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        val yTicks = 5
        for (i in 0..yTicks) {
            val py = height - margin - (i.toFloat() / yTicks) * renderHeight
            canvas.drawLine(margin, py, width - margin, py, paintGrid)
        }

        canvas.drawLine(margin, height - margin, width - margin, height - margin, paintAxis)
        canvas.drawLine(margin, margin, margin, height - margin, paintAxis)

        val steps = 300
        val pathGauss = Path()
        val pathArea = Path()
        var startedGauss = false
        var startedArea = false

        for (s in 0..steps) {
            val xData = minX + s * rangeX / steps
            val exponent = -0.5 * ((xData - media) / desvioPadrao).pow(2.0)
            val pdf = maxDensity * exp(exponent)

            val px = margin + ((xData - minX) / rangeX) * renderWidth
            val py = height - margin - (pdf / maxDensity) * renderHeight

            if (!startedGauss) {
                pathGauss.moveTo(px.toFloat(), py.toFloat())
                startedGauss = true
            } else {
                pathGauss.lineTo(px.toFloat(), py.toFloat())
            }

            val atendeCondicao = if (ehMaior) xData >= valorX else xData <= valorX
            if (atendeCondicao) {
                if (!startedArea) {
                    val startX = if (ehMaior) Math.max(valorX, minX) else minX
                    val startPx = margin + ((startX - minX) / rangeX) * renderWidth
                    pathArea.moveTo(startPx.toFloat(), height - margin)
                    pathArea.lineTo(px.toFloat(), py.toFloat())
                    startedArea = true
                } else {
                    pathArea.lineTo(px.toFloat(), py.toFloat())
                }
            }
        }

        if (startedArea) {
            val endX = if (ehMaior) maxX else Math.min(valorX, maxX)
            val endPx = margin + ((endX - minX) / rangeX) * renderWidth
            pathArea.lineTo(endPx.toFloat(), height - margin)
            pathArea.close()
            canvas.drawPath(pathArea, paintAreaFill)
        }

        canvas.drawPath(pathGauss, paintGaussLine)

        canvas.drawText(String.format("%.1f", minX), margin, height - margin + 35f, paintText)
        canvas.drawText(String.format("μ=%.1f", media), margin + renderWidth / 2, height - margin + 35f, paintText)
        canvas.drawText(String.format("%.1f", maxX), width - margin, height - margin + 35f, paintText)

        val pxValorX = margin + ((valorX - minX) / rangeX) * renderWidth
        if (pxValorX in margin..(width - margin)) {
            val paintXLine = Paint().apply { color = Color.parseColor("#2563EB"); strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true }
            canvas.drawLine(pxValorX.toFloat(), margin, pxValorX.toFloat(), height - margin, paintXLine)
            val paintTextX = Paint(paintText).apply { color = Color.parseColor("#2563EB"); isFakeBoldText = true }
            canvas.drawText("X", pxValorX.toFloat(), margin - 10f, paintTextX)
        }

        return bitmap
    }

    private fun desenharGraficoBarrasDiscreto(favoraveis: Double, total: Double, rotulo: String = "Sucessos"): Bitmap {
        val width = 900
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 80f
        val renderWidth = width - 2 * margin
        val renderHeight = height - 2 * margin

        val paintAxis = Paint().apply { color = Color.parseColor("#334155"); strokeWidth = 4f; isAntiAlias = true }
        val paintBar = Paint().apply { color = Color.parseColor("#2563EB"); style = Paint.Style.FILL; isAntiAlias = true }
        val paintText = Paint().apply { color = Color.parseColor("#475569"); textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        canvas.drawLine(margin, height - margin, width - margin, height - margin, paintAxis)
        canvas.drawLine(margin, margin, margin, height - margin, paintAxis)

        val barWidth = 160f
        val left = margin + 200f
        val barHeightRatio = if (total > 0) favoraveis / total else 0.0
        val top = (height - margin) - (barHeightRatio * renderHeight)

        canvas.drawRect(left, top.toFloat(), left + barWidth, height - margin, paintBar)

        val textoRotulo = if (total == 100.0) {
            String.format("%s: %.2f%%", rotulo, favoraveis)
        } else {
            String.format("%s: %.0f / %.0f", rotulo, favoraveis, total)
        }
        canvas.drawText(textoRotulo, left + barWidth / 2, top.toFloat() - 20f, paintText)

        return bitmap
    }
}