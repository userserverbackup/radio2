package com.service.assasinscreed02

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.net.NetworkInterface
import java.util.*

class DeviceInfo(private val context: Context) {
    companion object {
        private const val TAG = "DeviceInfo"
    }

    data class DeviceData(
        val deviceId: String,
        val deviceName: String,
        val ipAddress: String,
        val macAddress: String,
        val androidVersion: String,
        val manufacturer: String,
        val model: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Obtiene un ID único del dispositivo
     */
    fun getDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            // Combinar con información adicional para mayor unicidad
            val deviceInfo = "${Build.MANUFACTURER}_${Build.MODEL}_$androidId"
            deviceInfo.replace(" ", "_").replace("-", "_")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo device ID: ${e.message}")
            "unknown_device_${System.currentTimeMillis()}"
        }
    }

    /**
     * Obtiene el nombre del dispositivo
     */
    fun getDeviceName(): String {
        return try {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            
            if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo device name: ${e.message}")
            "Unknown Device"
        }
    }

    /**
     * Obtiene la dirección IP del dispositivo
     */
    fun getIpAddress(): String {
        return try {
            // Intentar obtener IP de WiFi primero
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    @Suppress("DEPRECATION")
                    val ipAddress = wifiInfo.ipAddress
                    if (ipAddress != 0) {
                        return "${ipAddress and 0xFF}.${ipAddress shr 8 and 0xFF}.${ipAddress shr 16 and 0xFF}.${ipAddress shr 24 and 0xFF}"
                    }
                }
            }

            // Si no hay WiFi, buscar en todas las interfaces de red
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.indexOf(':') < 0) {
                        val ip = inetAddress.hostAddress
                        if (ip != null && !ip.startsWith("169.254")) { // Evitar IPs link-local
                            return ip
                        }
                    }
                }
            }
            
            "0.0.0.0" // IP por defecto si no se encuentra
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo IP address: ${e.message}")
            "0.0.0.0"
        }
    }

    /**
     * Obtiene la dirección MAC del dispositivo
     */
    fun getMacAddress(): String {
        return try {
            // Intentar obtener MAC de WiFi primero
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val macAddress = wifiInfo.macAddress
                    if (macAddress != null && macAddress != "02:00:00:00:00:00") {
                        return macAddress
                    }
                }
            }

            // Si no hay WiFi, buscar en todas las interfaces de red
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val macAddress = networkInterface.hardwareAddress
                if (macAddress != null && macAddress.isNotEmpty()) {
                    val macString = macAddress.joinToString(":") { "%02X".format(it) }
                    if (macString != "00:00:00:00:00:00") {
                        return macString
                    }
                }
            }
            
            "00:00:00:00:00:00" // MAC por defecto si no se encuentra
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo MAC address: ${e.message}")
            "00:00:00:00:00:00"
        }
    }

    /**
     * Obtiene la versión de Android
     */
    fun getAndroidVersion(): String {
        return try {
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo Android version: ${e.message}")
            "Android Unknown"
        }
    }

    /**
     * Obtiene el fabricante del dispositivo
     */
    fun getManufacturer(): String {
        return try {
            Build.MANUFACTURER
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo manufacturer: ${e.message}")
            "Unknown"
        }
    }

    /**
     * Obtiene el modelo del dispositivo
     */
    fun getModel(): String {
        return try {
            Build.MODEL
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo model: ${e.message}")
            "Unknown"
        }
    }

    /**
     * Obtiene toda la información del dispositivo
     */
    fun getDeviceData(): DeviceData {
        return DeviceData(
            deviceId = getDeviceId(),
            deviceName = getDeviceName(),
            ipAddress = getIpAddress(),
            macAddress = getMacAddress(),
            androidVersion = getAndroidVersion(),
            manufacturer = getManufacturer(),
            model = getModel()
        )
    }

    /**
     * Obtiene información del dispositivo como string formateado
     */
    fun getDeviceInfoString(): String {
        val deviceData = getDeviceData()
        return """
            📱 Dispositivo: ${deviceData.deviceName}
            🆔 ID: ${deviceData.deviceId}
            🌐 IP: ${deviceData.ipAddress}
            📡 MAC: ${deviceData.macAddress}
            🤖 Android: ${deviceData.androidVersion}
            🏭 Fabricante: ${deviceData.manufacturer}
            📋 Modelo: ${deviceData.model}
        """.trimIndent()
    }

    /**
     * Obtiene información del dispositivo como JSON
     */
    fun getDeviceInfoJson(): String {
        val deviceData = getDeviceData()
        return """
            {
                "deviceId": "${deviceData.deviceId}",
                "deviceName": "${deviceData.deviceName}",
                "ipAddress": "${deviceData.ipAddress}",
                "macAddress": "${deviceData.macAddress}",
                "androidVersion": "${deviceData.androidVersion}",
                "manufacturer": "${deviceData.manufacturer}",
                "model": "${deviceData.model}",
                "timestamp": ${deviceData.timestamp}
            }
        """.trimIndent()
    }
} 