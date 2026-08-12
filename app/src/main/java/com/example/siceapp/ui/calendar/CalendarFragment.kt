package com.example.siceapp.ui.calendar

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentCalendarBinding
import com.example.siceapp.model.Task
import com.example.siceapp.ui.tasks.TaskDetailFragment
import com.example.siceapp.ui.tasks.TasksAdapter
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private var currentCalendar = Calendar.getInstance()
    private var tasksByDay = mapOf<String, List<Task>>()
    private lateinit var dayTasksAdapter: TasksAdapter

    private val monthNames = arrayOf(
        "Enero","Febrero","Marzo","Abril","Mayo","Junio",
        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
    )
    private val dayNames = arrayOf("Lun","Mar","Mié","Jue","Vie","Sáb","Dom")
    private var showHidden = false
    private var isAdmin = false
    private var selectedDay = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dayTasksAdapter = TasksAdapter(emptyList()) { task -> openTaskDetail(task) }
        binding.recyclerDayTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDayTasks.adapter = dayTasksAdapter

        setupDayHeaders()

        binding.btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            loadCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            loadCalendar()
        }

        // Check admin and setup toggle
        lifecycleScope.launch {
            isAdmin = com.example.siceapp.utils.TokenManager(requireContext()).getRole().first() == "admin"
            if (_binding != null && isAdmin) {
                binding.btnToggleHidden.visibility = View.VISIBLE
                binding.btnToggleHidden.setOnClickListener {
                    showHidden = !showHidden
                    binding.btnToggleHidden.text = if (showHidden)
                        "🙈 Ocultar tareas ocultas"
                    else
                        "👁️ Ver tareas ocultas"
                    binding.btnToggleHidden.setTextColor(
                        if (showHidden) android.graphics.Color.parseColor("#fbbf24")
                        else ContextCompat.getColor(requireContext(), R.color.text_muted))
                    loadCalendar(reloadSelectedDay = true)
                }
            }
        }

        loadCalendar()
    }

    private fun setupDayHeaders() {
        if (_binding == null) return
        binding.dayHeaders.removeAllViews()
        dayNames.forEach { day ->
            val tv = TextView(requireContext()).apply {
                text = day
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            binding.dayHeaders.addView(tv)
        }
    }

    private fun loadCalendar(reloadSelectedDay: Boolean = false) {
        if (_binding == null) return
        val month = currentCalendar.get(Calendar.MONTH) + 1
        val year  = currentCalendar.get(Calendar.YEAR)
        binding.tvMonthYear.text = "${monthNames[month-1]} $year"
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getCalendar(month = month, year = year, showHidden = if (showHidden) "1" else null)
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    tasksByDay = response.body()!!.data?.by_day ?: mapOf()
                    drawCalendar(month, year)
                    // Re-show tasks for selected day after toggle
                    if (reloadSelectedDay && selectedDay > 0) {
                        onDayClick(selectedDay, month, year)
                    }
                } else {
                    showToastSafe("Error al cargar calendario")
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun drawCalendar(month: Int, year: Int) {
        if (_binding == null) return
        binding.calendarGrid.removeAllViews()
        binding.dayTasksContainer.visibility = View.GONE

        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        var firstDow = cal.get(Calendar.DAY_OF_WEEK) - 2
        if (firstDow < 0) firstDow = 6

        val today = Calendar.getInstance()
        val todayDay   = today.get(Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(Calendar.MONTH) + 1
        val todayYear  = today.get(Calendar.YEAR)

        val cellSize = (resources.displayMetrics.widthPixels - 8) / 7

        repeat(firstDow) {
            val empty = View(requireContext())
            val params = android.widget.GridLayout.LayoutParams()
            params.width = cellSize; params.height = cellSize
            binding.calendarGrid.addView(empty, params)
        }

        for (day in 1..daysInMonth) {
            val hasTasks = tasksByDay.containsKey(day.toString())
            val isToday  = day == todayDay && month == todayMonth && year == todayYear

            val cell = layoutInflater.inflate(R.layout.item_calendar_day,
                binding.calendarGrid, false)
            val tvDay   = cell.findViewById<TextView>(R.id.tvDay)
            val tvDot   = cell.findViewById<TextView>(R.id.tvDot)
            val tvCount = cell.findViewById<TextView>(R.id.tvCount)

            tvDay.text = day.toString()
            tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            tvDay.background = null

            when {
                isToday && day == selectedDay -> {
                    // Today AND selected
                    tvDay.setBackgroundResource(R.drawable.today_bg)
                    tvDay.setTextColor(android.graphics.Color.WHITE)
                }
                isToday -> {
                    // Just today
                    tvDay.setBackgroundResource(R.drawable.today_bg)
                    tvDay.setTextColor(android.graphics.Color.WHITE)
                }
                day == selectedDay -> {
                    // Just selected - blue border/background
                    tvDay.setBackgroundResource(R.drawable.selected_day_bg)
                    tvDay.setTextColor(android.graphics.Color.parseColor("#38bdf8"))
                }
            }

            if (hasTasks) {
                val count = tasksByDay[day.toString()]?.size ?: 0
                tvDot.visibility = View.VISIBLE
                tvCount.visibility = if (count > 1) View.VISIBLE else View.GONE
                tvCount.text = count.toString()
            } else {
                tvDot.visibility = View.GONE
                tvCount.visibility = View.GONE
            }

            val params = android.widget.GridLayout.LayoutParams()
            params.width = cellSize; params.height = cellSize
            cell.layoutParams = params

            cell.setOnClickListener {
                selectedDay = day
                drawCalendar(month, year)
                onDayClick(day, month, year)
            }
            binding.calendarGrid.addView(cell)
        }
    }

    private fun onDayClick(day: Int, month: Int, year: Int) {
        if (_binding == null) return
        val tasks = tasksByDay[day.toString()] ?: emptyList()
        val dayStr = "${String.format("%02d",day)}-${String.format("%02d",month)}-$year"
        binding.tvSelectedDay.text = if (tasks.isEmpty())
            "// $dayStr — sin actividades"
        else
            "// $dayStr — ${tasks.size} tarea(s)"
        binding.dayTasksContainer.visibility = View.VISIBLE
        dayTasksAdapter.updateTasks(tasks)
    }

    private fun openTaskDetail(task: Task) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TaskDetailFragment.newInstance(task.id, task.title))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
