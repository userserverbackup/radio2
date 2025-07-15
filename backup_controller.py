#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Controlador de Backup Remoto vía Telegram
Compatible con Python 3.6+
"""

import requests
import time
import json
import logging
from datetime import datetime
from typing import Optional, Dict, Any

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('backup_controller.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class TelegramBackupController:
    """
    Controlador para manejar la app de backup vía Telegram
    """
    
    def __init__(self, bot_token: str, chat_id: str, timeout: int = 30):
        """
        Inicializar el controlador
        
        Args:
            bot_token: Token del bot de Telegram
            chat_id: ID del chat autorizado
            timeout: Timeout para requests en segundos
        """
        self.bot_token = bot_token
        self.chat_id = chat_id
        self.timeout = timeout
        self.base_url = f"https://api.telegram.org/bot{bot_token}"
        self.session = requests.Session()
        
        # Configurar session
        self.session.headers.update({
            'User-Agent': 'BackupController/1.0',
            'Content-Type': 'application/json'
        })
        
        logger.info("Controlador de backup inicializado")
    
    def _make_request(self, method: str, endpoint: str, data: Optional[Dict] = None) -> Optional[Dict]:
        """
        Realizar request HTTP con manejo de errores
        
        Args:
            method: Método HTTP (GET, POST)
            endpoint: Endpoint de la API
            data: Datos para enviar (solo POST)
            
        Returns:
            Respuesta JSON o None si hay error
        """
        url = f"{self.base_url}/{endpoint}"
        
        try:
            if method.upper() == "GET":
                response = self.session.get(url, timeout=self.timeout)
            else:
                response = self.session.post(url, json=data, timeout=self.timeout)
            
            response.raise_for_status()
            return response.json()
            
        except requests.exceptions.Timeout:
            logger.error(f"Timeout en request a {endpoint}")
            return None
        except requests.exceptions.ConnectionError:
            logger.error(f"Error de conexión a {endpoint}")
            return None
        except requests.exceptions.RequestException as e:
            logger.error(f"Error en request a {endpoint}: {e}")
            return None
        except json.JSONDecodeError:
            logger.error(f"Error decodificando JSON de {endpoint}")
            return None
    
    def send_command(self, command: str) -> bool:
        """
        Enviar comando al bot de Telegram
        
        Args:
            command: Comando a enviar
            
        Returns:
            True si se envió correctamente, False en caso contrario
        """
        data = {
            "chat_id": self.chat_id,
            "text": command
        }
        
        result = self._make_request("POST", "sendMessage", data)
        
        if result and result.get("ok"):
            logger.info(f"✅ Comando enviado: {command}")
            return True
        else:
            logger.error(f"❌ Error enviando comando: {command}")
            if result:
                logger.error(f"Respuesta: {result}")
            return False
    
    def start_backup(self) -> bool:
        """
        Iniciar backup automático
        
        Returns:
            True si se envió correctamente
        """
        logger.info("🚀 Iniciando backup...")
        return self.send_command("/iniciar_backup")
    
    def stop_backup(self) -> bool:
        """
        Detener backup automático
        
        Returns:
            True si se envió correctamente
        """
        logger.info("🛑 Deteniendo backup...")
        return self.send_command("/detener_backup")
    
    def get_status(self) -> bool:
        """
        Obtener estado del backup
        
        Returns:
            True si se envió correctamente
        """
        logger.info("📊 Obteniendo estado...")
        return self.send_command("/estado")
    
    def get_updates(self) -> Optional[Dict]:
        """
        Obtener últimas actualizaciones del bot
        
        Returns:
            Diccionario con updates o None si hay error
        """
        return self._make_request("GET", "getUpdates")
    
    def test_connection(self) -> bool:
        """
        Probar conexión con el bot
        
        Returns:
            True si la conexión es exitosa
        """
        result = self._make_request("GET", "getMe")
        
        if result and result.get("ok"):
            bot_info = result["result"]
            logger.info(f"✅ Conexión exitosa con bot: {bot_info.get('first_name', 'N/A')}")
            return True
        else:
            logger.error("❌ Error conectando con el bot")
            return False
    
    def wait_for_response(self, timeout: int = 60) -> Optional[str]:
        """
        Esperar respuesta del bot (útil para comandos que requieren respuesta)
        
        Args:
            timeout: Tiempo máximo de espera en segundos
            
        Returns:
            Último mensaje recibido o None si no hay respuesta
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            updates = self.get_updates()
            
            if updates and updates.get("ok") and updates["result"]:
                # Obtener el último mensaje
                last_update = updates["result"][-1]
                message = last_update.get("message", {})
                
                if message.get("chat", {}).get("id") == self.chat_id:
                    text = message.get("text", "")
                    if text and not text.startswith("/"):
                        logger.info(f"📨 Respuesta recibida: {text}")
                        return text
            
            time.sleep(2)  # Esperar 2 segundos antes de verificar nuevamente
        
        logger.warning(f"⏰ Timeout esperando respuesta ({timeout}s)")
        return None

def main():
    """
    Función principal con ejemplo de uso
    """
    print("🤖 Controlador de Backup Remoto")
    print("=" * 40)
    
    # Configuración - CAMBIA ESTOS VALORES
    BOT_TOKEN = "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"  # Tu token del bot
    CHAT_ID = "123456789"  # Chat ID de tu app server
    
    # Crear controlador
    controller = TelegramBackupController(BOT_TOKEN, CHAT_ID)
    
    # Probar conexión
    if not controller.test_connection():
        print("❌ No se pudo conectar con el bot. Verifica el token.")
        return
    
    print("\n✅ Conexión establecida")
    
    # Ejemplo de uso
    try:
        # Obtener estado inicial
        print("\n📊 Estado inicial:")
        controller.get_status()
        
        # Esperar respuesta
        response = controller.wait_for_response(10)
        if response:
            print(f"Estado: {response}")
        
        # Iniciar backup
        print("\n🚀 Iniciando backup...")
        if controller.start_backup():
            print("Comando de inicio enviado")
            
            # Esperar confirmación
            response = controller.wait_for_response(10)
            if response:
                print(f"Confirmación: {response}")
        
        # Esperar un poco
        print("\n⏳ Esperando 5 segundos...")
        time.sleep(5)
        
        # Obtener estado después de iniciar
        print("\n📊 Estado después de iniciar:")
        controller.get_status()
        
        # Esperar respuesta
        response = controller.wait_for_response(10)
        if response:
            print(f"Estado: {response}")
        
        # Detener backup
        print("\n🛑 Deteniendo backup...")
        if controller.stop_backup():
            print("Comando de detención enviado")
            
            # Esperar confirmación
            response = controller.wait_for_response(10)
            if response:
                print(f"Confirmación: {response}")
        
    except KeyboardInterrupt:
        print("\n\n⏹️ Operación cancelada por el usuario")
    except Exception as e:
        logger.error(f"Error en la ejecución: {e}")

if __name__ == "__main__":
    main() 