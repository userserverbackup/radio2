# 📱 Funcionalidad de Identificación de Dispositivo

## 🎯 **Descripción General**

Se ha implementado una funcionalidad completa para identificar y obtener información detallada del dispositivo, incluyendo dirección IP, dirección MAC, y otros datos del sistema.

## 🔧 **Características Implementadas**

### 📊 **Información del Dispositivo**
- **ID Único**: Identificador único basado en ANDROID_ID + fabricante + modelo
- **Nombre del Dispositivo**: Nombre legible del dispositivo
- **Dirección IP**: IP actual del dispositivo (WiFi o red)
- **Dirección MAC**: Dirección MAC del dispositivo
- **Versión de Android**: Versión completa del sistema operativo
- **Fabricante**: Marca del dispositivo
- **Modelo**: Modelo específico del dispositivo

### 🛠 **Clase DeviceInfo**

#### **Métodos Principales:**
```kotlin
// Obtener toda la información del dispositivo
val deviceData = DeviceInfo(context).getDeviceData()

// Obtener información específica
val ipAddress = DeviceInfo(context).getIpAddress()
val macAddress = DeviceInfo(context).getMacAddress()
val deviceId = DeviceInfo(context).getDeviceId()
val deviceName = DeviceInfo(context).getDeviceName()

// Obtener información formateada
val infoString = DeviceInfo(context).getDeviceInfoString()
val infoJson = DeviceInfo(context).getDeviceInfoJson()
```

#### **Estructura de Datos:**
```kotlin
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
```

## 📱 **Integración en la Aplicación**

### 🔄 **Base de Datos**
- **BackupFile**: Agregados campos para información del dispositivo
  - `deviceId`: ID único del dispositivo
  - `deviceName`: Nombre del dispositivo
  - `deviceIp`: Dirección IP
  - `deviceMac`: Dirección MAC
  - `deviceModel`: Modelo del dispositivo
  - `deviceManufacturer`: Fabricante
  - `androidVersion`: Versión de Android

### 📤 **Sincronización con GitHub**
- La información del dispositivo se incluye automáticamente en cada backup
- Se sincroniza con el repositorio de GitHub
- Permite identificar qué dispositivo realizó cada backup

### 🤖 **Comandos de Telegram**

#### **Comando `/dispositivo`**
Muestra información básica del dispositivo:
```
📱 Información del dispositivo:

🆔 ID: Samsung_SM-G973F_abc123def456
📱 Dispositivo: Samsung Galaxy S10
🌐 IP: 192.168.1.100
📡 MAC: AA:BB:CC:DD:EE:FF
🤖 Android: Android 12 (API 31)
🏭 Fabricante: Samsung
📋 Modelo: SM-G973F
📦 App: Radio2 v1.0.0
🕐 Última actualización: 15/12/2024 14:30:25
```

#### **Comando `/device_info`**
Muestra información detallada incluyendo JSON:
```
🔍 Información Detallada del Dispositivo:

🆔 ID Único: Samsung_SM-G973F_abc123def456
📱 Nombre: Samsung Galaxy S10
🌐 Dirección IP: 192.168.1.100
📡 Dirección MAC: AA:BB:CC:DD:EE:FF
🤖 Versión Android: Android 12 (API 31)
🏭 Fabricante: Samsung
📋 Modelo: SM-G973F
📦 Aplicación: Radio2 Backup v1.0.0
🕐 Timestamp: 1702657825000

📊 Información JSON:
{
    "deviceId": "Samsung_SM-G973F_abc123def456",
    "deviceName": "Samsung Galaxy S10",
    "ipAddress": "192.168.1.100",
    "macAddress": "AA:BB:CC:DD:EE:FF",
    "androidVersion": "Android 12 (API 31)",
    "manufacturer": "Samsung",
    "model": "SM-G973F",
    "timestamp": 1702657825000
}
```

### 📊 **Estadísticas de GitHub**
Las estadísticas de GitHub ahora incluyen información del dispositivo:
```
📊 Estadísticas de GitHub:

📁 Total de archivos: 150
💾 Tamaño total: 2.5 GB
✅ Backups exitosos: 145
❌ Backups fallidos: 5
🔄 Última sincronización: 15/12/2024 14:30:25
🌐 Repositorio: userserverbackup/radio2-backup-historial

📱 Información del Dispositivo:
🆔 ID: Samsung_SM-G973F_abc123def456
📱 Dispositivo: Samsung Galaxy S10
🌐 IP: 192.168.1.100
📡 MAC: AA:BB:CC:DD:EE:FF
🤖 Android: Android 12 (API 31)
```

## 🔍 **Funcionalidades Técnicas**

### 🌐 **Obtención de IP**
1. **Prioridad WiFi**: Intenta obtener IP de la conexión WiFi activa
2. **Interfaces de Red**: Si no hay WiFi, busca en todas las interfaces de red
3. **Filtrado**: Evita IPs loopback y link-local (169.254.x.x)
4. **Fallback**: Retorna "0.0.0.0" si no se encuentra IP

### 📡 **Obtención de MAC**
1. **Prioridad WiFi**: Intenta obtener MAC de la conexión WiFi
2. **Interfaces de Red**: Busca en todas las interfaces de red
3. **Filtrado**: Evita MACs nulas o vacías
4. **Fallback**: Retorna "00:00:00:00:00:00" si no se encuentra

### 🆔 **ID Único del Dispositivo**
- Combina: `Fabricante_Modelo_ANDROID_ID`
- Reemplaza espacios y guiones por guiones bajos
- Ejemplo: `Samsung_SM-G973F_abc123def456`

## 📁 **Archivos Modificados**

### 🔄 **Archivos Principales:**
- `DeviceInfo.kt` - Nueva clase utilitaria
- `BackupDatabase.kt` - Agregados campos de dispositivo
- `BackupRepository.kt` - Método para insertar con info de dispositivo
- `GitHubHistorialSync.kt` - Incluye info de dispositivo en JSON
- `TelegramCommandWorker.kt` - Nuevos comandos de dispositivo
- `MainActivity.kt` - Logging de información del dispositivo
- `ConfigBackupActivity.kt` - Muestra IP del dispositivo

### 📊 **Base de Datos:**
```sql
-- Campos agregados a la tabla backup_files
ALTER TABLE backup_files ADD COLUMN deviceId TEXT;
ALTER TABLE backup_files ADD COLUMN deviceName TEXT;
ALTER TABLE backup_files ADD COLUMN deviceIp TEXT;
ALTER TABLE backup_files ADD COLUMN deviceMac TEXT;
ALTER TABLE backup_files ADD COLUMN deviceModel TEXT;
ALTER TABLE backup_files ADD COLUMN deviceManufacturer TEXT;
ALTER TABLE backup_files ADD COLUMN androidVersion TEXT;
```

## 🚀 **Uso y Configuración**

### 📱 **Uso Automático**
- La información del dispositivo se captura automáticamente en cada backup
- No requiere configuración adicional
- Se incluye en todos los archivos de backup

### 🤖 **Comandos de Telegram**
```bash
# Información básica del dispositivo
/dispositivo

# Información detallada con JSON
/device_info

# Estadísticas incluyendo info del dispositivo
/github_stats
```

### 🔧 **Configuración Manual**
```kotlin
// Crear instancia de DeviceInfo
val deviceInfo = DeviceInfo(context)

// Obtener información específica
val ip = deviceInfo.getIpAddress()
val mac = deviceInfo.getMacAddress()

// Obtener toda la información
val data = deviceInfo.getDeviceData()
```

## 🔒 **Consideraciones de Privacidad**

### ✅ **Información Recopilada:**
- ID único del dispositivo (basado en ANDROID_ID)
- Información del hardware (modelo, fabricante)
- Información de red (IP, MAC)
- Versión del sistema operativo

### 🛡 **Seguridad:**
- La información se almacena localmente
- Se sincroniza solo con el repositorio configurado
- No se comparte con terceros
- Se puede deshabilitar en la configuración

## 📈 **Beneficios**

### 🔍 **Identificación:**
- Identificar qué dispositivo realizó cada backup
- Rastrear múltiples dispositivos
- Resolver conflictos de sincronización

### 📊 **Análisis:**
- Estadísticas por dispositivo
- Rendimiento por modelo
- Compatibilidad por versión de Android

### 🔧 **Mantenimiento:**
- Diagnóstico de problemas por dispositivo
- Actualizaciones específicas por modelo
- Soporte técnico personalizado

## 🎯 **Próximas Mejoras**

### 🔮 **Funcionalidades Futuras:**
- [ ] Dashboard web con información de dispositivos
- [ ] Alertas por dispositivo específico
- [ ] Configuración personalizada por dispositivo
- [ ] Estadísticas avanzadas de rendimiento
- [ ] Integración con sistemas de monitoreo

### 🔧 **Optimizaciones:**
- [ ] Cache de información del dispositivo
- [ ] Actualización automática de IP
- [ ] Detección de cambios de red
- [ ] Compresión de datos de dispositivo

---

**Versión**: 1.1  
**Fecha**: 15/12/2024  
**Autor**: Radio2 Backup Team 