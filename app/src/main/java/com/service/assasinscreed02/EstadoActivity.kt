package com.service.assasinscreed02

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class EstadoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estado)

        val txtEstadoDetalle = findViewById<TextView>(R.id.txtEstadoDetalle)
        txtEstadoDetalle.text = obtenerEstadoDetallado()
    }

    private fun obtenerEstadoDetallado(): String {
        val prefs = getSharedPreferences("BackupPrefs", MODE_PRIVATE)
        val ultima = prefs.getLong("ultima_conexion", 0L)
        val anterior = prefs.getLong("anterior_conexion", 0L)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val ultimaStr = if (ultima != 0L) sdf.format(Date(ultima)) else "-"
        val anteriorStr = if (anterior != 0L) sdf.format(Date(anterior)) else "-"
        val intervalo = prefs.getInt("intervalo_backup", 24)
        val archivos = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()
        val totalBackups = archivos.size
        val errores = prefs.getString("ultimo_error", "Sin errores recientes")
        return "Última ejecución: $ultimaStr\nAnterior: $anteriorStr\nIntervalo: $intervalo horas\nTotal de archivos respaldados: $totalBackups\nErrores recientes: $errores"
    }
} 