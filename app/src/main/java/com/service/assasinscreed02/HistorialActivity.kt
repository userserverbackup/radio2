package com.service.assasinscreed02

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistorialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        val recycler = findViewById<RecyclerView>(R.id.recyclerHistorial)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = BackupHistoryAdapter(obtenerHistorialBackups())
    }

    private fun obtenerHistorialBackups(): List<BackupWorker.BackupHistoryEntry> {
        val prefs = getSharedPreferences("BackupPrefs", MODE_PRIVATE)
        val archivos = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()
        return archivos.mapNotNull { hash ->
            val parts = hash.split("|||")
            if (parts.size == 3) BackupWorker.BackupHistoryEntry(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
        }.sortedByDescending { it.timestamp }
    }
} 