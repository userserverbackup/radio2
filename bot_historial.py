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

async def recibir_documento(update: Update, context: ContextTypes.DEFAULT_TYPE):
    document: Document = update.message.document
    if document.file_name == "historial_backup.json":
        file = await document.get_file()
        await file.download_to_drive(HISTORIAL_PATH)
        await update.message.reply_text("Historial actualizado correctamente.")
    else:
        await update.message.reply_text("Por favor, envía un archivo llamado historial_backup.json.")

async def historial(update: Update, context: ContextTypes.DEFAULT_TYPE):
    historial_json = cargar_historial()
    # Telegram limita los mensajes a 4096 caracteres, dividimos si es necesario
    if len(historial_json) > 4000:
        for i in range(0, len(historial_json), 4000):
            await update.message.reply_text(historial_json[i:i+4000])
    else:
        await update.message.reply_text(historial_json)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "¡Hola! Soy el bot de historial de backup.\n"
        "Comandos disponibles:\n"
        "/historial - Obtener el historial actual\n"
        "Envía un archivo 'historial_backup.json' para actualizar el historial."
    )

if __name__ == "__main__":
    app = Application.builder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("historial", historial))
    app.add_handler(MessageHandler(filters.Document.ALL, recibir_documento))
    print("Bot corriendo. Puedes enviar historial_backup.json o usar /historial.")
    print("Presiona Ctrl+C para detener el bot.")
    app.run_polling(allowed_updates=Update.ALL_TYPES) 