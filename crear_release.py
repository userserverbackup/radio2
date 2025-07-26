#!/usr/bin/env python3
"""
Script para crear releases automáticos en GitHub para Radio2
Uso: python crear_release.py <version> <descripcion>
Ejemplo: python crear_release.py 1.5.1 "Nuevas características de actualización automática"
"""

import sys
import os
import subprocess
import json
import requests
from datetime import datetime

# Configuración - CAMBIAR ESTOS VALORES
GITHUB_TOKEN = "tu_token_aqui"  # Token de GitHub con permisos de repo
REPO_OWNER = "userserverbackup"  # Tu usuario de GitHub
REPO_NAME = "radio2"  # Tu repositorio
GITHUB_API_BASE = "https://api.github.com"

def crear_release(version, descripcion):
    """Crea un release en GitHub"""
    
    if not GITHUB_TOKEN or GITHUB_TOKEN == "tu_token_aqui":
        print("❌ Error: Debes configurar tu token de GitHub en el script")
        print("1. Ve a GitHub.com -> Settings -> Developer settings -> Personal access tokens")
        print("2. Genera un nuevo token con permisos 'repo'")
        print("3. Reemplaza 'tu_token_aqui' en el script")
        return False
    
    # Verificar que existe el APK
    apk_path = f"app/build/outputs/apk/debug/app-debug.apk"
    if not os.path.exists(apk_path):
        print(f"❌ Error: No se encontró el APK en {apk_path}")
        print("Ejecuta './gradlew assembleDebug' primero")
        return False
    
    # Crear el release
    release_data = {
        "tag_name": f"v{version}",
        "name": f"Radio2 v{version}",
        "body": descripcion,
        "draft": False,
        "prerelease": False
    }
    
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    url = f"{GITHUB_API_BASE}/repos/{REPO_OWNER}/{REPO_NAME}/releases"
    
    try:
        print(f"🔄 Creando release v{version}...")
        response = requests.post(url, headers=headers, json=release_data)
        
        if response.status_code == 201:
            release_info = response.json()
            release_id = release_info["id"]
            print(f"✅ Release creado exitosamente: {release_info['html_url']}")
            
            # Subir el APK
            return subir_apk(release_id, apk_path, version)
        else:
            print(f"❌ Error creando release: {response.status_code}")
            print(f"Respuesta: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

def subir_apk(release_id, apk_path, version):
    """Sube el APK al release"""
    
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    # Nombre del archivo APK
    apk_name = f"radio2-v{version}.apk"
    
    url = f"{GITHUB_API_BASE}/repos/{REPO_OWNER}/{REPO_NAME}/releases/{release_id}/assets"
    
    try:
        print(f"🔄 Subiendo APK: {apk_name}...")
        
        with open(apk_path, 'rb') as f:
            files = {'file': (apk_name, f, 'application/vnd.android.package-archive')}
            response = requests.post(url, headers=headers, files=files)
        
        if response.status_code == 201:
            asset_info = response.json()
            print(f"✅ APK subido exitosamente: {asset_info['browser_download_url']}")
            return True
        else:
            print(f"❌ Error subiendo APK: {response.status_code}")
            print(f"Respuesta: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error subiendo APK: {e}")
        return False

def generar_descripcion_template(version):
    """Genera una descripción de release basada en el template"""
    
    template = f"""## Radio2 v{version}

### 🎉 Nuevas Características
- Sistema de actualización automática vía GitHub
- Verificación automática de actualizaciones
- Descarga e instalación automática de APKs
- Interfaz mejorada para gestión de actualizaciones

### 🔧 Mejoras Técnicas
- Arquitectura Clean Architecture implementada
- Sistema de exclusiones de archivos inteligente
- Refactorización completa del código de backup
- Mejor manejo de errores y logging

### 🐛 Correcciones
- Optimización de rendimiento
- Mejoras en la estabilidad
- Corrección de bugs menores

### 📱 Instalación
La aplicación verificará automáticamente si hay actualizaciones disponibles.
Si encuentras una actualización, simplemente toca "Actualizar" para descargar e instalar.

### 🔄 Actualización Automática
- Verificación diaria de actualizaciones
- Notificaciones de nuevas versiones
- Instalación con un solo clic
- Rollback fácil si es necesario

---
*Release creado automáticamente el {datetime.now().strftime('%d/%m/%Y %H:%M:%S')}*"""
    
    return template

def main():
    if len(sys.argv) < 2:
        print("❌ Uso: python crear_release.py <version> [descripcion]")
        print("Ejemplo: python crear_release.py 1.5.1")
        print("Ejemplo: python crear_release.py 1.5.1 'Nuevas características'")
        return
    
    version = sys.argv[1]
    
    # Validar formato de versión
    if not version.replace('.', '').isdigit():
        print("❌ Error: La versión debe tener formato numérico (ej: 1.5.1)")
        return
    
    # Descripción opcional
    if len(sys.argv) > 2:
        descripcion = sys.argv[2]
    else:
        descripcion = generar_descripcion_template(version)
    
    print(f"🚀 Creando release v{version} para {REPO_OWNER}/{REPO_NAME}")
    print(f"📝 Descripción: {descripcion[:100]}...")
    print()
    
    # Confirmar
    confirmar = input("¿Continuar? (y/N): ").lower().strip()
    if confirmar != 'y':
        print("❌ Cancelado")
        return
    
    # Crear release
    if crear_release(version, descripcion):
        print()
        print("🎉 ¡Release creado exitosamente!")
        print(f"📱 Los usuarios recibirán la actualización automáticamente")
        print(f"🔗 URL del release: https://github.com/{REPO_OWNER}/{REPO_NAME}/releases/tag/v{version}")
    else:
        print()
        print("❌ Error creando el release")
        print("Verifica tu token de GitHub y la configuración del repositorio")

if __name__ == "__main__":
    main() 