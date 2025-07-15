# Radio2 - Sistema de Backup Automático

## Descripción
Aplicación Android para backup automático de archivos multimedia a Telegram. Diseñada para uso personal.

## Funcionalidades
- ✅ Backup automático de fotos y videos
- ✅ Control remoto vía comandos de Telegram
- ✅ Configuración de intervalos personalizables
- ✅ Ejecución en segundo plano
- ✅ Reinicio automático después del boot del sistema

## Configuración

### 1. Crear Bot de Telegram
1. Busca `@BotFather` en Telegram
2. Envía `/newbot`
3. Sigue las instrucciones para crear tu bot
4. Guarda el token que te proporciona

### 2. Obtener Chat ID
1. Envía un mensaje a tu bot
2. Visita: `https://api.telegram.org/bot<TU_TOKEN>/getUpdates`
3. Busca el `chat_id` en la respuesta JSON

### 3. Configurar la Aplicación
1. Abre la app
2. Ve a "Configurar Bot" e ingresa:
   - Token del bot
   - Chat ID
3. Ve a "Configurar Backup" y establece el intervalo (en horas)

## Comandos de Telegram
- `/iniciar_backup` - Inicia el backup automático
- `/detener_backup` - Detiene el backup automático
- `/estado` - Muestra el estado actual del servicio

## Permisos Requeridos
- **Almacenamiento**: Para acceder a fotos y videos
- **Internet**: Para enviar archivos a Telegram
- **Ejecutar al inicio**: Para reiniciar automáticamente

## Solución de Problemas

### Error: "Configuración del bot no encontrada"
- Verifica que hayas configurado correctamente el token y chat ID
- Asegúrate de que el bot esté activo

### Error: "Permisos de almacenamiento no otorgados"
- Ve a Configuración > Aplicaciones > Radio2 > Permisos
- Otorga permisos de almacenamiento

### Error: "BackgroundServiceStartNotAllowedException"
- Ve a Configuración > Batería > Optimización de batería
- Excluye la aplicación de la optimización

### Los archivos no se envían
- Verifica tu conexión a internet
- Asegúrate de que el bot tenga permisos para enviar archivos
- Revisa los logs en el archivo `radio2_error.log`

## Archivos de Log
- `radio2_error.log`: Errores y eventos importantes
- Ubicación: Almacenamiento interno del dispositivo

## Notas Técnicas
- La app escanea archivos con extensiones: jpg, jpeg, png, mp4, mov, avi
- Máximo 10 archivos por ejecución para evitar sobrecarga
- Intervalo mínimo recomendado: 1 hora
- Los workers se reinician automáticamente en caso de error

## Seguridad
⚠️ **ADVERTENCIA**: Esta aplicación accede a todos los archivos multimedia del dispositivo. Úsala solo en dispositivos de tu propiedad y con fines legítimos.

## Soporte
Para reportar problemas o solicitar ayuda, revisa los logs de error en el archivo `radio2_error.log`. 