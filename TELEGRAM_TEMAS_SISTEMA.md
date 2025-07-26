# 📁 Sistema de Temas de Telegram para Organización de Archivos

## 🎯 **Descripción General**

Se ha implementado un sistema de organización automática de archivos usando los **temas (topics)** de Telegram. Los archivos se organizan automáticamente según su carpeta de origen en el dispositivo.

## 🔧 **Cómo Funciona**

### 📱 **En el Dispositivo Android:**
- Los archivos se detectan según su ubicación en el sistema
- Se determina automáticamente el tema correspondiente
- Se envía a Telegram con el caption del tema correcto

### 📱 **En Telegram:**
- Los temas se crean como "topics" en el grupo/canal
- Cada archivo se envía con el caption del tema correspondiente
- Los archivos se agrupan automáticamente por tema

## 📂 **Estructura de Temas Implementada**

```
📱 Radio2 Backup (Grupo/Canal)
├── 📸 DCIM
│   ├── 📸 DCIM/Camera
│   ├── 📸 DCIM/Screenshots
│   ├── 📸 DCIM/WhatsApp
│   ├── 📸 DCIM/Telegram
│   ├── 📸 DCIM/Instagram
│   ├── 📸 DCIM/Downloads
│   └── 📸 DCIM/Other
├── 📸 Pictures
├── 🎥 Movies
├── 🎥 Videos
├── 🎵 Music
├── 🎵 Ringtones
├── 🎵 Notifications
├── 🎵 Alarms
├── 📄 Documents
├── 📄 Downloads
├── 📱 Apps
└── 📁 Other
```

## 🎮 **Comandos de Telegram**

### 📁 **Crear Estructura de Temas**
```
/crear_carpetas
```
- Crea todos los temas necesarios en Telegram
- Envía archivos temporales para crear cada tema
- Muestra estadísticas de temas creados

### 📊 **Otros Comandos Disponibles**
- `/ayuda` - Lista de comandos
- `/github_sync` - Sincronizar con GitHub
- `/github_stats` - Estadísticas de GitHub
- `/device_info` - Información del dispositivo

## 🔍 **Lógica de Organización**

### 📸 **Fotos y Videos (DCIM)**
```kotlin
when {
    path.contains("/dcim/camera/") -> "📸 DCIM/Camera"
    path.contains("/dcim/screenshots/") -> "📸 DCIM/Screenshots"
    path.contains("/dcim/whatsapp/") -> "📸 DCIM/WhatsApp"
    path.contains("/dcim/telegram/") -> "📸 DCIM/Telegram"
    path.contains("/dcim/instagram/") -> "📸 DCIM/Instagram"
    path.contains("/dcim/downloads/") -> "📸 DCIM/Downloads"
    path.contains("/dcim/") -> "📸 DCIM/Other"
}
```

### 🎵 **Audio**
```kotlin
when {
    path.contains("/music/") -> "🎵 Music"
    path.contains("/ringtones/") -> "🎵 Ringtones"
    path.contains("/notifications/") -> "🎵 Notifications"
    path.contains("/alarms/") -> "🎵 Alarms"
}
```

### 📄 **Documentos**
```kotlin
when {
    path.contains("/documents/") -> "📄 Documents"
    path.contains("/downloads/") -> "📄 Downloads"
}
```

### 📱 **Aplicaciones**
```kotlin
when {
    path.contains(".apk") -> "📱 Apps"
    path.contains(".aab") -> "📱 Apps"
}
```

## 📋 **Información del Caption**

Cada archivo enviado incluye:

```
📁 <b>📸 DCIM/Camera</b>
📄 <b>Archivo:</b> IMG_20241225_123456.jpg
💾 <b>Tamaño:</b> 2.5 MB
📅 <b>Fecha:</b> 25/12/2024 12:34:56
📱 <b>Dispositivo:</b> Samsung Galaxy S21
📍 <b>Origen:</b> /storage/emulated/0/DCIM/Camera/IMG_20241225_123456.jpg
```

## ⚙️ **Configuración Requerida**

### 🔐 **Permisos del Bot**
- El bot debe ser **administrador** del grupo/canal
- Debe tener permisos para:
  - Enviar mensajes
  - Enviar archivos
  - Crear temas (topics)

### 📱 **Configuración del Grupo/Canal**
- El grupo debe tener **temas habilitados**
- El bot debe tener permisos de administrador
- Configurar el chat_id correcto en la aplicación

## 🚀 **Uso**

### 1️⃣ **Crear Temas**
```
/crear_carpetas
```

### 2️⃣ **Backup Automático**
- Los archivos se organizan automáticamente
- Se envían al tema correspondiente
- Se incluye información detallada

### 3️⃣ **Backup Manual**
- Seleccionar archivos específicos
- Se organizan según su ubicación
- Se envían al tema correcto

## 🔧 **Archivos Modificados**

### 📝 **BackupUtils.kt**
- `getTelegramFolder()` - Determina el tema según la ruta
- `enviarArchivoATelegram()` - Incluye caption con tema
- Caption mejorado con información del archivo

### 📱 **TelegramCommandWorker.kt**
- `/crear_carpetas` - Comando para crear temas
- `crearEstructuraCarpetas()` - Función de creación
- Ayuda actualizada

## 📊 **Ventajas del Sistema**

### ✅ **Organización Automática**
- No requiere intervención manual
- Archivos organizados por tipo y ubicación
- Fácil navegación en Telegram

### ✅ **Información Detallada**
- Caption con metadata completa
- Información del dispositivo
- Ruta de origen del archivo

### ✅ **Escalabilidad**
- Fácil agregar nuevos temas
- Lógica extensible
- Compatible con diferentes tipos de archivo

## 🔮 **Próximas Mejoras**

### 📈 **Funcionalidades Futuras**
- [ ] Temas personalizados por usuario
- [ ] Filtros por fecha de archivo
- [ ] Estadísticas por tema
- [ ] Búsqueda dentro de temas
- [ ] Backup selectivo por tema

### 🎨 **Mejoras de UI**
- [ ] Emojis personalizados por tema
- [ ] Colores de tema
- [ ] Íconos específicos por tipo de archivo

## 📞 **Soporte**

Para problemas o consultas:
1. Verificar permisos del bot
2. Confirmar que el grupo tiene temas habilitados
3. Revisar logs de la aplicación
4. Usar `/ayuda` para comandos disponibles

---

**Desarrollado para Radio2 Backup v1.0.0**
*Sistema de organización automática de archivos usando temas de Telegram* 