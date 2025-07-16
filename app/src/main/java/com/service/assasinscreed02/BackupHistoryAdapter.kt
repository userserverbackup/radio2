package com.service.assasinscreed02

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class BackupHistoryAdapter(private val items: List<BackupWorker.BackupHistoryEntry>) : RecyclerView.Adapter<BackupHistoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNombreArchivo: TextView = view.findViewById(R.id.txtNombreArchivo)
        val txtFechaBackup: TextView = view.findViewById(R.id.txtFechaBackup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_backup_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtNombreArchivo.text = item.fileName
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        holder.txtFechaBackup.text = sdf.format(Date(item.timestamp))
    }

    override fun getItemCount(): Int = items.size
} 