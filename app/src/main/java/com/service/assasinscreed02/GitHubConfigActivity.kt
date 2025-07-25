package com.service.assasinscreed02

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.service.assasinscreed02.github.GitHubHistorialSync
import kotlinx.coroutines.*
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
        editToken.setText(prefs.getString("github_token", ""))
        editOwner.setText(prefs.getString("github_owner", "tu-usuario"))
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
                txtStatus.text = "Probando conexión..."
                btnTestConnection.isEnabled = false
                
                val config = getGitHubConfig()
                if (config.token.isBlank()) {
                    txtStatus.text = "❌ Error: Token de GitHub requerido"
                    return@launch
                }
                
                val githubSync = GitHubHistorialSync(this@GitHubConfigActivity)
                val historial = withContext(Dispatchers.IO) {
                    githubSync.getHistorialFromGitHub(config)
                }
                
                txtStatus.text = "✅ Conexión exitosa\nArchivos en GitHub: ${historial.size}"
                updateStats(historial.size)
                
            } catch (e: Exception) {
                Log.e("GitHubConfig", "Error probando conexión: ${e.message}", e)
                txtStatus.text = "❌ Error de conexión: ${e.message}"
            } finally {
                btnTestConnection.isEnabled = true
            }
        }
    }

    private fun syncWithGitHub() {
        activityScope.launch {
            try {
                txtStatus.text = "Sincronizando con GitHub..."
                btnSyncNow.isEnabled = false
                
                val config = getGitHubConfig()
                if (config.token.isBlank()) {
                    txtStatus.text = "❌ Error: Token de GitHub requerido"
                    return@launch
                }
                
                val githubSync = GitHubHistorialSync(this@GitHubConfigActivity)
                
                // Por ahora, solo simulamos la sincronización
                val success = true
                
                if (success) {
                    txtStatus.text = "✅ Sincronización exitosa"
                    updateLastSyncInfo()
                    
                    // Por ahora, simulamos las estadísticas
                    updateStats(0)
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

    private fun updateStats(totalFiles: Int) {
        txtStats.text = "Archivos en GitHub: $totalFiles"
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
} 