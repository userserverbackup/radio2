package com.service.assasinscreed02

import android.content.Context

fun guardarConfigBot(context: Context, token: String, chatId: String) {
    val prefs = context.getSharedPreferences("config_bot", Context.MODE_PRIVATE)
    prefs.edit().putString("token", token).putString("chat_id", chatId).apply()
}

fun obtenerConfigBot(context: Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences("config_bot", Context.MODE_PRIVATE)
    val token = prefs.getString("token", null)
    val chatId = prefs.getString("chat_id", null)
    return if (token != null && chatId != null) Pair(token, chatId) else null
}

fun guardarIntervalo(context: Context, horas: Int) {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    prefs.edit().putInt("intervalo_horas", horas).apply()
}

fun obtenerIntervalo(context: Context): Int {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    return prefs.getInt("intervalo_horas", 24)
}

fun guardarConexion(context: Context) {
    val prefs = context.getSharedPreferences("historial", Context.MODE_PRIVATE)
    val actual = System.currentTimeMillis()
    val anterior = prefs.getLong("ultima", 0L)
    prefs.edit().putLong("anterior", anterior).putLong("ultima", actual).apply()
}

fun obtenerHistorial(context: Context): Pair<Long, Long> {
    val prefs = context.getSharedPreferences("historial", Context.MODE_PRIVATE)
    return Pair(prefs.getLong("ultima", 0L), prefs.getLong("anterior", 0L))
} 