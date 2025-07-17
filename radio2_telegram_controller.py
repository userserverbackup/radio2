#!/usr/bin/env python3
# -*- coding: utf-8*-
olador de Telegram para Radio2 Backup App
Controla la aplicación Android desde un servidor Python vía Telegram


import requests
import json
import time
import logging
from datetime import datetime
import os
from typing import Optional, Dict, Any

# Configuración de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s,handlers=[
        logging.FileHandler(radio2_controller.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class Radio2Controller:
    def __init__(self, bot_token: str, chat_id: str):
            Inicializa el controlador de Radio2     
        Args:
            bot_token: Token del bot de Telegram
            chat_id: ID del chat donde se enviarán los comandos
            self.bot_token = bot_token
        self.chat_id = chat_id
        self.base_url = f"https://api.telegram.org/bot{bot_token}"
        self.last_update_id =0        
        # Comandos disponibles
        self.commands = {
            '/ayuda': self.show_help,
            '/help': self.show_help,
            '/iniciar_backup': self.start_backup,
            '/detener_backup': self.stop_backup,
           '/backup_manual': self.manual_backup,
         '/estado': self.get_status,
          '/estadisticas': self.get_statistics,
           '/configuracion': self.get_configuration,
        '/limpiar_historial': self.clear_history,
         '/dispositivo': self.get_device_info,
            '/reiniciar': self.restart_app,
           '/logs': self.get_logs,
            '/test': self.test_connection
        }
        
        logger.info(f"Controlador Radio2 inicializado para chat {chat_id}")
    
    def send_message(self, text: str, parse_mode: str = "Markdown") -> bool:
            Envía un mensaje al chat configurado
        
        Args:
            text: Texto del mensaje
            parse_mode: Modo de parseo (Markdown, HTML, etc.)
            
        Returns:
            bool: True si se envió correctamente, False en caso contrario
         try:
            url = f"{self.base_url}/sendMessage            data =[object Object]
                'chat_id': self.chat_id,
            'text': text,
           'parse_mode': parse_mode
            }
            
            response = requests.post(url, json=data, timeout=30)
            
            if response.status_code == 200            logger.info(f"Mensaje enviado: {text[:50            return True
            else:
                logger.error(f"Error enviando mensaje: {response.status_code} - {response.text})            return False
                
        except Exception as e:
            logger.error(f"Excepción enviando mensaje: {e}")
            return False
    
    def get_updates(self) -> list:
            Obtiene las actualizaciones de Telegram
        
        Returns:
            list: Lista de actualizaciones
         try:
            url = f"{self.base_url}/getUpdates"
            params =[object Object]
            'offset': self.last_update_id + 1
            'timeout':30   }
            
            response = requests.get(url, params=params, timeout=35)
            
            if response.status_code == 200              data = response.json()
                if data.get('ok'):
                    updates = data.get('result', [])
                    if updates:
                        self.last_update_id = updates[-1]['update_id']                   return updates
                else:
                    logger.error(f"Error en getUpdates: {data}")
                    return []
            else:
                logger.error(f"ErrorHTTP en getUpdates: {response.status_code}")            return []
                
        except Exception as e:
            logger.error(f"Excepción en getUpdates: {e}")
            return []
    
    def process_message(self, message: Dict[str, Any]) -> None:
                Procesa un mensaje recibido
        
        Args:
            message: Diccionario con la información del mensaje
         try:
            chat_id = str(message['chat']['id'])           if chat_id != self.chat_id:
                logger.warning(f"Mensaje de chat no autorizado: {chat_id}")            return
            
            text = message.get('text', '').strip()
            if not text:
                return
            
            logger.info(f"Comando recibido: {text}")
            
            # Buscar comando
            for command, handler in self.commands.items():
                if text.lower().startswith(command.lower()):
                    handler()
                    return
            
            # Comando no reconocido
            self.send_message("❌ Comando no reconocido. Usa /ayuda para ver los comandos disponibles.")
            
        except Exception as e:
            logger.error(f"Error procesando mensaje: {e}")
    
    def show_help(self) -> None:
   estra la ayuda con todos los comandos disponibles"""
        help_text = """
🤖 *Comandos disponibles para Radio2*

📋 */ayuda* - Muestra esta lista de comandos
🔄 */iniciar_backup* - Inicia el backup automático
🛑 */detener_backup* - Detiene el backup automático
📤 */backup_manual* - Ejecuta un backup manual inmediato
📊 */estado* - Muestra el estado del backup
📈 */estadisticas* - Muestra estadísticas de uso
⚙️ */configuracion* - Muestra la configuración actual
🧹 */limpiar_historial* - Limpia el historial de archivos
📱 */dispositivo* - Información del dispositivo
🔄 */reiniciar* - Reinicia la aplicación
📝 */logs* - Obtiene los logs recientes
🧪 */test* - Prueba la conexión

_Envía cualquier comando para ejecutarlo._
  .strip()
        
        self.send_message(help_text)
    
    def start_backup(self) -> None:
  ia el backup automático"""
        self.send_message("🔄 Iniciando backup automático...")
        logger.info("Comando: iniciar_backup")
    
    def stop_backup(self) -> None:
   Detiene el backup automático"""
        self.send_message("🛑 Deteniendo backup automático...")
        logger.info("Comando: detener_backup")
    
    def manual_backup(self) -> None:
   Ejecuta un backup manual"""
        self.send_message("📤 Ejecutando backup manual...")
        logger.info("Comando: backup_manual")
    
    def get_status(self) -> None:
   Obtiene el estado del backup"""
        self.send_message("📊 Obteniendo estado del backup...")
        logger.info("Comando: estado")
    
    def get_statistics(self) -> None:
      tiene estadísticas"""
        self.send_message("📈 Obteniendo estadísticas...")
        logger.info("Comando: estadisticas")
    
    def get_configuration(self) -> None:
   tiene la configuración actual"""
        self.send_message("⚙️ Obteniendo configuración...")
        logger.info("Comando: configuracion")
    
    def clear_history(self) -> None:
  impia el historial"""
        self.send_message("🧹 Limpiando historial...")
        logger.info("Comando: limpiar_historial")
    
    def get_device_info(self) -> None:
btiene información del dispositivo"""
        self.send_message("📱 Obteniendo información del dispositivo...")
        logger.info("Comando: dispositivo")
    
    def restart_app(self) -> None:
    icia la aplicación"""
        self.send_message("🔄 Reiniciando aplicación...")
        logger.info("Comando: reiniciar")
    
    def get_logs(self) -> None:
    btiene los logs recientes"""
        self.send_message("📝 Obteniendo logs recientes...")
        logger.info("Comando: logs")
    
    def test_connection(self) -> None:
        Prueba la conexión"""
        self.send_message("🧪 Probando conexión...")
        logger.info("Comando: test")
    
    def run(self) -> None:
     el bucle principal del bot       logger.info("Iniciando bucle principal del bot...")
        
        while True:
            try:
                updates = self.get_updates()
                
                for update in updates:
                    if 'message' in update:
                        self.process_message(update['message'])
                
                time.sleep(1)  # Esperar 1 segundo entre consultas
                
            except KeyboardInterrupt:
                logger.info("Bot detenido por el usuario")             break
            except Exception as e:
                logger.error(f"Error en bucle principal: {e}")              time.sleep(5)  # Esperar 5 segundos antes de reintentar

def main():
    Función principal"""
    print("🤖 Controlador Radio2 - Servidor Python")
    print("=" * 50)
    
    # Configuración del bot
    bot_token = input("Ingresa el token del bot: ").strip()
    if not bot_token:
        print("❌ Token requerido")
        return
    
    chat_id = input("Ingresa el chat ID: ").strip()
    if not chat_id:
        print("❌ Chat ID requerido")
        return
    
    # Crear y ejecutar el controlador
    controller = Radio2Controller(bot_token, chat_id)
    
    # Probar conexión
    if controller.send_message("🚀 Controlador Radio2iado correctamente"):
        print("✅ Conexión exitosa")
        print("📱 El bot está listo para recibir comandos")
        print("🛑 Presiona Ctrl+C para detener")
        
        try:
            controller.run()
        except KeyboardInterrupt:
            print("👋 Bot detenido")
    else:
        print("❌ Error de conexión. Verifica el token y chat ID")

if __name__ == "__main__":
    main() 