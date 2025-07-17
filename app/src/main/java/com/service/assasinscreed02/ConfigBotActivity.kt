package com.service.assasinscreed02

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigBotActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_bot)

        val edtToken = findViewById<EditText>(R.id.edtToken)
        val edtChatId = findViewById<EditText>(R.id.edtChatId)
        val edtChatIdSecundario = findViewById<EditText>(R.id.edtChatIdSecundario)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarBot)

        // Cargar valores actuales
        ErrorHandler.obtenerConfigBot(this).let { (token, chatId) ->
            edtToken.setText(token ?: "")
            edtChatId.setText(chatId ?: "")
        }
        // Cargar chat_id secundario si existe
        val prefs = getSharedPreferences("BotConfig", MODE_PRIVATE)
        val chatIdSecundario = prefs.getString("bot_chat_id_secundario", "")
        edtChatIdSecundario.setText(chatIdSecundario ?: "")

        btnGuardar.setOnClickListener {
            val token = edtToken.text.toString().trim()
            val chatId = edtChatId.text.toString().trim()
            val chatIdSecundario = edtChatIdSecundario.text.toString().trim()
            if (token.isNotEmpty() && chatId.isNotEmpty()) {
                try {
                    ErrorHandler.guardarConfigBot(this, token, chatId)
                    // Guardar chat_id secundario
                    prefs.edit().putString("bot_chat_id_secundario", chatIdSecundario).apply()
                    Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error guardando configuración: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Completa ambos campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 