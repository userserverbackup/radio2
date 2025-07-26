package com.service.assasinscreed02

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.service.assasinscreed02.github.GitHubHistorialSync
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class GitHubConfigActivity : AppCompatActivity() {
    private lateinit var editToken: EditText
    private lateinit var editOwner: EditText
    private lateinit var editRepo: EditText
    private lateinit var editBranch: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSyncNow: Button
    private lateinit var btnSaveConfig: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtLastSync: TextView
    private lateinit var txtStats: TextView
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_github_config)
        
        initViews()
        loadCurrentConfig()
        setupListeners()
        updateStatusDisplay()
    }

    private fun initViews() {
        editToken = findViewById(R.id.editGitHubToken)
        editOwner = findViewById(R.id.editGitHubOwner)
        editRepo = findViewById(R.id.editGitHubRepo)
        editBranch = findViewById(R.id.editGitHubBranch)
        btnTestConnection = findViewById(R.id.btnTestGitHubConnection)
        btnSyncNow = findViewById(R.id.btnSyncGitHubNow)
        btnSaveConfig = findViewById(R.id.btnSaveGitHubConfig)
        txtStatus = findViewById(R.id.txtGitHubStatus)
        txtLastSync = findViewById(R.id.txtGitHubLastSync)
        txtStats = findViewById(R.id.txtGitHubStats)
    }

    private fun loadCurrentConfig() {
        val prefs = getSharedPreferences("github_config", MODE_PRIVATE)
        editToken.setText(prefs.getString("github_token", "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"))
        editOwner.setText(prefs.getString("github_owner", "userserverbackup"))
        editRepo.setText(prefs.getString("github_repo", "radio2-backup-historial"))
        editBranch.setText(prefs.getString("github_branch", "main"))
        
        updateLastSyncInfo()
    }

    private fun setupListeners() {
        btnTestConnection.setOnClickListener {
            testGitHubConnection()
        }
        
        btnSyncNow.setOnClickListener {
            syncWithGitHub()
        }
        
        btnSaveConfig.setOnClickListener {
            saveGitHubConfig()
        }
    }

    private fun testGitHubConnection() {
        activityScope.launch {
            try {
                txtStatus.text = "🔍 Probando conexión..."
                btnTestConnection.isEnabled = false
                
                val config = getGitHubConfig()
                if (!config.isValid()) {
                    txtStatus.text = "❌ Error: Configuración incompleta"
                    return@launch
                }
                
                val githubSync = GitHubHistorialSync(this@GitHubConfigActivity)
                val error = withContext(Dispatchers.IO) {
                    githubSync.testConnection(config)
                }
                
                if (error == null) {
                    // Si la conexión es exitosa, obtener estadísticas
                    val stats = withContext(Dispatchers.IO) {
                        githubSync.getGlobalStatistics(config)
                    }
                    
                    val totalFiles = stats["totalFiles"] as? Int ?: 0
                    val totalSize = stats["totalSize"] as? Long ?: 0L
                    
                    txtStatus.text = "✅ Conexión exitosa\n📁 Archivos en GitHub: $totalFiles\n💾 Tamaño total: ${formatFileSize(totalSize)}"
                    updateStats(totalFiles, totalSize)
                } else {
                    txtStatus.text = "❌ Error de conexión: ${error.message}"
                }
                
            } catch (e: Exception) {
                Log.e("GitHubConfig", "Error probando conexión: ${e.message}", e)
                txtStatus.text = "❌ Error: ${e.message}"
            } finally {
                btnTestConnection.isEnabled = true
            }
        }
    }

    private fun syncWithGitHub() {
        activityScope.launch {
            try {
                txtStatus.text = "🔄 Sincronizando con GitHub..."
                btnSyncNow.isEnabled = false
                
                val config = getGitHubConfig()
                if (!config.isValid()) {
                    txtStatus.text = "❌ Error: Configuración incompleta"
                    return@launch
                }
                
                val githubSync = GitHubHistorialSync(this@GitHubConfigActivity)
                
                // Obtener historial local desde la base de datos
                val repository = com.service.assasinscreed02.repository.BackupRepository(this@GitHubConfigActivity)
                val historialLocal = withContext(Dispatchers.IO) {
                    repository.getAllFiles().first()
                }
                
                // Sincronizar con GitHub
                val success = withContext(Dispatchers.IO) {
                    githubSync.syncHistorialToGitHub(historialLocal, config)
                }
                
                if (success) {
                    txtStatus.text = "✅ Sincronización exitosa"
                    updateLastSyncInfo()
                    
                    // Actualizar estadísticas reales
                    val stats = withContext(Dispatchers.IO) {
                        githubSync.getGlobalStatistics(config)
                    }
                    
                    val totalFiles = stats["totalFiles"] as? Int ?: 0
                    val totalSize = stats["totalSize"] as? Long ?: 0L
                    updateStats(totalFiles, totalSize)
                    
                    // Mostrar información adicional
                    val repoInfo = stats["repoInfo"] as? Map<*, *>
                    if (repoInfo != null) {
                        val owner = repoInfo["owner"] as? String ?: ""
                        val repo = repoInfo["repo"] as? String ?: ""
                        txtStatus.append("\n🌐 Repositorio: $owner/$repo")
                    }
                } else {
                    txtStatus.text = "❌ Error en la sincronización"
                }
                
            } catch (e: Exception) {
                Log.e("GitHubConfig", "Error sincronizando: ${e.message}", e)
                txtStatus.text = "❌ Error: ${e.message}"
            } finally {
                btnSyncNow.isEnabled = true
            }
        }
    }

    private fun saveGitHubConfig() {
        val token = editToken.text.toString().trim()
        val owner = editOwner.text.toString().trim()
        val repo = editRepo.text.toString().trim()
        val branch = editBranch.text.toString().trim()
        
        if (token.isBlank()) {
            Toast.makeText(this, "Token de GitHub es requerido", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (owner.isBlank() || repo.isBlank()) {
            Toast.makeText(this, "Owner y Repo son requeridos", Toast.LENGTH_SHORT).show()
            return
        }
        
        val prefs = getSharedPreferences("github_config", MODE_PRIVATE)
        prefs.edit().apply {
            putString("github_token", token)
            putString("github_owner", owner)
            putString("github_repo", repo)
            putString("github_branch", branch.ifBlank { "main" })
        }.apply()
        
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        txtStatus.text = "✅ Configuración guardada"
        updateStatusDisplay()
    }

    private fun getGitHubConfig(): GitHubHistorialSync.GitHubConfig {
        return GitHubHistorialSync.GitHubConfig(
            token = editToken.text.toString().trim(),
            owner = editOwner.text.toString().trim(),
            repo = editRepo.text.toString().trim(),
            branch = editBranch.text.toString().trim().ifBlank { "main" }
        )
    }

    private fun updateLastSyncInfo() {
        val prefs = getSharedPreferences("github_sync", MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync", 0L)
        
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            txtLastSync.text = "Última sincronización: ${sdf.format(Date(lastSync))}"
        } else {
            txtLastSync.text = "Nunca sincronizado"
        }
    }

    private fun updateStats(totalFiles: Int, totalSize: Long = 0L) {
        val sizeText = if (totalSize > 0) " (${formatFileSize(totalSize)})" else ""
        txtStats.text = "Archivos en GitHub: $totalFiles$sizeText"
    }

    private fun updateStatusDisplay() {
        val config = getGitHubConfig()
        if (config.isValid()) {
            txtStatus.text = "✅ Configuración válida"
            
            // Cargar estadísticas si están disponibles
            activityScope.launch {
                try {
                    val githubSync = GitHubHistorialSync(this@GitHubConfigActivity)
                    val stats = withContext(Dispatchers.IO) {
                        githubSync.getGlobalStatistics(config)
                    }
                    
                    val totalFiles = stats["totalFiles"] as? Int ?: 0
                    val totalSize = stats["totalSize"] as? Long ?: 0L
                    updateStats(totalFiles, totalSize)
                } catch (e: Exception) {
                    Log.e("GitHubConfig", "Error cargando estadísticas: ${e.message}")
                }
            }
        } else {
            txtStatus.text = "⚠️ Configuración incompleta"
            txtStats.text = "Archivos en GitHub: 0"
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
} 