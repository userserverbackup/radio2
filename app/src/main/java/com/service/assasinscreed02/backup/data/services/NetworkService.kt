package com.service.assasinscreed02.backup.data.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Servicio para manejar operaciones de red
 */
class NetworkService {
    
    companion object {
        private const val TAG = "NetworkService"
    }
    
    /**
     * Verifica si hay conexión WiFi disponible
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            Log.d(TAG, "Network capabilities: $capabilities")
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando WiFi: ${e.message}")
            false
        }
    }
    
    /**
     * Verifica si hay conexión de datos móviles disponible
     */
    fun isMobileDataConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando datos móviles: ${e.message}")
            false
        }
    }
    
    /**
     * Verifica si hay conexión disponible según el modo especificado
     */
    fun isConnectionAvailable(context: Context, forzarConDatos: Boolean): Boolean {
        return if (forzarConDatos) {
            isWifiConnected(context) || isMobileDataConnected(context)
        } else {
            isWifiConnected(context)
        }
    }
} 