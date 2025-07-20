package com.service.assasinscreed02

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.os.PowerManager
import android.widget.TextView
import android.widget.Button
import android.content.Intent
import android.widget.Toast

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

        // Mostrar errores críticos si existen
        val errorLog = obtenerErroresCriticos()
        if (errorLog.isNotEmpty()) {
            val txtErrores = TextView(this)
            txtErrores.textSize = 16f
            txtErrores.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            txtErrores.setPadding(0, 0, 0, 16)
            txtErrores.text = "\u26A0\uFE0F Errores críticos recientes:\n" + errorLog.joinToString("\n")
            root.addView(txtErrores, 2) // Insertar debajo de la advertencia
        }

        // Botón para enviar el log de errores críticos
        val btnEnviarErrores = Button(this)
        btnEnviarErrores.text = "Enviar errores críticos"
        btnEnviarErrores.setOnClickListener {
            enviarErroresPorCorreo()
        }
        root.addView(btnEnviarErrores, 3) // Debajo de los errores

        // Botón para limpiar el log de errores críticos
        val btnLimpiarErrores = Button(this)
        btnLimpiarErrores.text = "Limpiar errores críticos"
        btnLimpiarErrores.setOnClickListener {
            ErrorHandler.limpiarLogErrores(this)
            Toast.makeText(this, "Log de errores críticos limpiado", Toast.LENGTH_SHORT).show()
            recreate()
        }
        root.addView(btnLimpiarErrores, 4)
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

    private fun obtenerErroresCriticos(): List<String> {
        val errorFile = File(filesDir, "radio2_error_log.txt")
        return if (errorFile.exists()) {
            errorFile.readLines().takeLast(20) // Solo los últimos 20 errores críticos
        } else {
            emptyList()
        }
    }

    private fun enviarErroresPorCorreo() {
        val errorFile = File(filesDir, "radio2_error_log.txt")
        if (!errorFile.exists()) {
            Toast.makeText(this, "No hay errores críticos para enviar", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, packageName + ".provider", errorFile)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, "Log de errores críticos Radio2")
        intent.putExtra(Intent.EXTRA_TEXT, "Adjunto el log de errores críticos de la app Radio2.")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Enviar log de errores"))
    }
} 