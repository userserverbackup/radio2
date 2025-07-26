package com.service.assasinscreed02

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.net.NetworkInterface
import java.util.*
import java.io.File

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
            Log.d(TAG, "Iniciando obtención de MAC address...")
            
            // Método 1: Intentar obtener MAC de WiFi
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val macAddress = wifiInfo.macAddress
                    Log.d(TAG, "MAC de WiFi API: $macAddress")
                    if (macAddress != null && macAddress != "02:00:00:00:00:00" && macAddress != "00:00:00:00:00:00") {
                        Log.d(TAG, "✅ MAC obtenida de WiFi API: $macAddress")
                        return macAddress
                    }
                }
            }

            // Método 2: Buscar en todas las interfaces de red
            Log.d(TAG, "Buscando en interfaces de red...")
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            var interfaceCount = 0
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                interfaceCount++
                Log.d(TAG, "Interfaz encontrada: ${networkInterface.name} - Activa: ${networkInterface.isUp}")
                
                val macAddress = networkInterface.hardwareAddress
                if (macAddress != null && macAddress.isNotEmpty()) {
                    val macString = macAddress.joinToString(":") { "%02X".format(it) }
                    Log.d(TAG, "MAC de interfaz ${networkInterface.name}: $macString")
                    if (macString != "00:00:00:00:00:00" && macString != "02:00:00:00:00:00") {
                        Log.d(TAG, "✅ MAC obtenida de interfaz ${networkInterface.name}: $macString")
                        return macString
                    } else {
                        Log.d(TAG, "❌ MAC inválida en interfaz ${networkInterface.name}: $macString")
                    }
                } else {
                    Log.d(TAG, "❌ No hay MAC en interfaz ${networkInterface.name}")
                }
            }
            Log.d(TAG, "Total de interfaces encontradas: $interfaceCount")

            // Método 3: Intentar leer desde archivos del sistema (requiere permisos)
            Log.d(TAG, "Intentando leer desde archivos del sistema...")
            val macFromSystem = getMacFromSystemFiles()
            if (macFromSystem.isNotEmpty()) {
                Log.d(TAG, "✅ MAC obtenida de archivos del sistema: $macFromSystem")
                return macFromSystem
            }

            // Método 4: Generar MAC basada en ANDROID_ID (fallback)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            val generatedMac = generateMacFromAndroidId(androidId)
            Log.d(TAG, "⚠️ MAC generada desde ANDROID_ID (fallback): $generatedMac")
            return generatedMac
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo MAC address: ${e.message}")
            // Fallback final: generar MAC desde ANDROID_ID
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            generateMacFromAndroidId(androidId)
        }
    }

    /**
     * Intenta obtener MAC desde archivos del sistema
     */
    private fun getMacFromSystemFiles(): String {
        return try {
            val files = listOf(
                "/sys/class/net/wlan0/address",
                "/sys/class/net/eth0/address",
                "/sys/class/net/p2p0/address",
                "/sys/class/net/wlan1/address",
                "/sys/class/net/rmnet0/address",
                "/sys/class/net/rmnet1/address",
                "/sys/class/net/rmnet2/address",
                "/sys/class/net/ccmni0/address",
                "/sys/class/net/ccmni1/address",
                "/sys/class/net/ccmni2/address"
            )
            
            for (filePath in files) {
                try {
                    val file = File(filePath)
                    Log.d(TAG, "Verificando archivo: $filePath - Existe: ${file.exists()}, Se puede leer: ${file.canRead()}")
                    if (file.exists() && file.canRead()) {
                        val mac = file.readText().trim()
                        Log.d(TAG, "Contenido del archivo $filePath: '$mac'")
                        if (mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
                            Log.d(TAG, "✅ MAC válida encontrada en $filePath: $mac")
                            return mac
                        } else {
                            Log.d(TAG, "❌ MAC inválida en $filePath: $mac")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No se pudo leer $filePath: ${e.message}")
                }
            }
            Log.d(TAG, "❌ No se encontró MAC válida en archivos del sistema")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo archivos del sistema: ${e.message}")
            ""
        }
    }

    /**
     * Genera una MAC única basada en ANDROID_ID
     */
    private fun generateMacFromAndroidId(androidId: String): String {
        return try {
            // Crear un hash del ANDROID_ID
            val hash = androidId.hashCode()
            val bytes = ByteArray(6)
            
            // Usar los primeros 6 bytes del hash
            bytes[0] = 0x02.toByte() // MAC local unicast
            bytes[1] = (hash shr 16 and 0xFF).toByte()
            bytes[2] = (hash shr 8 and 0xFF).toByte()
            bytes[3] = (hash and 0xFF).toByte()
            bytes[4] = (hash shr 24 and 0xFF).toByte()
            bytes[5] = (hash shr 16 and 0xFF).toByte()
            
            // Formatear como MAC
            bytes.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generando MAC desde ANDROID_ID: ${e.message}")
            "02:00:00:00:00:00"
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