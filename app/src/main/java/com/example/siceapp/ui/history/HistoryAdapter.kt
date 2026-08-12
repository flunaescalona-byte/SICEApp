package com.example.siceapp.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.siceapp.databinding.ItemHistoryTaskBinding
import com.example.siceapp.model.Task

class HistoryAdapter(
    private var tasks: List<Task>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryTaskBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        with(holder.binding) {
            tvNumber.text = "${position + 1}"
            tvTitle.text  = task.title
            tvAssigned.text = "👤 ${task.assigned_name ?: "—"}"
            tvCategory.text = "${task.cat_icon ?: ""} ${task.cat_name ?: "Sin categoría"}".trim()
            tvDate.text = "📅 ${formatDate(task.start_date)}" +
                if (task.end_date != task.start_date) " → ${formatDate(task.end_date)}" else ""
            tvTime.text = if (!task.start_time.isNullOrEmpty())
                "⏰ ${task.start_time}${if (!task.end_time.isNullOrEmpty()) " — ${task.end_time}" else ""}"
                else ""
            tvComments.text = "💬 ${task.comment_count}"

            val (label, color) = getStatusInfo(task.status)
            tvStatus.text = label
            tvStatus.setTextColor(Color.parseColor(color))
        }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    private fun getStatusInfo(status: String): Pair<String, String> = when(status) {
        "pending"     -> Pair("Pendiente",   "#38bdf8")
        "in_progress" -> Pair("En progreso", "#fbbf24")
        "completed"   -> Pair("Completada",  "#34d399")
        "cancelled"   -> Pair("Cancelada",   "#64748b")
        else          -> Pair(status,         "#64748b")
    }

    private fun formatDate(date: String): String = try {
        val p = date.split("-"); "${p[2]}-${p[1]}-${p[0]}"
    } catch (e: Exception) { date }
}
