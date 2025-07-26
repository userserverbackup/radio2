# Integración con GitHub - Radio2 Backup

## Descripción

La aplicación Radio2 Backup incluye una integración completa con GitHub para sincronizar automáticamente el historial de backups. Esta funcionalidad permite mantener un registro centralizado de todos los archivos respaldados, facilitando la gestión y recuperación de datos.

## Características Principales

### 🔄 Sincronización Automática
- **Sincronización automática**: Después de cada backup exitoso, el historial se sincroniza automáticamente con GitHub
- **Sincronización manual**: Opción para sincronizar manualmente desde la interfaz de usuario
- **Sincronización por Telegram**: Comando `/github_sync` para sincronizar desde Telegram

### 📊 Estadísticas en Tiempo Real
- Total de archivos respaldados
- Tamaño total de los backups
- Número de backups exitosos y fallidos
- Última fecha de sincronización
- Información del repositorio

### 🛡️ Gestión de Duplicados
- Verificación automática de archivos duplicados usando hashes MD5
- Prevención de subidas duplicadas
- Optimización del almacenamiento

### 🔧 Configuración Flexible
- Token de acceso personal configurable
- Repositorio personalizable
- Rama (branch) configurable
- Validación de configuración

## Configuración Inicial

### 1. Crear Repositorio en GitHub

1. Ve a [GitHub](https://github.com) y crea una nueva cuenta o inicia sesión
2. Crea un nuevo repositorio público llamado `radio2-backup-historial`
3. El repositorio debe ser público para permitir el acceso desde la aplicación

### 2. Generar Personal Access Token

1. En GitHub, ve a **Settings** > **Developer settings** > **Personal access tokens** > **Tokens (classic)**
2. Haz clic en **Generate new token (classic)**
3. Configura el token:
   - **Note**: `Radio2 Backup Sync`
   - **Expiration**: `No expiration` (o selecciona una fecha futura)
   - **Scopes**: Marca `repo` para acceso completo al repositorio
4. Haz clic en **Generate token**
5. **IMPORTANTE**: Copia el token generado (no podrás verlo de nuevo)

### 3. Configurar en la Aplicación

1. Abre la aplicación Radio2 Backup
2. Ve a **Configuración** > **GitHub**
3. Completa los campos:
   - **Token de GitHub**: Pega el token generado
   - **Usuario de GitHub**: Tu nombre de usuario de GitHub
   - **Nombre del Repositorio**: `radio2-backup-historial`
   - **Rama**: `main` (por defecto)
4. Haz clic en **Guardar Configuración**
5. Haz clic en **Probar Conexión** para verificar que todo funciona

## Uso de la Funcionalidad

### Interfaz de Usuario

#### Pantalla de Configuración de GitHub
- **Guardar Configuración**: Guarda la configuración de GitHub
- **Probar Conexión**: Verifica la conectividad con GitHub
- **Sincronizar Ahora**: Ejecuta una sincronización manual
- **Estado**: Muestra el estado actual de la configuración
- **Última Sincronización**: Fecha y hora de la última sincronización
- **Estadísticas**: Información detallada de archivos en GitHub

#### Información Mostrada
- ✅ Configuración válida
- ⚠️ Configuración incompleta
- ❌ Error de conexión
- 📁 Total de archivos respaldados
- 💾 Tamaño total de los backups

### Comandos de Telegram

#### `/github_sync`
Sincroniza manualmente el historial con GitHub.

**Respuesta:**
```
✅ Sincronización con GitHub exitosa
📁 Total de archivos: 150 (2.5 GB)
```

#### `/github_stats`
Muestra estadísticas detalladas de GitHub.

**Respuesta:**
```
📊 Estadísticas de GitHub:

📁 Total de archivos: 150
💾 Tamaño total: 2.5 GB
✅ Backups exitosos: 145
❌ Backups fallidos: 5
🔄 Última sincronización: 15/12/2024 14:30:25
🌐 Repositorio: tu-usuario/radio2-backup-historial
```

## Estructura de Datos

### Archivo de Historial (`historial_backup.json`)

El historial se almacena en GitHub como un archivo JSON con la siguiente estructura:

```json
[
  {
    "id": 1,
    "fileName": "IMG_20241215_143025.jpg",
    "filePath": "/storage/emulated/0/DCIM/Camera/IMG_20241215_143025.jpg",
    "fileHash": "a1b2c3d4e5f6...",
    "fileSize": 2048576,
    "fileType": "image",
    "uploadDate": 1702653025000,
    "uploadStatus": "success",
    "telegramMessageId": "123456789",
    "errorMessage": ""
  }
]
```

### Campos del Historial
- **id**: Identificador único del archivo
- **fileName**: Nombre del archivo
- **filePath**: Ruta completa del archivo
- **fileHash**: Hash MD5 del archivo (para detección de duplicados)
- **fileSize**: Tamaño del archivo en bytes
- **fileType**: Tipo de archivo (`image` o `video`)
- **uploadDate**: Timestamp de la subida
- **uploadStatus**: Estado de la subida (`success`, `failed`, `pending`)
- **telegramMessageId**: ID del mensaje de Telegram (si aplica)
- **errorMessage**: Mensaje de error (si aplica)

## Funcionalidades Técnicas

### Sincronización Inteligente
- **Merge automático**: Combina historial local con el de GitHub
- **Resolución de conflictos**: Los archivos locales tienen prioridad
- **Ordenamiento**: Los archivos se ordenan por fecha de subida (más recientes primero)

### Validación y Seguridad
- **Validación de configuración**: Verifica que todos los campos requeridos estén completos
- **Validación de token**: Verifica que el token tenga permisos suficientes
- **Límites de tamaño**: Verifica que el archivo no exceda los límites de GitHub (100MB)

### Manejo de Errores
- **Errores de red**: Reintentos automáticos con backoff exponencial
- **Errores de autenticación**: Mensajes claros sobre problemas de token
- **Errores de repositorio**: Validación de existencia y permisos
- **Logs detallados**: Registro completo de errores para debugging

## Límites y Consideraciones

### Límites de GitHub
- **Tamaño máximo de archivo**: 100MB por archivo
- **Límite de API**: 5000 requests por hora para usuarios autenticados
- **Repositorio público**: El repositorio debe ser público para funcionar

### Optimizaciones
- **Compresión de datos**: Los datos se comprimen antes de subir
- **Sincronización incremental**: Solo se suben cambios nuevos
- **Cache local**: Se mantiene cache de estadísticas para mejor rendimiento

## Solución de Problemas

### Error: "Configuración inválida"
- Verifica que todos los campos estén completos
- Asegúrate de que el token no esté vacío
- Verifica que el nombre de usuario y repositorio sean correctos

### Error: "Error de conexión"
- Verifica tu conexión a internet
- Asegúrate de que el token sea válido y tenga permisos de `repo`
- Verifica que el repositorio exista y sea público

### Error: "Error en la sincronización"
- Verifica que el repositorio tenga permisos de escritura
- Revisa los logs de la aplicación para más detalles
- Intenta sincronizar manualmente desde la interfaz

### El historial no se actualiza
- Verifica que el backup automático esté funcionando
- Revisa la configuración de WiFi (el backup solo funciona con WiFi)
- Verifica que la sincronización automática esté habilitada

## Logs y Debugging

### Logs de la Aplicación
Los logs se guardan en la aplicación y se pueden ver en:
- **Configuración** > **Logs**

### Logs de GitHub
Los errores de GitHub se registran con el tag `GitHubHistorialSync`:
```
D/GitHubHistorialSync: Iniciando sincronización con GitHub...
D/GitHubHistorialSync: Historial sincronizado exitosamente con GitHub
E/GitHubHistorialSync: Error obteniendo archivo de GitHub: 404 - Not Found
```

## Actualizaciones Futuras

### Funcionalidades Planificadas
- **Sincronización bidireccional**: Descargar historial desde GitHub
- **Backup de configuración**: Guardar configuración en GitHub
- **Múltiples repositorios**: Sincronizar con varios repositorios
- **Filtros avanzados**: Filtrar archivos por tipo, fecha, tamaño
- **Exportación**: Exportar historial en diferentes formatos

### Mejoras de Rendimiento
- **Sincronización diferencial**: Solo sincronizar cambios
- **Compresión mejorada**: Reducir tamaño de datos transferidos
- **Cache inteligente**: Cache más eficiente de estadísticas

## Soporte

Si tienes problemas con la funcionalidad de GitHub:

1. **Revisa esta documentación** para soluciones comunes
2. **Verifica los logs** de la aplicación para errores específicos
3. **Prueba la conexión** desde la interfaz de configuración
4. **Contacta al desarrollador** con los logs de error

---

**Nota**: Esta funcionalidad requiere una conexión a internet estable y un repositorio público en GitHub. El historial se sincroniza automáticamente después de cada backup exitoso. 