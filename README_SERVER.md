s# Controlador de Backup Remoto - App Server

## Descripción
Controlador Python estable y compatible para manejar la aplicación de backup Android desde un servidor remoto vía Telegram.

## Características
- ✅ **Estable**: Manejo robusto de errores y timeouts
- ✅ **Compatible**: Python 3.6+ en cualquier sistema
- ✅ **Seguro**: Solo responde a chat ID autorizado
- ✅ **Logging**: Registro completo de operaciones
- ✅ **Fácil uso**: Configuración automática

## Instalación

### 1. Requisitos
- Python 3.6 o superior
- Conexión a Internet
- Bot de Telegram configurado

### 2. Instalación automática
```bash
# Clonar o descargar los archivos
git clone <tu-repositorio>
cd backup-controller

# Ejecutar configuración automática
python setup.py
```

### 3. Instalación manual
```bash
# Instalar dependencias
pip install -r requirements.txt

# Configurar manualmente
# Editar config.py con tu BOT_TOKEN y CHAT_ID
```

## Configuración

### 1. Crear Bot de Telegram
1. Busca `@BotFather` en Telegram
2. Envía `/newbot`
3. Sigue las instrucciones
4. Guarda el token

### 2. Obtener Chat ID
1. Envía un mensaje al bot desde tu app server
2. Ejecuta `python setup.py` para obtener el chat ID automáticamente
3. O visita: `https://api.telegram.org/bot<TU_TOKEN>/getUpdates`

### 3. Configurar archivo
Edita `config.py`:
```python
BOT_TOKEN = "tu_token_aqui"
CHAT_ID = "tu_chat_id_aqui"
```

## Uso

### Uso básico
```python
from backup_controller import TelegramBackupController
from config import BOT_TOKEN, CHAT_ID

# Crear controlador
controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)

# Probar conexión
if controller.test_connection():
    # Iniciar backup
    controller.start_backup()
    
    # Obtener estado
    controller.get_status()
    
    # Detener backup
    controller.stop_backup()
```

### Ejemplo completo
```python
#!/usr/bin/env python3
import time
from backup_controller import TelegramBackupController
from config import BOT_TOKEN, CHAT_ID

def main():
    # Crear controlador
    controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)
    
    # Verificar conexión
    if not controller.test_connection():
        print("❌ No se pudo conectar")
        return
    
    print("✅ Conectado al bot")
    
    # Obtener estado inicial
    print("\n📊 Estado inicial:")
    controller.get_status()
    
    # Esperar respuesta
    response = controller.wait_for_response(10)
    if response:
        print(f"Estado: {response}")
    
    # Iniciar backup cada 6 horas
    print("\n🚀 Iniciando backup...")
    if controller.start_backup():
        print("Comando enviado")
        
        # Esperar confirmación
        response = controller.wait_for_response(10)
        if response:
            print(f"Confirmación: {response}")
    
    # Monitorear por 1 minuto
    print("\n⏳ Monitoreando...")
    for i in range(6):
        time.sleep(10)
        controller.get_status()
        response = controller.wait_for_response(5)
        if response:
            print(f"Estado: {response}")

if __name__ == "__main__":
    main()
```

### Uso desde línea de comandos
```bash
# Ejecutar ejemplo básico
python backup_controller.py

# Ejecutar con configuración personalizada
python -c "
from backup_controller import TelegramBackupController
controller = TelegramBackupController('tu_token', 'tu_chat_id')
controller.start_backup()
"
```

## API de Métodos

### TelegramBackupController

#### Constructor
```python
TelegramBackupController(bot_token, chat_id, timeout=30)
```

#### Métodos principales
- `test_connection()` → `bool`: Probar conexión con el bot
- `start_backup()` → `bool`: Iniciar backup automático
- `stop_backup()` → `bool`: Detener backup automático
- `get_status()` → `bool`: Obtener estado del backup
- `send_command(command)` → `bool`: Enviar comando personalizado
- `wait_for_response(timeout=60)` → `str|None`: Esperar respuesta

#### Métodos auxiliares
- `get_updates()` → `dict|None`: Obtener updates del bot
- `_make_request(method, endpoint, data)` → `dict|None`: Request HTTP

## Comandos Disponibles

| Comando | Descripción |
|---------|-------------|
| `/iniciar_backup` | Inicia el backup automático |
| `/detener_backup` | Detiene el backup automático |
| `/estado` | Obtiene el estado actual |

## Logging

El controlador genera logs automáticamente:
- **Archivo**: `backup_controller.log`
- **Consola**: Salida en tiempo real
- **Nivel**: INFO por defecto

### Ejemplo de log
```
2024-01-15 10:30:15 - INFO - Controlador de backup inicializado
2024-01-15 10:30:16 - INFO - ✅ Conexión exitosa con bot: BackupBot
2024-01-15 10:30:17 - INFO - 🚀 Iniciando backup...
2024-01-15 10:30:18 - INFO - ✅ Comando enviado: /iniciar_backup
2024-01-15 10:30:20 - INFO - 📨 Respuesta recibida: ✅ Backup iniciado en el dispositivo.
```

## Manejo de Errores

El controlador maneja automáticamente:
- ✅ Timeouts de conexión
- ✅ Errores de red
- ✅ Respuestas inválidas
- ✅ Tokens incorrectos
- ✅ Chat IDs no autorizados

### Códigos de error comunes
- **"Timeout en request"**: Problema de conexión
- **"Error conectando con el bot"**: Token inválido
- **"Error enviando comando"**: Chat ID incorrecto

## Integración con Otros Sistemas

### Como módulo
```python
# En tu aplicación principal
from backup_controller import TelegramBackupController
from config import BOT_TOKEN, CHAT_ID

class BackupManager:
    def __init__(self):
        self.controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)
    
    def schedule_backup(self, interval_hours=24):
        """Programar backup automático"""
        return self.controller.start_backup()
    
    def emergency_stop(self):
        """Detener backup de emergencia"""
        return self.controller.stop_backup()
    
    def get_backup_status(self):
        """Obtener estado del backup"""
        return self.controller.get_status()
```

### Como servicio
```python
import schedule
import time
from backup_controller import TelegramBackupController

def backup_job():
    controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)
    controller.get_status()

# Programar verificación cada hora
schedule.every().hour.do(backup_job)

while True:
    schedule.run_pending()
    time.sleep(60)
```

## Seguridad

### Verificación de autorización
- Solo responde a comandos del chat ID configurado
- Valida token del bot antes de cada operación
- Timeouts para evitar bloqueos

### Recomendaciones
- ✅ Usa tokens únicos para cada bot
- ✅ No compartas el chat ID
- ✅ Monitorea los logs regularmente
- ✅ Usa HTTPS en producción

## Troubleshooting

### Problemas comunes

**1. "Error conectando con el bot"**
- Verifica el token del bot
- Comprueba conexión a Internet
- Revisa que el bot esté activo

**2. "Error enviando comando"**
- Verifica el chat ID
- Asegúrate de haber enviado un mensaje al bot
- Comprueba permisos del bot

**3. "Timeout esperando respuesta"**
- La app Android puede estar apagada
- Verifica que la app tenga permisos
- Revisa logs de la app Android

### Debug
```python
import logging
logging.getLogger().setLevel(logging.DEBUG)

controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)
controller.test_connection()
```

## Soporte

Para reportar problemas:
1. Revisa los logs en `backup_controller.log`
2. Verifica la configuración en `config.py`
3. Prueba la conexión con `controller.test_connection()`

## Licencia

Este código es para uso personal. Úsalo responsablemente. 