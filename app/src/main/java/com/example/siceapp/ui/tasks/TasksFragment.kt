package com.example.siceapp.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentTasksBinding
import com.example.siceapp.model.Task
import com.example.siceapp.utils.TokenManager
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TasksAdapter
    private var currentStatus: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TasksAdapter(emptyList()) { task -> openTaskDetail(task) }
        binding.recyclerTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTasks.adapter = adapter

        val statuses = listOf("Todas", "Pendiente", "En progreso", "Completada", "Cancelada")
        val spinnerAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, statuses)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatus.adapter = spinnerAdapter
        binding.spinnerStatus.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    currentStatus = when(pos) {
                        1->"pending"; 2->"in_progress"; 3->"completed"; 4->"cancelled"; else->null
                    }
                    loadTasks()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }

        binding.swipeRefresh.setColorSchemeColors(0xFF0ea5e9.toInt())
        binding.swipeRefresh.setOnRefreshListener { loadTasks() }

        // FAB - solo admin
        lifecycleScope.launch {
            try {
                val role = TokenManager(requireContext()).getRole().first()
                if (_binding != null) {
                    if (role == "admin") {
                        binding.fabNewTask.visibility = View.VISIBLE
                        binding.fabNewTask.setOnClickListener {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, TaskFormFragment.newInstance())
                                .addToBackStack(null)
                                .commit()
                        }
                    } else {
                        binding.fabNewTask.visibility = View.GONE
                    }
                }
            } catch (e: Exception) { }
        }

        loadTasks()
    }

    private fun loadTasks() {
        if (_binding == null) return
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getTasks(status = currentStatus)
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    showTasks(response.body()!!.data ?: emptyList())
                } else {
                    showToastSafe("Error al cargar tareas")
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) {
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun openTaskDetail(task: Task) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TaskDetailFragment.newInstance(task.id, task.title))
            .addToBackStack(null)
            .commit()
    }

    private fun showTasks(tasks: List<Task>) {
        if (_binding == null) return
        adapter.updateTasks(tasks)
        binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerTasks.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        if (_binding == null) return
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
