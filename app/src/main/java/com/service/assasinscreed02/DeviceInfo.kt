package com.service.assasinscreed02

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

@Entity(tableName = "device_info")
data class DeviceInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val deviceName: String,
    val androidVersion: String,
    val appVersion: String,
    val lastSeen: Long,
    val isActive: Boolean = true,
    val serverUrl: String,
    val lastBackupTime: Long = 0,
    val totalBackups: Int = 0,
    val successfulBackups: Int = 0,
    val failedBackups: Int = 0,
    val deviceModel: String = "",
    val manufacturer: String = "",
    val screenResolution: String = "",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val networkType: String = "",
    val freeStorage: Long = 0,
    val totalStorage: Long = 0,
    // NUEVO CAMPO:
    val ipAddress: String = ""
)

fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                    return address.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "0.0.0.0"
}

fun registrarDispositivo(context: Context, deviceInfo: DeviceInfo) {
    val url = deviceInfo.serverUrl + "/api/register"
    val json = JSONObject().apply {
        put("deviceId", deviceInfo.deviceId)
        put("deviceName", deviceInfo.deviceName)
        put("androidVersion", deviceInfo.androidVersion)
        put("appVersion", deviceInfo.appVersion)
        put("serverUrl", deviceInfo.serverUrl)
        put("ipAddress", getLocalIpAddress())
        // Agrega otros campos si es necesario
    }

    Log.d("Registro", "Enviando JSON de registro: $json")
    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
    val request = Request.Builder().url(url).post(body).build()
    val client = OkHttpClient()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d("Registro", "Registro exitoso: ${response.body?.string()}")
            } else {
                Log.e("Registro", "Error en registro: ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e("Registro", "Excepción en registro: ${e.message}")
        }
    }.start()
} 