package com.example.siceapp.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentTaskFormBinding
import com.example.siceapp.model.User
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskFormFragment : Fragment() {

    private var _binding: FragmentTaskFormBinding? = null
    private val binding get() = _binding!!

    private var editTaskId: Int? = null
    private var users = listOf<User>()
    private var categoryNames = listOf<String>()
    private var categoryIds = listOf<Int?>()

    private var startDate = ""
    private var endDate   = ""
    private var startTime = ""
    private var endTime   = ""

    // Collaborators
    private val selectedCollaborators = mutableListOf<User>()

    companion object {
        fun newInstance(taskId: Int? = null): TaskFormFragment {
            val f = TaskFormFragment()
            taskId?.let { f.arguments = Bundle().apply { putInt("task_id", it) } }
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editTaskId = arguments?.getInt("task_id", -1)?.takeIf { it != -1 }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvFormTitle.text = if (editTaskId != null) "Editar Tarea" else "Nueva Tarea"
        binding.btnDelete.visibility = if (editTaskId != null) View.VISIBLE else View.GONE
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.btnStartDate.setOnClickListener { showDatePicker(true) }
        binding.btnEndDate.setOnClickListener   { showDatePicker(false) }
        binding.btnStartTime.setOnClickListener { showTimePicker(true) }
        binding.btnEndTime.setOnClickListener   { showTimePicker(false) }

        // Add collaborator button
        binding.btnAddCollaborator.setOnClickListener { showCollaboratorPicker() }

        setupSpinner(binding.spinnerPriority, listOf("🟢 Baja", "🟡 Media", "🔴 Alta"), 1)
        setupSpinner(binding.spinnerStatus,
            listOf("Pendiente", "En progreso", "Completada", "Cancelada"), 0)

        binding.btnSave.setOnClickListener { saveTask() }
        binding.btnDelete.setOnClickListener { deleteTask() }

        loadUsersAndCategories()
    }

    private fun setupSpinner(
        spinner: android.widget.Spinner,
        items: List<String>,
        selectedIndex: Int = 0
    ) {
        val adapter = object : ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_spinner_item, items
        ) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(pos, convertView, parent)
                (v as? TextView)?.apply { setTextColor(Color.WHITE); textSize = 14f }
                return v
            }
            override fun getDropDownView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(pos, convertView, parent)
                (v as? TextView)?.apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#0f1f3d"))
                    textSize = 14f
                    setPadding(32, 24, 32, 24)
                }
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIndex)
    }

    private fun showCollaboratorPicker() {
        if (users.isEmpty()) {
            Toast.makeText(requireContext(), "Cargando usuarios...", Toast.LENGTH_SHORT).show()
            return
        }

        val assignedPos = binding.spinnerAssign.selectedItemPosition
        val assignedUser = users.getOrNull(assignedPos)

        // Filter out already selected and assigned user
        val available = users.filter { u ->
            u.id != assignedUser?.id &&
            selectedCollaborators.none { it.id == u.id }
        }

        if (available.isEmpty()) {
            Toast.makeText(requireContext(), "No hay más usuarios disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        val names = available.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar colaborador")
            .setItems(names) { _, which ->
                val selected = available[which]
                selectedCollaborators.add(selected)
                updateCollaboratorChips()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateCollaboratorChips() {
        if (_binding == null) return
        binding.collabChipsContainer.removeAllViews()

        selectedCollaborators.forEach { collab ->
            val chip = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                binding.collabChipsContainer, false
            )
            val tv = chip as? TextView ?: chip.findViewById(android.R.id.text1)
            tv?.apply {
                text = "👤 ${collab.name}  ✕"
                setTextColor(Color.parseColor("#0ea5e9"))
                textSize = 13f
                setPadding(0, 8, 0, 8)
                setOnClickListener {
                    selectedCollaborators.remove(collab)
                    updateCollaboratorChips()
                }
            }
            binding.collabChipsContainer.addView(chip)
        }

        // Update hint
        if (selectedCollaborators.isEmpty()) {
            binding.btnAddCollaborator.text = "+ Agregar colaborador"
        } else {
            binding.btnAddCollaborator.text = "+ Agregar otro colaborador"
        }
    }

    private fun loadUsersAndCategories() {
        lifecycleScope.launch {
            try {
                val usersResp = ApiConfig.service.getUsers()
                if (usersResp.isSuccessful && usersResp.body()?.ok == true) {
                    users = usersResp.body()!!.data ?: emptyList()
                    setupSpinner(binding.spinnerAssign, users.map { it.name })
                }

                val catsResp = ApiConfig.service.getCategories()
                if (catsResp.isSuccessful) {
                    val catsData = catsResp.body()?.data
                    if (catsData is List<*>) {
                        val catList = catsData.filterIsInstance<Map<String, Any>>()
                        categoryNames = listOf("Sin categoría") + catList.map {
                            "${it["icon"] ?: ""} ${it["name"] ?: ""}".trim()
                        }
                        categoryIds = listOf(null) + catList.map {
                            (it["id"] as? Double)?.toInt()
                        }
                        setupSpinner(binding.spinnerCategory, categoryNames)
                    }
                }

                editTaskId?.let { loadTaskForEdit(it) }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error cargando datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTaskForEdit(taskId: Int) {
        lifecycleScope.launch {
            try {
                val resp = ApiConfig.service.getTaskDetail(path = "tasks/$taskId")
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    val task = resp.body()!!.data!!
                    binding.etTitle.setText(task.title)
                    binding.etDescription.setText(task.description ?: "")

                    startDate = task.start_date
                    endDate   = task.end_date
                    startTime = task.start_time ?: ""
                    endTime   = task.end_time ?: ""

                    binding.btnStartDate.text = formatDisplay(startDate)
                    binding.btnEndDate.text   = formatDisplay(endDate)
                    if (startTime.isNotEmpty()) binding.btnStartTime.text = startTime
                    if (endTime.isNotEmpty())   binding.btnEndTime.text   = endTime

                    val userIdx = users.indexOfFirst { it.name == task.assigned_name }
                    if (userIdx >= 0) binding.spinnerAssign.setSelection(userIdx)

                    val prioIdx = when(task.priority) { "low" -> 0; "high" -> 2; else -> 1 }
                    binding.spinnerPriority.setSelection(prioIdx)

                    val statIdx = when(task.status) {
                        "pending" -> 0; "in_progress" -> 1; "completed" -> 2; else -> 3
                    }
                    binding.spinnerStatus.setSelection(statIdx)

                    // Load existing collaborators
                    task.collaborators?.forEach { collab ->
                        val user = users.find { it.id == collab.id }
                        if (user != null) selectedCollaborators.add(user)
                    }
                    updateCollaboratorChips()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error cargando tarea", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTask() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "El título es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }
        if (startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(requireContext(), "Selecciona las fechas", Toast.LENGTH_SHORT).show()
            return
        }
        if (users.isEmpty()) {
            Toast.makeText(requireContext(), "No hay usuarios disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        val assignedUser = users[binding.spinnerAssign.selectedItemPosition]
        val priority = when(binding.spinnerPriority.selectedItemPosition) {
            0 -> "low"; 2 -> "high"; else -> "medium"
        }
        val status = when(binding.spinnerStatus.selectedItemPosition) {
            0 -> "pending"; 1 -> "in_progress"; 2 -> "completed"; else -> "cancelled"
        }
        val catId = categoryIds.getOrNull(binding.spinnerCategory.selectedItemPosition)

        val body = mutableMapOf(
            "title"       to title,
            "description" to binding.etDescription.text.toString().trim(),
            "assigned_to" to assignedUser.id.toString(),
            "start_date"  to startDate,
            "end_date"    to endDate,
            "priority"    to priority,
            "status"      to status,
            "collaborators" to selectedCollaborators.map { it.id.toString() }.joinToString(",")
        )
        if (startTime.isNotEmpty()) body["start_time"] = startTime
        if (endTime.isNotEmpty())   body["end_time"]   = endTime
        catId?.let { body["category_id"] = it.toString() }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Guardando..."

        lifecycleScope.launch {
            try {
                val response = if (editTaskId != null)
                    ApiConfig.service.editTask(path = "tasks/$editTaskId", body = body)
                else
                    ApiConfig.service.createTask(body = body)

                if (response.isSuccessful && response.body()?.ok == true) {
                    Toast.makeText(requireContext(),
                        if (editTaskId != null) "✅ Tarea actualizada" else "✅ Tarea creada",
                        Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Guardar"
                }
            }
        }
    }

    private fun deleteTask() {
        val id = editTaskId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar tarea")
            .setMessage("¿Estás seguro? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val resp = ApiConfig.service.deleteTask(path = "tasks/$id")
                        if (resp.isSuccessful && resp.body()?.ok == true) {
                            Toast.makeText(requireContext(), "Tarea eliminada", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                            parentFragmentManager.popBackStack()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val dateStr = "$year-${String.format("%02d", month+1)}-${String.format("%02d", day)}"
            val display = "${String.format("%02d", day)}-${String.format("%02d", month+1)}-$year"
            if (isStart) {
                startDate = dateStr
                binding.btnStartDate.text = display
                if (endDate.isEmpty()) { endDate = dateStr; binding.btnEndDate.text = display }
            } else {
                endDate = dateStr
                binding.btnEndDate.text = display
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val timeStr = "${String.format("%02d", hour)}:${String.format("%02d", minute)}"
            if (isStart) { startTime = timeStr; binding.btnStartTime.text = timeStr }
            else         { endTime   = timeStr; binding.btnEndTime.text   = timeStr }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun formatDisplay(date: String): String {
        return try {
            val p = date.split("-"); "${p[2]}-${p[1]}-${p[0]}"
        } catch (e: Exception) { date }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
