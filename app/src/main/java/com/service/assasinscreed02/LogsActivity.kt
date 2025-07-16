package com.service.assasinscreed02

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.os.PowerManager
import android.widget.TextView

class LogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        val txtAdvertencia = TextView(this)
        txtAdvertencia.textSize = 16f
        txtAdvertencia.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
        txtAdvertencia.setPadding(0, 0, 0, 16)
        txtAdvertencia.text = ""

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                txtAdvertencia.text = "⚠️ La app está siendo restringida por optimización de batería. Esto puede afectar el funcionamiento de los backups automáticos."
            }
        }

        val root = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerLogs).parent as android.view.ViewGroup
        root.addView(txtAdvertencia, 1) // Insertar advertencia arriba del RecyclerView

        val recycler = findViewById<RecyclerView>(R.id.recyclerLogs)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = LogsAdapter(obtenerLineasLog())
    }

    private fun obtenerLineasLog(): List<String> {
        // Puedes cambiar el nombre del archivo según el log que quieras mostrar
        val logFile = File(filesDir.parentFile, "radio_crash_log.txt")
        return if (logFile.exists()) {
            logFile.readLines().takeLast(500) // Solo las últimas 500 líneas para evitar sobrecarga
        } else {
            listOf("No se encontró el archivo de logs.")
        }
    }
} 