package com.service.assasinscreed02

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import android.app.AlertDialog
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.util.Log

class BackupManualSelectivoActivity : AppCompatActivity() {
    private val tiposSeleccionados = mutableSetOf<String>()
    private val carpetasSeleccionadas = mutableListOf<Uri>()
    private lateinit var txtCarpetas: TextView
    private lateinit var btnSeleccionarCarpeta: Button
    private lateinit var btnBackup: Button
    private lateinit var chkImagenes: CheckBox
    private lateinit var chkVideos: CheckBox
    private lateinit var chkOtros: CheckBox

    private val selectorCarpeta = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            carpetasSeleccionadas.add(it)
            mostrarCarpetas()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_manual_selectivo)

        chkImagenes = findViewById(R.id.chkImagenes)
        chkVideos = findViewById(R.id.chkVideos)
        chkOtros = findViewById(R.id.chkOtros)
        txtCarpetas = findViewById(R.id.txtCarpetas)
        btnSeleccionarCarpeta = findViewById(R.id.btnSeleccionarCarpeta)
        btnBackup = findViewById(R.id.btnBackupManual)

        btnSeleccionarCarpeta.setOnClickListener {
            selectorCarpeta.launch(null)
        }

        btnBackup.setOnClickListener {
            tiposSeleccionados.clear()
            if (chkImagenes.isChecked) tiposSeleccionados.addAll(listOf("jpg", "jpeg", "png"))
            if (chkVideos.isChecked) tiposSeleccionados.addAll(listOf("mp4", "mov", "avi"))
            if (chkOtros.isChecked) tiposSeleccionados.addAll(listOf("pdf", "docx", "txt"))
            if (tiposSeleccionados.isEmpty() || carpetasSeleccionadas.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un tipo y una carpeta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (carpetasSeleccionadas.size == 1) {
                mostrarDialogoModoBackup()
            } else {
                ejecutarBackupPorLotes()
            }
        }
    }

    private fun mostrarCarpetas() {
        val nombres = carpetasSeleccionadas.map { uri ->
            DocumentsContract.getTreeDocumentId(uri)
        }
        txtCarpetas.text = "Carpetas seleccionadas:\n" + nombres.joinToString("\n")
    }

    private fun mostrarDialogoModoBackup() {
        AlertDialog.Builder(this)
            .setTitle("¿Cómo quieres subir los archivos?")
            .setMessage("Puedes subir los archivos por lotes (uno a uno) o comprimirlos en un ZIP.")
            .setPositiveButton("Por lotes") { _, _ -> ejecutarBackupPorLotes() }
            .setNegativeButton("Comprimir en ZIP") { _, _ -> ejecutarBackupZip() }
            .show()
    }

    private fun ejecutarBackupPorLotes() {
        if (carpetasSeleccionadas.isEmpty() || tiposSeleccionados.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un tipo y una carpeta", Toast.LENGTH_SHORT).show()
            return
        }
        val archivosAEnviar = mutableListOf<ArchivoUri>()
        for (uri in carpetasSeleccionadas) {
            archivosAEnviar.addAll(obtenerArchivosDeCarpeta(uri).filter { archivo ->
                val ext = archivo.name.substringAfterLast('.', "").lowercase()
                tiposSeleccionados.contains(ext)
            })
        }
        if (archivosAEnviar.isEmpty()) {
            Toast.makeText(this, "No se encontraron archivos para enviar", Toast.LENGTH_LONG).show()
            return
        }
        val (token, chatId) = ErrorHandler.obtenerConfigBot(this)
        Thread {
            var enviados = 0
            val errores = mutableListOf<String>()
            for (archivo in archivosAEnviar) {
                try {
                    // Copiar el archivo a un File temporal accesible
                    val tempFile = File.createTempFile("lote_", "_${archivo.name}", cacheDir)
                    contentResolver.openInputStream(archivo.uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("No se pudo abrir el archivo: ${archivo.name}")
                    val errorEnvio = com.service.assasinscreed02.BackupUtils.enviarArchivoATelegram(token ?: "", chatId ?: "", tempFile)
                    tempFile.delete()
                    if (errorEnvio == null) {
                        enviados++
                    } else {
                        errores.add("${archivo.name}: $errorEnvio")
                    }
                } catch (e: Exception) {
                    errores.add("${archivo.name}: ${e.message}")
                }
            }
            runOnUiThread {
                if (errores.isEmpty()) {
                    Toast.makeText(this, "Backup por lotes finalizado: $enviados archivos enviados", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Backup por lotes: $enviados enviados, ${errores.size} errores", Toast.LENGTH_LONG).show()
                    Log.e("BackupManualSelectivo", "Errores en backup por lotes: ${errores.joinToString("; ")}")
                }
            }
        }.start()
    }

    private fun ejecutarBackupZip() {
        val uri = carpetasSeleccionadas.first()
        val nombreCarpeta = DocumentsContract.getTreeDocumentId(uri).substringAfterLast(":")
        // Crear el ZIP en el almacenamiento externo principal (/storage/emulated/0)
        val externalDir = android.os.Environment.getExternalStorageDirectory()
        val zipFile = File(externalDir, "$nombreCarpeta.zip")
        try {
            // Asegurar que el directorio existe
            zipFile.parentFile?.mkdirs()
            val archivos = obtenerArchivosDeCarpeta(uri)
            if (archivos.isEmpty()) {
                Toast.makeText(this, "No se encontraron archivos para comprimir", Toast.LENGTH_LONG).show()
                return
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (file in archivos) {
                    try {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        contentResolver.openInputStream(file.uri)?.use { input ->
                            input.copyTo(zos)
                        } ?: throw Exception("No se pudo abrir el archivo: "+file.name)
                        zos.closeEntry()
                    } catch (e: Exception) {
                        Log.e("BackupManualSelectivo", "Error con archivo ${file.name}: ${e.message}")
                        Toast.makeText(this, "Error con archivo ${file.name}: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            // Enviar el ZIP a Telegram usando BackupUtils
            val (token, chatId) = ErrorHandler.obtenerConfigBot(this)
            Thread {
                val errorEnvio = com.service.assasinscreed02.BackupUtils.enviarArchivoATelegram(token ?: "", chatId ?: "", zipFile)
                runOnUiThread {
                    if (errorEnvio == null) {
                        Toast.makeText(this, "Backup ZIP creado y enviado con éxito", Toast.LENGTH_LONG).show()
                        val eliminado = zipFile.delete()
                        Log.d("BackupManualSelectivo", "ZIP eliminado: $eliminado, ruta: ${zipFile.absolutePath}")
                    } else {
                        Toast.makeText(this, "Error al enviar el ZIP a Telegram: $errorEnvio", Toast.LENGTH_LONG).show()
                        Log.e("BackupManualSelectivo", "Error al enviar ZIP: $errorEnvio")
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("BackupManualSelectivo", "Error al comprimir o enviar ZIP: ${e.message}", e)
            Toast.makeText(this, "Error al comprimir o enviar ZIP: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private data class ArchivoUri(val name: String, val uri: Uri)

    private fun obtenerArchivosDeCarpeta(uri: Uri): List<ArchivoUri> {
        val archivos = mutableListOf<ArchivoUri>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val cursor = contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val mime = it.getString(1)
                val docId = it.getString(2)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    archivos.addAll(obtenerArchivosDeCarpeta(childUri))
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (tiposSeleccionados.contains(ext)) {
                        archivos.add(ArchivoUri(name, childUri))
                    }
                }
            }
        }
        return archivos
    }
} 