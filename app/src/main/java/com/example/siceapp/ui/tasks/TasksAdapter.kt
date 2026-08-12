package com.example.siceapp.ui.tasks

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.siceapp.databinding.ItemTaskBinding
import com.example.siceapp.model.Task

class TasksAdapter(
    private var tasks: List<Task>,
    private val onClick: (Task) -> Unit
) : RecyclerView.Adapter<TasksAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        with(holder.binding) {
            tvTitle.text = task.title

            // Description
            if (!task.description.isNullOrEmpty()) {
                tvDescription.visibility = View.VISIBLE
                tvDescription.text = task.description
            } else {
                tvDescription.visibility = View.GONE
            }

            // Status with color
            val (statusLabel, statusColor) = when (task.status) {
                "pending"     -> Pair("Pendiente",   "#38bdf8")
                "in_progress" -> Pair("En progreso", "#fbbf24")
                "completed"   -> Pair("Completada",  "#34d399")
                "cancelled"   -> Pair("Cancelada",   "#64748b")
                else          -> Pair(task.status,   "#64748b")
            }
            tvStatus.text = statusLabel
            tvStatus.setTextColor(Color.parseColor(statusColor))

            // Assigned
            tvAssigned.text = "👤 ${task.assigned_name ?: "—"}"

            // Date
            tvDate.text = "📅 ${formatDate(task.start_date)}"

            // Time
            if (!task.start_time.isNullOrEmpty()) {
                tvTime.visibility = View.VISIBLE
                tvTime.text = "⏰ ${task.start_time}${
                    if (!task.end_time.isNullOrEmpty()) " — ${task.end_time}" else ""
                }"
            } else {
                tvTime.visibility = View.GONE
            }

            // Priority color on left border
            val priorityColor = when (task.priority) {
                "high"   -> "#ef4444"
                "medium" -> "#f59e0b"
                else     -> "#10b981"
            }
            root.setBackgroundResource(com.example.siceapp.R.drawable.card_bg)

            root.setOnClickListener { onClick(task) }
        }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    private fun formatDate(date: String): String = try {
        val p = date.split("-")
        "${p[2]}-${p[1]}-${p[0]}"
    } catch (e: Exception) { date }
}
