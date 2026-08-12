package com.example.siceapp.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentNotificationsBinding
import com.example.siceapp.ui.tasks.TaskDetailFragment
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        loadNotifications()
    }

    private fun loadNotifications() {
        if (_binding == null) return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getNotifications()
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    val items = response.body()!!.data?.items ?: emptyList()
                    if (items.isEmpty()) {
                        binding.tvEmpty.visibility         = View.VISIBLE
                        binding.recyclerNotifications.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility         = View.GONE
                        binding.recyclerNotifications.visibility = View.VISIBLE
                        binding.recyclerNotifications.adapter = NotificationsAdapter(items) { taskId, taskTitle ->
                            navigateToTask(taskId, taskTitle)
                        }
                    }
                    // Mark as read
                    try { ApiConfig.service.markNotificationsRead() } catch (e: Exception) {}
                } else {
                    showToastSafe("Error al cargar notificaciones")
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigateToTask(taskId: Int, taskTitle: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TaskDetailFragment.newInstance(taskId, taskTitle))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
