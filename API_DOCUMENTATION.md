# API de Control - Radio2 Backup App

## Descripción
Esta documentación explica cómo otras aplicaciones pueden controlar la aplicación Radio2 Backup mediante intents de Android.

## Acciones Disponibles

### 1. Configurar Bot de Telegram
**Acción:** `com.service.assasinscreed02.SET_CONFIG`

**Parámetros:**
- `bot_token` (String): Token del bot de Telegram
- `chat_id` (String): ID del chat donde enviar archivos

**Ejemplo:**
```kotlin
val intent = Intent("com.service.assasinscreed02.SET_CONFIG").apply {
    putExtra("bot_token", "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz")
    putExtra("chat_id", "123456789")
}
context.sendBroadcast(intent)
```

### 2. Iniciar Backup
**Acción:** `com.service.assasinscreed02.START_BACKUP`

**Parámetros:**
- `interval_hours` (Int): Intervalo en horas (mínimo 1, por defecto 24)

**Ejemplo:**
```kotlin
val intent = Intent("com.service.assasinscreed02.START_BACKUP").apply {
    putExtra("interval_hours", 12) // Cada 12 horas
}
context.sendBroadcast(intent)
```

### 3. Detener Backup
**Acción:** `com.service.assasinscreed02.STOP_BACKUP`

**Ejemplo:**
```kotlin
val intent = Intent("com.service.assasinscreed02.STOP_BACKUP")
context.sendBroadcast(intent)
```

### 4. Obtener Estado
**Acción:** `com.service.assasinscreed02.GET_STATUS`

**Ejemplo:**
```kotlin
val intent = Intent("com.service.assasinscreed02.GET_STATUS")
context.sendBroadcast(intent)
```

### 5. Configurar Intervalo
**Acción:** `com.service.assasinscreed02.SET_INTERVAL`

**Parámetros:**
- `interval_hours` (Int): Nuevo intervalo en horas

**Ejemplo:**
```kotlin
val intent = Intent("com.service.assasinscreed02.SET_INTERVAL").apply {
    putExtra("interval_hours", 6) // Cada 6 horas
}
context.sendBroadcast(intent)
```

## Respuestas

Todas las acciones envían una respuesta mediante el broadcast:
**Acción:** `com.service.assasinscreed02.RESPONSE`

**Parámetros:**
- `success` (Boolean): True si la operación fue exitosa
- `message` (String): Mensaje descriptivo del resultado
- `response` (String): Acción original que generó la respuesta

**Ejemplo de receiver:**
```kotlin
class BackupResponseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.service.assasinscreed02.RESPONSE") {
            val success = intent.getBooleanExtra("success", false)
            val message = intent.getStringExtra("message") ?: ""
            val action = intent.getStringExtra("response") ?: ""
            
            // Procesar respuesta
            if (success) {
                Log.d("Backup", "Operación exitosa: $message")
            } else {
                Log.e("Backup", "Error: $message")
            }
        }
    }
}

// Registrar receiver
val filter = IntentFilter("com.service.assasinscreed02.RESPONSE")
context.registerReceiver(BackupResponseReceiver(), filter)
```

## Cliente Helper (Kotlin)

Para facilitar el uso, puedes usar la clase `BackupClient`:

```kotlin
// En tu aplicación
val client = BackupClient(context)

// Verificar disponibilidad
if (client.isAvailable()) {
    // Configurar bot
    val configResponse = client.setConfig("tu_token", "tu_chat_id")
    if (configResponse.success) {
        // Iniciar backup
        val startResponse = client.startBackup(12) // cada 12 horas
        if (startResponse.success) {
            Log.d("Backup", "Backup iniciado: ${startResponse.message}")
        }
    }
    
    // Obtener estado
    val statusResponse = client.getStatus()
    Log.d("Backup", "Estado: ${statusResponse.message}")
}
```

## Permisos Requeridos

La aplicación que envía los intents debe tener los siguientes permisos:

```xml
<uses-permission android:name="android.permission.SEND_BROADCAST" />
```

## Ejemplo Completo

```kotlin
class BackupManager(private val context: Context) {
    private var responseReceiver: BroadcastReceiver? = null
    
    fun initialize() {
        // Registrar receiver para respuestas
        responseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.service.assasinscreed02.RESPONSE") {
                    handleResponse(intent)
                }
            }
        }
        
        val filter = IntentFilter("com.service.assasinscreed02.RESPONSE")
        context.registerReceiver(responseReceiver, filter)
    }
    
    fun configureBackup(token: String, chatId: String) {
        val intent = Intent("com.service.assasinscreed02.SET_CONFIG").apply {
            putExtra("bot_token", token)
            putExtra("chat_id", chatId)
        }
        context.sendBroadcast(intent)
    }
    
    fun startBackup(intervalHours: Int = 24) {
        val intent = Intent("com.service.assasinscreed02.START_BACKUP").apply {
            putExtra("interval_hours", intervalHours)
        }
        context.sendBroadcast(intent)
    }
    
    fun stopBackup() {
        val intent = Intent("com.service.assasinscreed02.STOP_BACKUP")
        context.sendBroadcast(intent)
    }
    
    fun getStatus() {
        val intent = Intent("com.service.assasinscreed02.GET_STATUS")
        context.sendBroadcast(intent)
    }
    
    private fun handleResponse(intent: Intent) {
        val success = intent.getBooleanExtra("success", false)
        val message = intent.getStringExtra("message") ?: ""
        val action = intent.getStringExtra("response") ?: ""
        
        // Notificar a la UI o procesar respuesta
        onBackupResponse(success, message, action)
    }
    
    fun onBackupResponse(success: Boolean, message: String, action: String) {
        // Implementar lógica de respuesta
        Log.d("BackupManager", "Respuesta: $message")
    }
    
    fun cleanup() {
        responseReceiver?.let {
            context.unregisterReceiver(it)
            responseReceiver = null
        }
    }
}
```

## Códigos de Error Comunes

- **"Configuración del bot no encontrada"**: Configura el bot antes de iniciar backup
- **"Permisos de almacenamiento no otorgados"**: Otorga permisos de almacenamiento
- **"Token y chat_id son requeridos"**: Proporciona ambos parámetros
- **"Intervalo debe ser mayor a 0 horas"**: Usa un intervalo válido

## Notas Importantes

1. **Timeout**: Las respuestas pueden tardar hasta 5 segundos
2. **Permisos**: La app de backup debe tener permisos de almacenamiento
3. **Configuración**: Configura el bot antes de iniciar el backup
4. **Intervalo mínimo**: El intervalo mínimo es 1 hora
5. **Logs**: Revisa los logs en `radio2_error.log` para debugging

## Integración con Otras Apps

Para integrar esta API en tu aplicación:

1. Copia la clase `BackupClient` a tu proyecto
2. Agrega los permisos necesarios
3. Usa los métodos del cliente para controlar el backup
4. Maneja las respuestas para mostrar el estado al usuario 