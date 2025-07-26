# 📁 Sistema de Agrupación de Temas en Telegram

## 🎯 **Descripción General**

Este sistema permite organizar automáticamente los archivos de backup en temas (topics) específicos de Telegram, basándose en la ruta de origen del archivo. Los archivos se envían a temas separados según su ubicación en el dispositivo.

## 🔧 **Componentes del Sistema**

### **1. Función de Clasificación de Archivos**
- **Archivo:** `BackupUtils.kt`
- **Función:** `getTelegramTopicName(filePath: String): String`
- **Propósito:** Determina a qué tema debe ir cada archivo basándose en su ruta

### **2. Sistema de Configuración de IDs**
- **Archivo:** `TelegramCommandWorker.kt`
- **Funciones:** 
  - `mostrarConfiguracionTemas()`
  - `configurarTopicId()`
  - `encontrarTopicIds()`
- **Propósito:** Permite configurar los IDs reales de los temas de Telegram

### **3. Función de Envío con Agrupación**
- **Archivo:** `BackupUtils.kt`
- **Función:** `enviarArchivoATelegram()`
- **Propósito:** Envía archivos a temas específicos usando `message_thread_id`

## 📋 **Mapeo de Rutas a Temas**

### **Archivos de DCIM:**
```
/storage/emulated/0/DCIM/Camera/* → 📸 DCIM - Camera
/storage/emulated/0/DCIM/Screenshots/* → 📸 DCIM - Screenshots
/storage/emulated/0/DCIM/WhatsApp/* → 📸 DCIM - WhatsApp
/storage/emulated/0/DCIM/Telegram/* → 📸 DCIM - Telegram
/storage/emulated/0/DCIM/Instagram/* → 📸 DCIM - Instagram
/storage/emulated/0/DCIM/Downloads/* → 📸 DCIM - Downloads
/storage/emulated/0/DCIM/Other/* → 📸 DCIM - Other
```

### **Archivos de Media:**
```
/storage/emulated/0/Pictures/* → 📸 Pictures
/storage/emulated/0/Movies/* → 🎥 Movies
/storage/emulated/0/Videos/* → 🎥 Videos
/storage/emulated/0/Music/* → 🎵 Music
/storage/emulated/0/Ringtones/* → 🎵 Ringtones
/storage/emulated/0/Notifications/* → 🎵 Notifications
/storage/emulated/0/Alarms/* → 🎵 Alarms
```

### **Archivos de Documentos:**
```
/storage/emulated/0/Documents/* → 📄 Documents
/storage/emulated/0/Downloads/* → 📄 Downloads
*.apk, *.aab → 📱 Apps
Otros archivos → 📁 Other
```

### **Archivos de Prueba:**
```
/cache/test_camera.jpg → 📸 DCIM - Camera
/cache/test_screenshots.png → 📸 DCIM - Screenshots
/cache/test_whatsapp.jpg → 📸 DCIM - WhatsApp
/cache/test_music.mp3 → 🎵 Music
/cache/test_documents.pdf → 📄 Documents
```

## 🛠️ **Comandos Disponibles**

### **1. Configuración de Temas**
```
/configurar_temas
```
- Muestra el estado actual de configuración de todos los temas
- Indica qué temas están configurados y cuáles faltan

### **2. Configurar ID de Tema**
```
/set_topic [nombre] [id]
```
**Ejemplos:**
```
/set_topic camera 5
/set_topic screenshots 12
/set_topic whatsapp 8
/set_topic music 11
/set_topic documents 15
```

### **3. Ayuda para Encontrar IDs**
```
/encontrar_ids
```
- Proporciona instrucciones detalladas sobre cómo encontrar los IDs de los temas
- Incluye métodos de prueba y error
- Explica la creación secuencial de temas

### **4. Prueba de Agrupación**
```
/probar_agrupacion
```
- Envía archivos de prueba a diferentes temas
- Verifica que la agrupación funcione correctamente
- Muestra estadísticas de envío

## 🔍 **Cómo Encontrar los IDs de los Temas**

### **Método 1: Prueba y Error**
1. Crea un tema en tu grupo de Telegram
2. Usa `/set_topic camera 1`
3. Usa `/probar_agrupacion`
4. Si funciona, el ID es 1
5. Si no funciona, prueba con 2, 3, etc.

### **Método 2: Creación Secuencial**
1. Elimina todos los temas existentes
2. Crea los temas en este orden exacto:
   - DCIM - Camera
   - DCIM - Screenshots
   - DCIM - WhatsApp
   - DCIM - Telegram
   - DCIM - Instagram
   - DCIM - Downloads
   - DCIM - Other
   - Pictures
   - Movies
   - Videos
   - Music
   - Ringtones
   - Notifications
   - Alarms
   - Documents
   - Downloads
   - Apps
   - Other

3. Los IDs serán: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18

### **Método 3: Comando Automático**
Usa `/set_topic [nombre] [número]` con diferentes números:
```
/set_topic camera 5
/set_topic camera 10
/set_topic camera 15
```
Luego usa `/probar_agrupacion` para verificar.

## 💾 **Almacenamiento de Configuración**

Los IDs de los temas se almacenan en `SharedPreferences` con las siguientes claves:

```kotlin
"topic_camera" → 📸 DCIM - Camera
"topic_screenshots" → 📸 DCIM - Screenshots
"topic_whatsapp" → 📸 DCIM - WhatsApp
"topic_telegram" → 📸 DCIM - Telegram
"topic_instagram" → 📸 DCIM - Instagram
"topic_downloads" → 📸 DCIM - Downloads
"topic_other" → 📸 DCIM - Other
"topic_pictures" → 📸 Pictures
"topic_movies" → 🎥 Movies
"topic_videos" → 🎥 Videos
"topic_music" → 🎵 Music
"topic_ringtones" → 🎵 Ringtones
"topic_notifications" → 🎵 Notifications
"topic_alarms" → 🎵 Alarms
"topic_documents" → 📄 Documents
"topic_downloads_docs" → 📄 Downloads
"topic_apps" → 📱 Apps
"topic_other_files" → 📁 Other
```

## 🔧 **Implementación Técnica**

### **Flujo de Envío de Archivos:**
1. Se determina el tema basándose en la ruta del archivo
2. Se obtiene el ID del tema desde SharedPreferences
3. Se incluye `message_thread_id` en la solicitud de Telegram
4. El archivo se envía al tema correspondiente

### **Código Clave:**
```kotlin
// Determinar tema
val telegramTopic = getTelegramTopicName(archivo.absolutePath)

// Obtener ID del tema
val topicId = getTopicIdForFolder(telegramTopic, token, chatId, context)

// Incluir en solicitud
if (topicId != null) {
    requestBodyBuilder.addFormDataPart("message_thread_id", topicId.toString())
}
```

## ⚠️ **Problemas Comunes y Soluciones**

### **Error: "message thread not found"**
- **Causa:** El ID del tema no coincide con el ID real en Telegram
- **Solución:** Usar `/encontrar_ids` y configurar los IDs correctos

### **Todos los archivos van al tema "Other"**
- **Causa:** Los IDs no están configurados o son incorrectos
- **Solución:** Configurar los IDs usando `/set_topic`

### **Archivos de prueba no se agrupan**
- **Causa:** La función `getTelegramTopicName()` no detecta archivos de prueba
- **Solución:** Verificar que los archivos estén en `/cache/` y empiecen con `test_`

## 📊 **Estado Actual de la Implementación**

### **✅ Funcionalidades Completadas:**
- Sistema de clasificación de archivos por ruta
- Configuración manual de IDs de temas
- Comandos de administración
- Envío de archivos a temas específicos
- Manejo de archivos de prueba
- Almacenamiento persistente de configuración

### **🔄 Funcionalidades en Desarrollo:**
- Detección automática de IDs de temas
- Creación automática de temas
- Sincronización con GitHub

### **📋 Próximas Mejoras:**
- Interfaz gráfica para configuración
- Backup de configuración de temas
- Estadísticas de uso por tema
- Notificaciones de archivos enviados

## 🎯 **Resultado Esperado**

Con el sistema correctamente configurado, los archivos se organizarán automáticamente en Telegram de la siguiente manera:

```
📸 DCIM - Camera
├── IMG_20250725_120000.jpg
├── IMG_20250725_120100.jpg
└── ...

📸 DCIM - Screenshots
├── Screenshot_20250725_120000.png
├── Screenshot_20250725_120100.png
└── ...

🎵 Music
├── song1.mp3
├── song2.mp3
└── ...

📄 Documents
├── document1.pdf
├── document2.docx
└── ...
```

## 📝 **Notas de Mantenimiento**

- Los IDs de los temas deben reconfigurarse si se eliminan y recrean los temas
- La configuración se mantiene entre reinicios de la aplicación
- Los archivos de prueba se eliminan automáticamente después del envío
- El sistema es compatible con grupos de Telegram que tengan temas habilitados

---

**Fecha de última actualización:** 26/07/2025  
**Versión del sistema:** 1.0  
**Estado:** Funcional y en producción 