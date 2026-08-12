package com.example.siceapp.ui.tasks

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentTaskDetailBinding
import com.example.siceapp.model.CommentRequest
import com.example.siceapp.model.StatusRequest
import com.example.siceapp.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!
    private var taskId = 0
    private var taskTitle = ""
    private var currentUserId = 0
    private var isAdmin = false
    private var isHidden = false
    private var cameraImageUri: Uri? = null
    private val selectedImages = mutableListOf<Uri>()

    companion object {
        fun newInstance(id: Int, title: String): TaskDetailFragment {
            val f = TaskDetailFragment()
            f.arguments = Bundle().apply {
                putInt("task_id", id)
                putString("task_title", title)
            }
            return f
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedImages.add(cameraImageUri!!)
            updatePhotoPreview()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
            updatePhotoPreview()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskId    = arguments?.getInt("task_id") ?: 0
        taskTitle = arguments?.getString("task_title") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTaskTitle.text = taskTitle
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // Load current user info
        lifecycleScope.launch {
            try {
                val tm = com.example.siceapp.utils.TokenManager(requireContext())
                currentUserId = tm.getId().first()?.toIntOrNull() ?: 0
                isAdmin = tm.getRole().first() == "admin"
            } catch (e: Exception) {}
        }
        binding.recyclerComments.layoutManager = LinearLayoutManager(requireContext())

        // Camera
        binding.btnCamera.setOnClickListener { checkCameraAndOpen() }

        // Gallery
        binding.btnGallery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                galleryLauncher.launch("image/*")
            } else {
                val perm = Manifest.permission.READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(requireContext(), perm) ==
                    PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("image/*")
                } else {
                    permissionLauncher.launch(arrayOf(perm))
                }
            }
        }

        // Send comment
        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString().trim()
            if (text.isEmpty() && selectedImages.isEmpty()) {
                Toast.makeText(requireContext(),
                    "Escribe un comentario o adjunta una foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendComment(text)
        }

        // Status spinner
        val statusOptions = arrayOf("🔵 Pendiente", "🟡 En progreso", "🟢 Completada", "⚫ Cancelada")
        val statusColors  = arrayOf("#38bdf8",     "#fbbf24",       "#34d399",      "#64748b")
        val statusAdapter = object : android.widget.ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_spinner_item, statusOptions) {
            override fun getView(pos: Int, v: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(pos, v, parent)
                (view as? android.widget.TextView)?.apply {
                    setTextColor(android.graphics.Color.parseColor(statusColors[pos]))
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                return view
            }
            override fun getDropDownView(pos: Int, v: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(pos, v, parent)
                (view as? android.widget.TextView)?.apply {
                    setTextColor(android.graphics.Color.parseColor(statusColors[pos]))
                    setBackgroundColor(android.graphics.Color.parseColor("#0f1f3d"))
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(32, 24, 32, 24)
                }
                return view
            }
        }
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatus.adapter = statusAdapter

        var statusInitialized = false
        binding.spinnerStatus.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?,
                    v: View?, pos: Int, id: Long) {
                    if (!statusInitialized) { statusInitialized = true; return }
                    val status = when(pos) {
                        0 -> "pending"; 1 -> "in_progress"
                        2 -> "completed"; else -> "cancelled"
                    }
                    updateStatus(status)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }

        // Edit + Toggle hidden buttons (admin only)
        lifecycleScope.launch {
            val role = TokenManager(requireContext()).getRole().first()
            if (_binding == null) return@launch
            if (role == "admin") {
                // Edit button
                binding.btnEdit.visibility = View.VISIBLE
                binding.btnEdit.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, TaskFormFragment.newInstance(taskId))
                        .addToBackStack(null)
                        .commit()
                }
                // Toggle hidden button
                binding.btnToggleHidden.visibility = View.VISIBLE
                binding.btnToggleHidden.setOnClickListener { toggleHidden() }
            }
        }

        loadDetail()
    }

    private fun checkCameraAndOpen() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) openCamera()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "SICE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_",
            ".jpg",
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.provider", photoFile
        )
        cameraLauncher.launch(cameraImageUri)
    }

    private fun updatePhotoPreview() {
        binding.scrollPhotos.visibility =
            if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
        binding.photosContainer.removeAllViews()
        binding.tvPhotoCount.text =
            if (selectedImages.isNotEmpty()) "📷 ${selectedImages.size} foto(s)" else ""

        selectedImages.forEach { uri ->
            val imgView = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(160, 120)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(0, 0, 8, 0)
            }
            Glide.with(this).load(uri).centerCrop().into(imgView)
            imgView.setOnLongClickListener {
                selectedImages.remove(uri)
                updatePhotoPreview()
                true
            }
            binding.photosContainer.addView(imgView)
        }
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val detailResp = ApiConfig.service.getTaskDetail(path = "tasks/$taskId")
                if (detailResp.isSuccessful && detailResp.body()?.ok == true) {
                    val task = detailResp.body()!!.data!!
                    binding.tvDescription.text =
                        task.description?.takeIf { it.isNotEmpty() } ?: "Sin descripción"
                    binding.tvAssigned.text = "👤 ${task.assigned_name ?: "—"}"
                    binding.tvDates.text = "📅 ${formatDate(task.start_date)}" +
                        if (task.end_date != task.start_date)
                            " → ${formatDate(task.end_date)}" else ""
                    binding.tvTime.text = if (!task.start_time.isNullOrEmpty())
                        "⏰ ${task.start_time}${if (!task.end_time.isNullOrEmpty())
                            " — ${task.end_time}" else ""}" else ""
                    binding.tvCategory.text =
                        "${task.cat_icon ?: ""} ${task.cat_name ?: ""}".trim()

                    val statusPos = when(task.status) {
                        "pending" -> 0; "in_progress" -> 1; "completed" -> 2; else -> 3
                    }
                    binding.spinnerStatus.setSelection(statusPos)
                    // Update hidden button text
                    isHidden = task.hidden
                    if (isAdmin) {
                        binding.btnToggleHidden.text = if (isHidden)
                            "👁️ Mostrar tarea en calendario"
                        else "🙈 Ocultar tarea del calendario"
                        binding.btnToggleHidden.setTextColor(
                            if (isHidden) android.graphics.Color.parseColor("#fbbf24")
                            else android.graphics.Color.parseColor("#64748b"))
                    }
                    // Update hidden button state
                    isHidden = task.hidden
                    binding.btnToggleHidden.text = if (isHidden)
                        "👁️ Mostrar tarea en calendario"
                    else
                        "🙈 Ocultar tarea del calendario"
                    binding.btnToggleHidden.setTextColor(
                        if (isHidden) android.graphics.Color.parseColor("#fbbf24")
                        else android.graphics.Color.parseColor("#64748b"))
                }

                val commentsResp = ApiConfig.service.getComments(taskId = taskId)
                if (commentsResp.isSuccessful && commentsResp.body()?.ok == true) {
                    val comments = commentsResp.body()!!.data ?: emptyList()
                    binding.recyclerComments.adapter = CommentsAdapter(
                        comments.toMutableList(),
                        currentUserId,
                        isAdmin,
                        viewLifecycleOwner
                    ) { loadDetail() }
                    binding.tvCommentsTitle.text = "// comentarios (${comments.size})"
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun sendComment(text: String) {
        binding.btnSendComment.isEnabled = false
        binding.btnSendComment.text = "Enviando..."

        lifecycleScope.launch {
            try {
                if (selectedImages.isEmpty()) {
                    val response = ApiConfig.service.addComment(
                        body = CommentRequest(taskId, text)
                    )
                    if (response.isSuccessful && response.body()?.ok == true) {
                        onCommentSent()
                    } else {
                        showToast("Error al enviar comentario")
                    }
                } else {
                    sendCommentWithImages(text)
                }
            } catch (e: Exception) {
                showToast("Error de conexión")
            } finally {
                binding.btnSendComment.isEnabled = true
                binding.btnSendComment.text = "Enviar"
            }
        }
    }

    private suspend fun sendCommentWithImages(text: String) {
        try {
            val token = ApiConfig.getToken()
            val url = "${ApiConfig.BASE_URL}?path=comments"

            val result = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                builder.addFormDataPart("task_id", taskId.toString())
                builder.addFormDataPart("comment", text)

                selectedImages.forEachIndexed { index, uri ->
                    try {
                        val inputStream = requireContext().contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            val tempFile = File(requireContext().cacheDir, "upload_$index.jpg")
                            tempFile.writeBytes(bytes)
                            builder.addFormDataPart(
                                "files[]", "photo_$index.jpg",
                                tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-Token", token)
                    .post(builder.build())
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                Pair(response.isSuccessful, body)
            }

            val (isSuccessful, responseBody) = result
            val json = JSONObject(responseBody)

            if (isSuccessful && json.optBoolean("ok")) {
                onCommentSent()
            } else {
                val errMsg = json.optString("error", "Error al enviar")
                showToast(errMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error subiendo fotos: ${e.message}")
        }
    }

    private fun onCommentSent() {
        binding.etComment.setText("")
        selectedImages.clear()
        updatePhotoPreview()
        loadDetail()
        showToast("✅ Comentario enviado")
    }

    private fun updateStatus(status: String) {
        lifecycleScope.launch {
            try {
                ApiConfig.service.updateTaskStatus(
                    path = "tasks/$taskId/status",
                    body = StatusRequest(status)
                )
                showToast("Estado actualizado")
            } catch (e: Exception) { }
        }
    }

    private fun showToast(msg: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDate(date: String): String {
        return try {
            val p = date.split("-")
            "${p[2]}-${p[1]}-${p[0]}"
        } catch (e: Exception) { date }
    }

    private fun toggleHidden() {
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.toggleTaskHidden(path = "tasks/$taskId/hidden")
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    isHidden = !isHidden
                    binding.btnToggleHidden.text = if (isHidden)
                        "👁️ Mostrar tarea en calendario"
                    else
                        "🙈 Ocultar tarea del calendario"
                    binding.btnToggleHidden.setTextColor(
                        if (isHidden) android.graphics.Color.parseColor("#fbbf24")
                        else android.graphics.Color.parseColor("#64748b"))
                    val msg = if (isHidden)
                        "✅ Tarea ocultada del calendario"
                    else
                        "✅ Tarea visible en calendario"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
