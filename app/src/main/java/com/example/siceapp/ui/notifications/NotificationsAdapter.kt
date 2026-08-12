package com.example.siceapp.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.siceapp.databinding.ItemNotificationBinding
import com.example.siceapp.model.Notification

class NotificationsAdapter(
    private val items: List<Notification>,
    private val onTap: (taskId: Int, taskTitle: String) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvMessage.text = item.message
            tvDate.text    = formatDate(item.created_at)
            tvTaskTitle.text = if (!item.task_title.isNullOrEmpty())
                "→ ${item.task_title}" else ""

            // Highlight unread
            root.alpha = if (item.read_at != null) 0.6f else 1f

            // Tap to navigate to task
            root.setOnClickListener {
                if (item.task_id != null && item.task_id > 0) {
                    onTap(item.task_id, item.task_title ?: "Tarea")
                }
            }
        }
    }

    override fun getItemCount() = items.size

    private fun formatDate(dateStr: String): String = try {
        val parts = dateStr.split(" ")
        val d = parts[0].split("-")
        "${d[2]}-${d[1]} ${parts[1].substring(0, 5)}"
    } catch (e: Exception) { dateStr }
}
