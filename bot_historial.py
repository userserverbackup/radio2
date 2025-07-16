import json
from collections import Counter, defaultdict
from telegram import Update, Document
from telegram.ext import Application, CommandHandler, MessageHandler, ContextTypes, filters

TOKEN = "7721187473:AAHcRkxt8lmoPpBAF5PBTxUYuPbTvKyEU2U"
HISTORIAL_PATH = "historial_backup.json"

def cargar_historial():
    try:
        with open(HISTORIAL_PATH, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return "[]"

def parsear_historial():
    try:
        with open(HISTORIAL_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return []

async def recibir_documento(update: Update, context: ContextTypes.DEFAULT_TYPE):
    document: Document = update.message.document
    if document.file_name == "historial_backup.json":
        file = await document.get_file()
        await file.download_to_drive(HISTORIAL_PATH)
        await update.message.reply_text("Historial actualizado correctamente.")
    else:
        await update.message.reply_text("Por favor, envía un archivo llamado historial_backup.json.")

async def historial(update: Update, context: ContextTypes.DEFAULT_TYPE):
    historial = parsear_historial()
    if not historial:
        await update.message.reply_text("No hay historial disponible.")
        return
    # Contar archivos por carpeta
    carpetas = defaultdict(int)
    for entry in historial:
        # Suponiendo que entry['fileName'] tiene la ruta completa
        ruta = entry.get('fileName', '')
        carpeta = ruta.rsplit('/', 1)[0] if '/' in ruta else '(raíz)'
        carpetas[carpeta] += 1
    total = len(historial)
    resumen = f"Resumen de historial:\nTotal de archivos: {total}\n\n"
    for carpeta, cantidad in sorted(carpetas.items()):
        resumen += f"{carpeta}: {cantidad} archivos\n"
    await update.message.reply_text(resumen)
    # Si el historial es pequeño, también lo envía como texto
    historial_json = cargar_historial()
    if len(historial_json) <= 4000:
        await update.message.reply_text(historial_json)
    else:
        await update.message.reply_text("El historial completo es muy grande. Usa /historial_completo para recibir el archivo.")

async def historial_completo(update: Update, context: ContextTypes.DEFAULT_TYPE):
    try:
        await update.message.reply_document(document=open(HISTORIAL_PATH, "rb"), filename="historial_backup.json")
    except Exception:
        await update.message.reply_text("No se pudo enviar el archivo de historial.")

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "¡Hola! Soy el bot de historial de backup.\n"
        "Comandos disponibles:\n"
        "/historial - Resumen y estado del historial\n"
        "/historial_completo - Descargar el archivo completo de historial\n"
        "Envía un archivo 'historial_backup.json' para actualizar el historial."
    )

if __name__ == "__main__":
    app = Application.builder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("historial", historial))
    app.add_handler(CommandHandler("historial_completo", historial_completo))
    app.add_handler(MessageHandler(filters.Document.ALL, recibir_documento))
    print("Bot corriendo. Puedes enviar historial_backup.json o usar /historial.")
    print("Presiona Ctrl+C para detener el bot.")
    app.run_polling(allowed_updates=Update.ALL_TYPES) 