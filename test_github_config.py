#!/usr/bin/env python3
"""
Script de prueba para verificar la configuración de GitHub
"""

import requests
import json
import base64
from datetime import datetime

# Configuración de GitHub
GITHUB_TOKEN = "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"
GITHUB_OWNER = "userserverbackup"
GITHUB_REPO = "radio2-backup-historial"
GITHUB_BRANCH = "main"
HISTORIAL_FILE_PATH = "historial_backup.json"

# Headers para la API de GitHub
headers = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "Radio2-Backup-Test"
}

def test_github_connection():
    """Prueba la conexión con GitHub"""
    print("🔍 Probando conexión con GitHub...")
    
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}"
    
    try:
        response = requests.get(url, headers=headers)
        
        if response.status_code == 200:
            repo_data = response.json()
            print(f"✅ Conexión exitosa!")
            print(f"📁 Repositorio: {repo_data['full_name']}")
            print(f"🌐 URL: {repo_data['html_url']}")
            print(f"📊 Visibilidad: {repo_data['visibility']}")
            print(f"⭐ Stars: {repo_data['stargazers_count']}")
            print(f"🔄 Última actualización: {repo_data['updated_at']}")
            return True
        else:
            print(f"❌ Error de conexión: {response.status_code}")
            print(f"📝 Respuesta: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error de red: {e}")
        return False

def check_historial_file():
    """Verifica si existe el archivo de historial"""
    print("\n📄 Verificando archivo de historial...")
    
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/contents/{HISTORIAL_FILE_PATH}?ref={GITHUB_BRANCH}"
    
    try:
        response = requests.get(url, headers=headers)
        
        if response.status_code == 200:
            file_data = response.json()
            print(f"✅ Archivo de historial encontrado!")
            print(f"📁 Nombre: {file_data['name']}")
            print(f"📏 Tamaño: {file_data['size']} bytes")
            print(f"🔗 URL: {file_data['html_url']}")
            print(f"🔄 Última actualización: {file_data['updated_at']}")
            
            # Decodificar y mostrar contenido
            content = base64.b64decode(file_data['content']).decode('utf-8')
            historial = json.loads(content)
            print(f"📊 Archivos en historial: {len(historial)}")
            
            if historial:
                print("\n📋 Últimos 5 archivos:")
                for i, file in enumerate(historial[:5]):
                    print(f"  {i+1}. {file['fileName']} ({file['fileType']}) - {file['fileSize']} bytes")
            
            return True
        elif response.status_code == 404:
            print("⚠️ Archivo de historial no encontrado (normal para la primera vez)")
            return False
        else:
            print(f"❌ Error verificando archivo: {response.status_code}")
            print(f"📝 Respuesta: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error verificando archivo: {e}")
        return False

def create_test_historial():
    """Crea un archivo de historial de prueba"""
    print("\n🔄 Creando archivo de historial de prueba...")
    
    # Datos de prueba
    test_historial = [
        {
            "id": 1,
            "fileName": "test_image_001.jpg",
            "filePath": "/storage/emulated/0/DCIM/Camera/test_image_001.jpg",
            "fileHash": "a1b2c3d4e5f678901234567890123456",
            "fileSize": 1024000,
            "fileType": "image",
            "uploadDate": int(datetime.now().timestamp() * 1000),
            "uploadStatus": "success",
            "telegramMessageId": "123456789",
            "errorMessage": ""
        },
        {
            "id": 2,
            "fileName": "test_video_001.mp4",
            "filePath": "/storage/emulated/0/DCIM/Camera/test_video_001.mp4",
            "fileHash": "b2c3d4e5f6789012345678901234567",
            "fileSize": 5120000,
            "fileType": "video",
            "uploadDate": int(datetime.now().timestamp() * 1000),
            "uploadStatus": "success",
            "telegramMessageId": "123456790",
            "errorMessage": ""
        }
    ]
    
    # Convertir a JSON
    json_content = json.dumps(test_historial, indent=2)
    encoded_content = base64.b64encode(json_content.encode('utf-8')).decode('utf-8')
    
    # Crear commit
    commit_data = {
        "message": f"Crear historial de backup inicial - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "content": encoded_content,
        "branch": GITHUB_BRANCH
    }
    
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/contents/{HISTORIAL_FILE_PATH}"
    
    try:
        response = requests.put(url, headers=headers, json=commit_data)
        
        if response.status_code in [200, 201]:
            print("✅ Archivo de historial creado exitosamente!")
            return True
        else:
            print(f"❌ Error creando archivo: {response.status_code}")
            print(f"📝 Respuesta: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error creando archivo: {e}")
        return False

def main():
    """Función principal"""
    print("🚀 Iniciando pruebas de configuración de GitHub")
    print("=" * 50)
    
    # Probar conexión
    if not test_github_connection():
        print("\n❌ No se pudo conectar a GitHub. Verifica la configuración.")
        return
    
    # Verificar archivo de historial
    if not check_historial_file():
        print("\n📝 Creando archivo de historial inicial...")
        if create_test_historial():
            print("\n✅ Configuración completada exitosamente!")
        else:
            print("\n❌ Error creando archivo de historial.")
            return
    
    print("\n🎉 ¡Configuración de GitHub lista para usar!")
    print("\n📋 Resumen:")
    print(f"   • Repositorio: {GITHUB_OWNER}/{GITHUB_REPO}")
    print(f"   • Rama: {GITHUB_BRANCH}")
    print(f"   • Archivo: {HISTORIAL_FILE_PATH}")
    print(f"   • Token: Configurado correctamente")

if __name__ == "__main__":
    main() 