package com.isaacx.isaacplot

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.ImageView
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("activity_main", "layout", packageName))

        try {
            val logoView = findViewById<ImageView>(resources.getIdentifier("logo1", "id", packageName))
            val inputStream = assets.open("logo1.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            logoView?.setImageBitmap(bitmap)
        } catch (and: Exception) {
            and.printStackTrace()
        }

        val btnGrafico2D = findViewById<Button>(resources.getIdentifier("btnGrafico2D", "id", packageName))
        btnGrafico2D?.setOnClickListener {
            val intent = Intent(this, Grafico_2dActivity::class.java)
            startActivity(intent)
        }

        val btnGrafico3D = findViewById<Button>(resources.getIdentifier("btnGrafico3D", "id", packageName))
        btnGrafico3D?.setOnClickListener {
            val intent = Intent(this, Grafico_3dActivity::class.java)
            startActivity(intent)
        }

        val btnGraficoEstatistico = findViewById<Button>(resources.getIdentifier("btnGraficoEstatistico", "id", packageName))
        btnGraficoEstatistico?.setOnClickListener {
            val intent = Intent(this, Grafico_estatisticoActivity::class.java)
            startActivity(intent)
        }

        val btnProbabilidade = findViewById<Button>(resources.getIdentifier("btnProbabilidade", "id", packageName))
        btnProbabilidade?.setOnClickListener {
            val intent = Intent(this, Grafico_probabilidadeActivity::class.java)
            startActivity(intent)
        }
    }
}