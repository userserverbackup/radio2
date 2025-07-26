# 🔄 Sistema de Cola Centralizada para Múltiples Dispositivos

## 🎯 **Problema Identificado**

Cuando múltiples dispositivos intentan subir archivos simultáneamente al bot de Telegram, se producen:
- **Rate limiting** del bot
- **Conflictos de concurrencia**
- **Reintentos simultáneos**
- **Saturación del servidor**

## 🛠️ **Solución Propuesta: Sistema de Cola Centralizada**

### **Arquitectura del Sistema:**

```
Dispositivo 1 → Cola Central → Bot Telegram
Dispositivo 2 → Cola Central → Bot Telegram
Dispositivo 3 → Cola Central → Bot Telegram
```

### **Componentes del Sistema:**

1. **Servidor de Cola** (GitHub como base de datos)
2. **Sistema de Turnos** por dispositivo
3. **Control de Rate Limiting**
4. **Manejo de Errores Centralizado**

## 📋 **Implementación Técnica**

### **1. Estructura de Cola en GitHub:**

```json
{
  "queue": [
    {
      "id": "uuid-123",
      "device_id": "samsung-sm-a155m-001",
      "device_name": "Samsung SM-A155M",
      "timestamp": 1640995200000,
      "status": "pending",
      "files": [
        {
          "path": "/storage/emulated/0/DCIM/Camera/IMG_001.jpg",
          "size": 2048576,
          "hash": "abc123..."
        }
      ],
      "priority": 1
    }
  ],
  "active_device": "samsung-sm-a155m-001",
  "last_upload": 1640995200000,
  "rate_limit_reset": 1640995260000
}
```

### **2. Flujo de Trabajo:**

#### **Fase 1: Solicitud de Turno**
```kotlin
// El dispositivo solicita un turno
fun requestUploadTurn(deviceId: String): Boolean {
    // 1. Verificar si hay otro dispositivo activo
    // 2. Si no hay, tomar el turno
    // 3. Si hay, esperar en cola
}
```

#### **Fase 2: Subida de Archivos**
```kotlin
// Solo el dispositivo activo puede subir
fun uploadFiles(files: List<File>) {
    // 1. Verificar que es el dispositivo activo
    // 2. Subir archivos con rate limiting
    // 3. Actualizar estado en GitHub
}
```

#### **Fase 3: Liberación de Turno**
```kotlin
// Liberar el turno para el siguiente dispositivo
fun releaseTurn() {
    // 1. Marcar como completado
    // 2. Pasar turno al siguiente dispositivo
    // 3. Actualizar cola
}
```

### **3. Control de Rate Limiting:**

```kotlin
class RateLimitManager {
    private val maxRequestsPerMinute = 20
    private val maxRequestsPerHour = 1000
    
    fun canUpload(): Boolean {
        // Verificar límites de Telegram
        // Verificar tiempo desde última subida
        // Verificar estado del bot
    }
    
    fun waitForNextSlot() {
        // Calcular tiempo de espera
        // Esperar antes de reintentar
    }
}
```

## 🔧 **Comandos del Sistema**

### **Comandos de Administración:**
```
/queue_status - Ver estado de la cola
/queue_clear - Limpiar cola (admin)
/queue_skip - Saltar dispositivo actual
/queue_priority - Cambiar prioridad
```

### **Comandos de Dispositivo:**
```
/request_turn - Solicitar turno de subida
/release_turn - Liberar turno manualmente
/device_status - Ver estado del dispositivo
```

## 📊 **Estados de la Cola**

### **Estados de Dispositivo:**
- **`waiting`** - Esperando turno
- **`active`** - Subiendo archivos
- **`completed`** - Subida completada
- **`error`** - Error en subida
- **`timeout`** - Tiempo agotado

### **Estados de Archivo:**
- **`pending`** - Pendiente de subida
- **`uploading`** - En proceso de subida
- **`completed`** - Subido exitosamente
- **`failed`** - Error en subida
- **`retry`** - Reintentando

## ⚙️ **Configuración del Sistema**

### **Parámetros Ajustables:**
```kotlin
object QueueConfig {
    const val MAX_UPLOAD_TIME = 300000L // 5 minutos por dispositivo
    const val RATE_LIMIT_DELAY = 3000L // 3 segundos entre archivos
    const val MAX_RETRIES = 3 // Máximo 3 reintentos
    const val QUEUE_TIMEOUT = 600000L // 10 minutos de timeout
    const val MAX_QUEUE_SIZE = 100 // Máximo 100 archivos en cola
}
```

### **Prioridades de Dispositivo:**
- **Prioridad 1:** Dispositivos críticos (backup principal)
- **Prioridad 2:** Dispositivos secundarios
- **Prioridad 3:** Dispositivos de prueba

## 🔄 **Algoritmo de Turnos**

### **Round Robin con Prioridades:**
1. Dispositivos con prioridad 1 tienen preferencia
2. Dentro de cada prioridad, se usa round robin
3. Timeout automático si un dispositivo no responde
4. Reintentos automáticos para archivos fallidos

### **Ejemplo de Secuencia:**
```
Tiempo 0-5min: Dispositivo A (Prioridad 1)
Tiempo 5-10min: Dispositivo B (Prioridad 1)
Tiempo 10-15min: Dispositivo C (Prioridad 2)
Tiempo 15-20min: Dispositivo D (Prioridad 2)
Tiempo 20-25min: Dispositivo A (Prioridad 1) - Nuevo ciclo
```

## 🛡️ **Manejo de Errores**

### **Errores Comunes:**
- **Rate Limit Exceeded:** Esperar y reintentar
- **Device Timeout:** Pasar al siguiente dispositivo
- **Network Error:** Reintentar con backoff exponencial
- **Bot Saturation:** Pausar cola temporalmente

### **Recuperación Automática:**
```kotlin
fun handleError(error: UploadError) {
    when (error.type) {
        RATE_LIMIT -> {
            waitForRateLimitReset()
            retryWithBackoff()
        }
        TIMEOUT -> {
            releaseTurn()
            moveToNextDevice()
        }
        NETWORK -> {
            retryWithExponentialBackoff()
        }
        BOT_SATURATION -> {
            pauseQueue()
            notifyAdmin()
        }
    }
}
```

## 📈 **Monitoreo y Estadísticas**

### **Métricas a Monitorear:**
- Tiempo promedio en cola
- Tasa de éxito de subidas
- Tiempo de procesamiento por dispositivo
- Errores por tipo
- Uso de rate limiting

### **Alertas Automáticas:**
- Cola muy larga (>50 archivos)
- Muchos errores consecutivos
- Dispositivo sin respuesta
- Rate limiting frecuente

## 🚀 **Implementación Gradual**

### **Fase 1: Sistema Básico**
- Cola simple en GitHub
- Turnos secuenciales
- Rate limiting básico

### **Fase 2: Sistema Avanzado**
- Prioridades de dispositivo
- Reintentos inteligentes
- Monitoreo en tiempo real

### **Fase 3: Sistema Completo**
- Dashboard web
- Alertas automáticas
- Optimización automática

## 💡 **Beneficios del Sistema**

1. **Elimina conflictos** entre dispositivos
2. **Optimiza rate limiting** de Telegram
3. **Mejora la confiabilidad** de las subidas
4. **Permite monitoreo** centralizado
5. **Escalable** para múltiples dispositivos
6. **Recuperación automática** de errores

---

**Estado:** Propuesta de diseño  
**Prioridad:** Alta  
**Complejidad:** Media  
**Tiempo estimado:** 2-3 semanas 