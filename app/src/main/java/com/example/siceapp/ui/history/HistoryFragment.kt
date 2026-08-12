package com.example.siceapp.ui.history

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentHistoryBinding
import com.example.siceapp.model.Task
import com.example.siceapp.model.User
import com.example.siceapp.utils.TokenManager
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter
    private var currentTasks = listOf<Task>()
    private var isMonthFilter = true
    private var isAdmin = false

    private val months = arrayOf("Enero","Febrero","Marzo","Abril","Mayo","Junio",
        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
    private val years = (2024..2027).map { it.toString() }

    private var selectedMonth    = Calendar.getInstance().get(Calendar.MONTH) + 1
    private var selectedYear     = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedWeekStart = ""
    private var weekDates        = listOf<Pair<String, String>>()

    private var users         = listOf<User>()
    private var selectedUserId = 0  // 0 = todos
    private var showHidden = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(emptyList())
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        // Check if admin to show user filter
        lifecycleScope.launch {
            isAdmin = TokenManager(requireContext()).getRole().first() == "admin"
            if (isAdmin) {
                binding.panelUser.visibility      = View.VISIBLE
                binding.btnToggleHidden.visibility = View.VISIBLE
                loadUsers()
            }
        }

        setupMonthSpinner()
        setupYearSpinner()
        setupWeekSpinner()

        binding.btnFilterMonth.setOnClickListener { setFilterType(true) }
        binding.btnFilterWeek.setOnClickListener  { setFilterType(false) }
        binding.btnApplyFilter.setOnClickListener { loadHistory() }
        binding.btnToggleHidden.setOnClickListener {
            showHidden = !showHidden
            binding.btnToggleHidden.text = if (showHidden) "🙈 Ocultar ocultas" else "👁️ Ver ocultas"
            binding.btnToggleHidden.setTextColor(
                if (showHidden) android.graphics.Color.parseColor("#fbbf24")
                else android.graphics.Color.parseColor("#64748b"))
        }
        binding.btnGeneratePdf.setOnClickListener { openPdfInApp() }

        loadHistory()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getUsers()
                if (response.isSuccessful && response.body()?.ok == true) {
                    users = response.body()!!.data ?: emptyList()
                    setupUserSpinner()
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupUserSpinner() {
        if (_binding == null) return
        val names = listOf("👥 Todos los usuarios") + users.map { it.name }
        val adapter = makeSpinnerAdapter(names)
        binding.spinnerUser.adapter = adapter
        binding.spinnerUser.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    selectedUserId = if (pos == 0) 0 else users[pos - 1].id
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setFilterType(isMonth: Boolean) {
        if (_binding == null) return
        isMonthFilter = isMonth
        binding.panelMonth.visibility = if (isMonth) View.VISIBLE else View.GONE
        binding.panelWeek.visibility  = if (isMonth) View.GONE   else View.VISIBLE
        binding.btnFilterMonth.setBackgroundResource(
            if (isMonth) R.drawable.btn_primary else R.drawable.btn_outline)
        binding.btnFilterMonth.setTextColor(
            if (isMonth) Color.WHITE else Color.parseColor("#64748b"))
        binding.btnFilterWeek.setBackgroundResource(
            if (!isMonth) R.drawable.btn_primary else R.drawable.btn_outline)
        binding.btnFilterWeek.setTextColor(
            if (!isMonth) Color.WHITE else Color.parseColor("#64748b"))
    }

    private fun setupMonthSpinner() {
        binding.spinnerMonth.adapter = makeSpinnerAdapter(months.toList())
        binding.spinnerMonth.setSelection(selectedMonth - 1)
        binding.spinnerMonth.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    selectedMonth = pos + 1; setupWeekSpinner()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupYearSpinner() {
        binding.spinnerYear.adapter = makeSpinnerAdapter(years)
        binding.spinnerYear.setSelection(years.indexOf(selectedYear.toString()).takeIf { it >= 0 } ?: 0)
        binding.spinnerYear.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    selectedYear = years[pos].toInt(); setupWeekSpinner()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupWeekSpinner() {
        weekDates = generateWeeks(selectedYear, selectedMonth)
        if (weekDates.isNotEmpty()) selectedWeekStart = weekDates[0].first
        val labels = weekDates.map { it.second }
        binding.spinnerWeek.adapter = makeSpinnerAdapter(labels, smallText = true)
        binding.spinnerWeek.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    selectedWeekStart = weekDates.getOrNull(pos)?.first ?: ""
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
    }

    private fun makeSpinnerAdapter(items: List<String>, smallText: Boolean = false) =
        object : ArrayAdapter<String>(requireContext(),
            android.R.layout.simple_spinner_item, items) {
            override fun getView(pos: Int, v: View?, parent: ViewGroup): View {
                val view = super.getView(pos, v, parent)
                (view as? TextView)?.apply {
                    setTextColor(Color.WHITE)
                    if (smallText) textSize = 11f
                }
                return view
            }
            override fun getDropDownView(pos: Int, v: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(pos, v, parent)
                (view as? TextView)?.apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#0f1f3d"))
                    setPadding(32, 20, 32, 20)
                    if (smallText) textSize = 11f
                }
                return view
            }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun generateWeeks(year: Int, month: Int): List<Pair<String,String>> {
        val weeks = mutableListOf<Pair<String,String>>()
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysBack = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysBack)
        val endCal = Calendar.getInstance()
        endCal.set(year, month - 1, lastDay)
        while (!cal.after(endCal)) {
            val start = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH))
            val startLabel = "%02d-%02d-%04d".format(cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR))
            cal.add(Calendar.DAY_OF_MONTH, 6)
            val endLabel = "%02d-%02d-%04d".format(cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR))
            weeks.add(Pair(start, "$startLabel al $endLabel"))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return weeks
    }

    private fun loadHistory() {
        if (_binding == null) return
        binding.progressBar.visibility  = View.VISIBLE
        binding.tvEmpty.visibility      = View.GONE
        binding.summaryContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getTasks(
                    month = selectedMonth,
                    year  = selectedYear,
                    showHidden = if (showHidden) "1" else null
                )
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    var tasks = response.body()!!.data ?: emptyList()

                    // Filter by week
                    if (!isMonthFilter && selectedWeekStart.isNotEmpty()) {
                        val weekEnd = getWeekEnd(selectedWeekStart)
                        tasks = tasks.filter {
                            it.start_date >= selectedWeekStart && it.start_date <= weekEnd
                        }
                    }

                    // Filter by user
                    if (selectedUserId > 0) {
                        val selectedUser = users.find { it.id == selectedUserId }
                        if (selectedUser != null) {
                            tasks = tasks.filter {
                                it.assigned_name == selectedUser.name
                            }
                        }
                    }

                    currentTasks = tasks
                    showTasks(tasks)
                } else {
                    showToastSafe("Error al cargar historial")
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun getWeekEnd(weekStart: String): String {
        val cal = Calendar.getInstance()
        val p = weekStart.split("-")
        cal.set(p[0].toInt(), p[1].toInt()-1, p[2].toInt())
        cal.add(Calendar.DAY_OF_MONTH, 6)
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun showTasks(tasks: List<Task>) {
        if (_binding == null) return
        if (tasks.isEmpty()) {
            binding.tvEmpty.visibility         = View.VISIBLE
            binding.recyclerHistory.visibility = View.GONE
            binding.summaryContainer.visibility = View.GONE
            return
        }
        binding.tvEmpty.visibility          = View.GONE
        binding.recyclerHistory.visibility  = View.VISIBLE
        binding.summaryContainer.visibility = View.VISIBLE
        binding.tvTotal.text      = tasks.size.toString()
        binding.tvCompleted.text  = tasks.count { it.status == "completed" }.toString()
        binding.tvInProgress.text = tasks.count { it.status == "in_progress" }.toString()
        binding.tvPending.text    = tasks.count { it.status == "pending" }.toString()
        adapter.updateTasks(tasks)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPdfInApp() {
        if (currentTasks.isEmpty()) { showToastSafe("No hay tareas para PDF"); return }

        val token     = ApiConfig.getToken()
        val base      = "https://calendario.fernandolunatech.cl"
        val userParam   = if (selectedUserId > 0) "%26user=$selectedUserId" else ""
        val hiddenParam = if (showHidden) "%26show_hidden=1" else ""
        val pdfPath   = if (isMonthFilter)
            "/history.php%3Fmonth=$selectedMonth%26year=$selectedYear$userParam%26pdf=1"
        else
            "/history.php%3Ftype=week%26week=$selectedWeekStart%26month=$selectedMonth%26year=$selectedYear$userParam%26pdf=1"
        val url = "$base/api/v1/web_login.php?app_token=$token&redirect=$pdfPath"

        // Create fullscreen dialog
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(com.example.siceapp.R.layout.dialog_pdf)
        dialog.setCancelable(true)

        val webView   = dialog.findViewById<WebView>(com.example.siceapp.R.id.webViewPdf)
        val progress  = dialog.findViewById<View>(com.example.siceapp.R.id.progressPdf)
        val btnClose  = dialog.findViewById<View>(com.example.siceapp.R.id.btnClosePdf)
        val btnPrint  = dialog.findViewById<View>(com.example.siceapp.R.id.btnPrint)

        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            builtInZoomControls  = true
            displayZoomControls  = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, u: String?, f: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(v: WebView?, u: String?) {
                progress.visibility = View.GONE
                // Trigger print dialog via JS
                webView.evaluateJavascript("window.print()", null)
            }
            override fun shouldOverrideUrlLoading(v: WebView?,
                r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return false
                return if (u.contains("calendario.fernandolunatech.cl")) {
                    v?.loadUrl(u); true
                } else false
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnPrint.setOnClickListener {
            val printManager = requireContext()
                .getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
            val jobName = "SICE Historial"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }

        dialog.show()
        webView.loadUrl(url)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
