# 🔧 Configuración del Sistema de Actualización Automática

## 📋 Pasos para Configurar

### 1. **Configurar Repositorio de GitHub**

#### A. Crear Repositorio en GitHub
1. Ve a [GitHub.com](https://github.com)
2. Crea un nuevo repositorio llamado `radio2`
3. Haz el repositorio público o privado (según prefieras)

#### B. Configurar el Código
Edita el archivo `app/src/main/java/com/service/assasinscreed02/updater/data/repositories/GitHubUpdateRepository.kt`:

```kotlin
// Líneas 25-27: Cambiar estos valores
private const val REPO_OWNER = "tu-usuario" // Tu usuario de GitHub
private const val REPO_NAME = "radio2" // Tu repositorio
```

### 2. **Configurar Token de GitHub**

#### A. Generar Token de Acceso
1. Ve a GitHub.com → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click en "Generate new token (classic)"
3. Dale un nombre como "Radio2 Updates"
4. Selecciona los permisos:
   - ✅ `repo` (Full control of private repositories)
   - ✅ `public_repo` (Access public repositories)
5. Click en "Generate token"
6. **IMPORTANTE:** Copia el token generado

#### B. Configurar el Script
Edita el archivo `crear_release.py`:

```python
# Línea 12: Cambiar este valor
GITHUB_TOKEN = "ghp_tu_token_aqui"  # Pega tu token de GitHub
REPO_OWNER = "tu-usuario"  # Tu usuario de GitHub
REPO_NAME = "radio2"  # Tu repositorio
```

### 3. **Crear el Primer Release**

#### A. Compilar la App
```bash
./gradlew assembleDebug
```

#### B. Crear Release Automático
```bash
python crear_release.py 1.5.1
```

O manualmente:
1. Ve a tu repositorio en GitHub
2. Click en "Releases" → "Create a new release"
3. Tag: `v1.5.1`
4. Título: `Radio2 v1.5.1`
5. Descripción: (usa el template del script)
6. Sube el archivo `app/build/outputs/apk/debug/app-debug.apk`

### 4. **Probar el Sistema**

#### A. Instalar la App
```bash
./gradlew installDebug
```

#### B. Verificar Funcionamiento
1. Abre la app
2. Toca el botón "🔄 Verificar Actualización"
3. Debería mostrar si hay actualizaciones disponibles

## 🔄 Flujo de Actualización

### **Para el Desarrollador:**
1. Hacer cambios en el código
2. Compilar: `./gradlew assembleDebug`
3. Crear release: `python crear_release.py 1.5.2`
4. ¡Listo! Los usuarios recibirán la actualización automáticamente

### **Para el Usuario:**
1. La app verifica actualizaciones automáticamente
2. Si hay una actualización, aparece un diálogo
3. Toca "Actualizar" para descargar e instalar
4. ¡Listo! La app se actualiza automáticamente

## 📱 Funcionalidades Implementadas

### ✅ **Verificación Automática**
- Al iniciar la app
- Diariamente en segundo plano
- Manual con el botón

### ✅ **Descarga Inteligente**
- Progreso en tiempo real
- Manejo de errores de red
- Reintentos automáticos

### ✅ **Instalación Automática**
- Instalación directa desde la app
- Permisos automáticos
- Manejo de errores

### ✅ **Interfaz de Usuario**
- Diálogo informativo
- Notas de la versión
- Tamaño del archivo
- Actualizaciones obligatorias

## 🛠️ Comandos Útiles

### **Compilar y Instalar:**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### **Crear Release:**
```bash
python crear_release.py 1.5.1
python crear_release.py 1.5.2 "Nuevas características"
```

### **Ver Logs:**
```bash
adb logcat -s UpdateCheckWorker
adb logcat -s GitHubUpdateRepository
adb logcat -s UpdateActivity
```

## 🔧 Configuración Avanzada

### **Cambiar Frecuencia de Verificación**
Edita en `MainActivity.kt`:
```kotlin
// Verificación diaria (cambiar 1 por el número de días)
val periodicUpdateWorkRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
    1, TimeUnit.DAYS
)
```

### **Habilitar/Deshabilitar Actualización Automática**
La configuración se guarda automáticamente en SharedPreferences.
Los usuarios pueden deshabilitarla desde la configuración del sistema.

### **Actualizaciones Obligatorias**
En la descripción del release, incluye `[MANDATORY]` para hacer la actualización obligatoria.

## 🚨 Solución de Problemas

### **Error: "No se encontró APK"**
```bash
./gradlew clean
./gradlew assembleDebug
```

### **Error: "Token inválido"**
1. Verifica que el token tenga permisos `repo`
2. Regenera el token si es necesario
3. Actualiza el token en `crear_release.py`

### **Error: "Repositorio no encontrado"**
1. Verifica que el repositorio existe
2. Verifica que el nombre del usuario es correcto
3. Verifica que el token tiene acceso al repositorio

### **La app no verifica actualizaciones**
1. Verifica que la app tiene permisos de internet
2. Verifica que la configuración del repositorio es correcta
3. Revisa los logs: `adb logcat -s UpdateCheckWorker`

## 📊 Estado del Sistema

- ✅ **Compilación:** Exitosa
- ✅ **Instalación:** Completada
- ✅ **Funcionalidad:** Sistema activo
- ✅ **Documentación:** Completa
- ⏳ **Configuración:** Pendiente (GitHub)

## 🎯 Próximos Pasos

1. **Configurar tu repositorio de GitHub**
2. **Generar token de acceso**
3. **Crear el primer release**
4. **Probar la actualización**
5. **Crear punto de restauración**

---

**🎉 ¡El sistema de actualización automática está listo para usar!** 