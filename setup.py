#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script de instalación y configuración del controlador de backup
"""

import os
import sys
import subprocess
import requests
import json

def check_python_version():
    """Verificar versión de Python"""
    if sys.version_info < (3, 6):
        print("❌ Se requiere Python 3.6 o superior")
        print(f"   Versión actual: {sys.version}")
        return False
    print(f"✅ Python {sys.version.split()[0]} detectado")
    return True

def install_dependencies():
    """Instalar dependencias"""
    print("\n📦 Instalando dependencias...")
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])
        print("✅ Dependencias instaladas correctamente")
        return True
    except subprocess.CalledProcessError:
        print("❌ Error instalando dependencias")
        return False

def get_bot_info(bot_token):
    """Obtener información del bot"""
    try:
        url = f"https://api.telegram.org/bot{bot_token}/getMe"
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            return response.json()["result"]
        else:
            return None
    except:
        return None

def get_chat_id(bot_token):
    """Obtener chat ID interactivamente"""
    print("\n📱 Para obtener tu chat ID:")
    print("1. Envía un mensaje al bot desde tu app server")
    print("2. Presiona Enter para continuar...")
    input()
    
    try:
        url = f"https://api.telegram.org/bot{bot_token}/getUpdates"
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            data = response.json()
            if data.get("ok") and data["result"]:
                # Obtener el último mensaje
                last_update = data["result"][-1]
                chat_id = last_update.get("message", {}).get("chat", {}).get("id")
                if chat_id:
                    return str(chat_id)
        
        print("❌ No se pudo obtener el chat ID")
        print("   Asegúrate de haber enviado un mensaje al bot")
        return None
    except Exception as e:
        print(f"❌ Error obteniendo chat ID: {e}")
        return None

def update_config(bot_token, chat_id):
    """Actualizar archivo de configuración"""
    try:
        with open("config.py", "r", encoding="utf-8") as f:
            content = f.read()
        
        # Reemplazar valores
        content = content.replace('BOT_TOKEN = "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"', f'BOT_TOKEN = "{bot_token}"')
        content = content.replace('CHAT_ID = "123456789"', f'CHAT_ID = "{chat_id}"')
        
        with open("config.py", "w", encoding="utf-8") as f:
            f.write(content)
        
        print("✅ Configuración actualizada")
        return True
    except Exception as e:
        print(f"❌ Error actualizando configuración: {e}")
        return False

def test_connection(bot_token, chat_id):
    """Probar conexión"""
    print("\n🔍 Probando conexión...")
    try:
        # Probar bot
        bot_info = get_bot_info(bot_token)
        if bot_info:
            print(f"✅ Bot conectado: {bot_info.get('first_name', 'N/A')}")
            print(f"   Username: @{bot_info.get('username', 'N/A')}")
        else:
            print("❌ Error conectando con el bot")
            return False
        
        # Probar envío de mensaje
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        data = {"chat_id": chat_id, "text": "🔧 Test de conexión"}
        response = requests.post(url, json=data, timeout=10)
        
        if response.status_code == 200:
            print("✅ Mensaje de prueba enviado correctamente")
            return True
        else:
            print("❌ Error enviando mensaje de prueba")
            return False
            
    except Exception as e:
        print(f"❌ Error en prueba de conexión: {e}")
        return False

def main():
    """Función principal"""
    print("🤖 Configuración del Controlador de Backup")
    print("=" * 50)
    
    # Verificar Python
    if not check_python_version():
        return
    
    # Instalar dependencias
    if not install_dependencies():
        return
    
    # Configurar bot
    print("\n🔧 Configuración del Bot")
    print("-" * 30)
    
    bot_token = input("Ingresa el token de tu bot: ").strip()
    if not bot_token:
        print("❌ Token requerido")
        return
    
    # Verificar bot
    bot_info = get_bot_info(bot_token)
    if not bot_info:
        print("❌ Token inválido o error de conexión")
        return
    
    print(f"✅ Bot verificado: {bot_info.get('first_name', 'N/A')}")
    
    # Obtener chat ID
    chat_id = get_chat_id(bot_token)
    if not chat_id:
        return
    
    print(f"✅ Chat ID obtenido: {chat_id}")
    
    # Actualizar configuración
    if not update_config(bot_token, chat_id):
        return
    
    # Probar conexión
    if not test_connection(bot_token, chat_id):
        print("❌ La configuración no funciona correctamente")
        return
    
    print("\n🎉 Configuración completada exitosamente!")
    print("\n📋 Para usar el controlador:")
    print("   python backup_controller.py")
    print("\n📋 Para usar en tu código:")
    print("   from backup_controller import TelegramBackupController")
    print("   controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)")

if __name__ == "__main__":
    main() 