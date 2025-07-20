package com.service.assasinscreed02

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.io.File
import android.content.Context
import android.app.AlertDialog
import kotlinx.coroutines.*

class BackupProgressActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BackupProgressActivity"
        const val EXTRA_FORZAR_CON_DATOS = "forzar_con_datos"
        
        // Variables estáticas para comunicación con BackupUtils
        @Volatile var isBackupCancelled = false
        @Volatile var currentProgress = 0
        @Volatile var totalFiles = 0
        @Volatile var currentFile = ""
        @Volatile var currentPhase = ""
        @Volatile var filesSent = 0
        @Volatile var filesError = 0
        @Volatile var currentFileProgress = 0
        @Volatile var currentFileSize = 0L
    }
    
    private lateinit var txtEstadoActual: TextView
    private lateinit var txtFaseActual: TextView
    private lateinit var progressBarPrincipal: ProgressBar
    private lateinit var txtProgresoPrincipal: TextView
    private lateinit var progressBarArchivo: ProgressBar
    private lateinit var txtArchivoActual: TextView
    private lateinit var txtEnviados: TextView
    private lateinit var txtErrores: TextView
    private lateinit var btnCancelar: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private var forzarConDatos = false
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_progress)
        
        // Obtener parámetros
        forzarConDatos = intent.getBooleanExtra(EXTRA_FORZAR_CON_DATOS, false)
        
        // Inicializar vistas
        inicializarVistas()
        
        // Configurar botón cancelar
        btnCancelar.setOnClickListener {
            mostrarDialogoCancelar()
        }
        
        // Iniciar backup en hilo secundario
        iniciarBackup()
        
        // Iniciar actualización de UI
        iniciarActualizacionUI()
    }
    
    private fun inicializarVistas() {
        txtEstadoActual = findViewById(R.id.txtEstadoActual)
        txtFaseActual = findViewById(R.id.txtFaseActual)
        progressBarPrincipal = findViewById(R.id.progressBarPrincipal)
        txtProgresoPrincipal = findViewById(R.id.txtProgresoPrincipal)
        progressBarArchivo = findViewById(R.id.progressBarArchivo)
        txtArchivoActual = findViewById(R.id.txtArchivoActual)
        txtEnviados = findViewById(R.id.txtEnviados)
        txtErrores = findViewById(R.id.txtErrores)
        btnCancelar = findViewById(R.id.btnCancelar)
        
        // Configurar barras de progreso
        progressBarPrincipal.max = 100
        progressBarArchivo.max = 100
    }
    
    private fun iniciarBackup() {
        backupScope.launch {
            try {
                // Resetear variables estáticas
                isBackupCancelled = false
                currentProgress = 0
                totalFiles = 0
                currentFile = ""
                currentPhase = "Iniciando backup..."
                filesSent = 0
                filesError = 0
                currentFileProgress = 0
                currentFileSize = 0L
                // Ejecutar backup con callback de progreso
                val success = BackupUtils.runBackupWithProgress(this@BackupProgressActivity, forzarConDatos) { progress, total, file, phase, sent, errors, fileProgress, fileSize ->
                    currentProgress = progress
                    totalFiles = total
                    currentFile = file
                    currentPhase = phase
                    filesSent = sent
                    filesError = errors
                    currentFileProgress = fileProgress
                    currentFileSize = fileSize
                }
                withContext(Dispatchers.Main) {
                    mostrarResultadoFinal(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en backup: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    mostrarResultadoFinal(false)
                }
            }
        }
    }
    
    private fun iniciarActualizacionUI() {
        val updateRunnable = object : Runnable {
            override fun run() {
                actualizarUI()
                if (!isBackupCancelled) {
                    handler.postDelayed(this, 100) // Actualizar cada 100ms
                }
            }
        }
        handler.post(updateRunnable)
    }
    
    private fun actualizarUI() {
        // Actualizar estado y fase
        txtEstadoActual.text = if (isBackupCancelled) "Backup cancelado" else "Backup en progreso..."
        txtFaseActual.text = "Fase: $currentPhase"
        
        // Actualizar barra de progreso principal
        if (totalFiles > 0) {
            val progress = ((currentProgress.toFloat() / totalFiles) * 100).toInt()
            progressBarPrincipal.progress = progress
            txtProgresoPrincipal.text = "$currentProgress / $totalFiles archivos"
        } else {
            progressBarPrincipal.progress = 0
            txtProgresoPrincipal.text = "0 / 0 archivos"
        }
        
        // Actualizar barra de progreso del archivo actual
        progressBarArchivo.progress = currentFileProgress
        txtArchivoActual.text = if (currentFile.isNotEmpty()) "Archivo: $currentFile" else "Archivo: -"
        
        // Actualizar estadísticas
        txtEnviados.text = "Enviados: $filesSent"
        txtErrores.text = "Errores: $filesError"
    }
    
    private fun mostrarDialogoCancelar() {
        AlertDialog.Builder(this)
            .setTitle("Cancelar Backup")
            .setMessage("¿Estás seguro de que quieres cancelar el backup?")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                cancelarBackup()
            }
            .setNegativeButton("No, continuar", null)
            .show()
    }
    
    private fun cancelarBackup() {
        isBackupCancelled = true
        btnCancelar.isEnabled = false
        btnCancelar.text = "Cancelando..."
        txtEstadoActual.text = "Cancelando backup..."
    }
    
    private fun mostrarResultadoFinal(success: Boolean) {
        val mensaje = if (success) {
            "Backup completado exitosamente\nEnviados: $filesSent, Errores: $filesError"
        } else {
            "Error en el backup\nEnviados: $filesSent, Errores: $filesError"
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (success) "Backup Completado" else "Error en Backup")
            .setMessage(mensaje)
            .setPositiveButton("OK") { _, _ ->
                reanudarBackupAutomatico()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        backupScope.cancel()
        if (!isFinishing) {
            reanudarBackupAutomatico()
        }
    }

    private fun reanudarBackupAutomatico() {
        try {
            val intervalo = ErrorHandler.obtenerIntervalo(this).toLong()
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.service.assasinscreed02.BackupWorker>(intervalo, java.util.concurrent.TimeUnit.HOURS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 1, java.util.concurrent.TimeUnit.HOURS)
                .build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "backup_trabajo",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Backup automático reanudado tras backup forzado")
        } catch (e: Exception) {
            Log.e(TAG, "Error reanudando backup automático: ${e.message}", e)
        }
    }
} 