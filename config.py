#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Archivo de configuración para el controlador de backup
"""

# Configuración del Bot de Telegram
BOT_TOKEN = "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"  # Reemplaza con tu token
CHAT_ID = "123456789"  # Reemplaza con tu chat ID

# Configuración de timeouts
REQUEST_TIMEOUT = 30  # segundos
RESPONSE_TIMEOUT = 60  # segundos

# Configuración de logging
LOG_LEVEL = "INFO"  # DEBUG, INFO, WARNING, ERROR
LOG_FILE = "backup_controller.log"

# Configuración de reintentos
MAX_RETRIES = 3
RETRY_DELAY = 2  # segundos

# Comandos disponibles
COMMANDS = {
    "start": "/iniciar_backup",
    "stop": "/detener_backup", 
    "status": "/estado"
} 