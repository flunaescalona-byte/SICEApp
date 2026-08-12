package com.example.siceapp.ui.profile

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentProfileBinding
import com.example.siceapp.ui.main.MainActivity
import com.example.siceapp.utils.TokenManager
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.ui.profile.EditProfileFragment
import com.example.siceapp.utils.showToastSafe
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

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var tokenManager: TokenManager
    private var cameraImageUri: Uri? = null
    private var currentPhotoUrl: String? = null
    private var isAdmin = false
    private lateinit var teamAdapter: TeamAdapter

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success && cameraImageUri != null) uploadPhoto(cameraImageUri!!) }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { uploadPhoto(it) } }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> if (perms.values.all { it }) openCamera() else showToastSafe("Permiso denegado") }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tokenManager = TokenManager(requireContext())

        // Setup team recycler
        teamAdapter = TeamAdapter(emptyList(), false, viewLifecycleOwner,
            onStatusChanged = { loadTeam() },
            onEditProfile = { user ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer,
                        EditProfileFragment.newInstance(user.id, user.name))
                    .addToBackStack(null).commit()
            }
        )
        binding.recyclerEquipo.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEquipo.adapter = teamAdapter

        // Check if admin
        lifecycleScope.launch {
            isAdmin = tokenManager.getRole().first() == "admin"
            if (!isAdmin) binding.tabEquipo.visibility = View.GONE
            teamAdapter = TeamAdapter(emptyList(), isAdmin, viewLifecycleOwner,
                onStatusChanged = { loadTeam() },
                onEditProfile = { user ->
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer,
                            EditProfileFragment.newInstance(user.id, user.name))
                        .addToBackStack(null).commit()
                }
            )
            binding.recyclerEquipo.adapter = teamAdapter
        }

        // TABS
        binding.tabMiPerfil.setOnClickListener { showTab(true) }
        binding.tabEquipo.setOnClickListener   { showTab(false) }

        // Photo
        binding.imgPhoto.setOnClickListener {
            if (!currentPhotoUrl.isNullOrEmpty()) showPhotoFullscreen(currentPhotoUrl!!)
            else showToastSafe("No tienes foto de perfil aún")
        }
        binding.btnChangePhoto.setOnClickListener { showPhotoOptions() }

        // Edit own profile button
        binding.btnEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditProfileFragment.newInstance(0, ""))
                .addToBackStack(null).commit()
        }

        // Status
        binding.btnAvailable.setOnClickListener  { updateStatus("available") }
        binding.btnDeployed.setOnClickListener   { updateStatus("deployed") }
        binding.btnOffDuty.setOnClickListener    { updateStatus("off_duty") }
        binding.btnCommission.setOnClickListener { updateStatus("commission") }

        // Logout
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                try { ApiConfig.service.logout() } catch (e: Exception) {}
                tokenManager.clearSession()
                ApiConfig.setToken("")
                (activity as? MainActivity)?.goToLogin()
            }
        }

        loadProfile()
    }

    private fun showTab(showProfile: Boolean) {
        if (_binding == null) return
        binding.panelMiPerfil.visibility = if (showProfile) View.VISIBLE else View.GONE
        binding.panelEquipo.visibility   = if (showProfile) View.GONE else View.VISIBLE

        // Update tab styles
        binding.tabMiPerfil.setBackgroundResource(
            if (showProfile) R.drawable.btn_primary else android.R.color.transparent)
        binding.tabMiPerfil.setTextColor(
            if (showProfile) Color.WHITE else Color.parseColor("#64748b"))
        binding.tabEquipo.setBackgroundResource(
            if (!showProfile) R.drawable.btn_primary else android.R.color.transparent)
        binding.tabEquipo.setTextColor(
            if (!showProfile) Color.WHITE else Color.parseColor("#64748b"))

        if (!showProfile) loadTeam()
    }

    private fun loadProfile() {
        if (_binding == null) return
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getMe()
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    val user = response.body()!!.data!!
                    binding.tvName.text     = user.name
                    binding.tvPosition.text = user.position ?: ""
                    val (label, color) = getStatusInfo(user.status ?: "available")
                    binding.tvStatus.text = label
                    binding.tvStatus.setTextColor(Color.parseColor(color))
                    currentPhotoUrl = user.photo
                    if (!user.photo.isNullOrEmpty()) {
                        Glide.with(this@ProfileFragment)
                            .load(user.photo)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .circleCrop()
                            .into(binding.imgPhoto)
                    }
                }
            } catch (e: Exception) {
                try {
                    binding.tvName.text     = tokenManager.getName().first() ?: ""
                    binding.tvPosition.text = tokenManager.getPos().first() ?: ""
                } catch (ex: Exception) {}
            }
        }
    }

    private fun loadTeam() {
        if (_binding == null) return
        binding.progressEquipo.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.getUsers()
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    val users = response.body()!!.data ?: emptyList()
                    teamAdapter.updateMembers(users)
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) binding.progressEquipo.visibility = View.GONE
            }
        }
    }

    private fun showPhotoFullscreen(url: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image)
        dialog.setCancelable(true)
        val imgFull  = dialog.findViewById<ImageView>(R.id.imgFull)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
        Glide.with(requireContext()).load(url)
            .transition(DrawableTransitionOptions.withCrossFade()).into(imgFull)
        var isZoomed = false
        imgFull.setOnClickListener {
            isZoomed = !isZoomed
            imgFull.animate().scaleX(if (isZoomed) 2f else 1f)
                .scaleY(if (isZoomed) 2f else 1f).setDuration(200).start()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPhotoOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cambiar foto de perfil")
            .setItems(arrayOf("📷 Tomar foto", "🖼️ Elegir de galería")) { _, which ->
                when (which) {
                    0 -> checkCameraAndOpen()
                    1 -> openGallery()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkCameraAndOpen() {
        val perms = mutableListOf(Manifest.permission.CAMERA).also {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                it.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.all { ContextCompat.checkSelfPermission(requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED }) openCamera()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "SICE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_",
            ".jpg", requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.provider", photoFile)
        cameraLauncher.launch(cameraImageUri)
    }

    private fun openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            galleryLauncher.launch("image/*")
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) ==
                PackageManager.PERMISSION_GRANTED) galleryLauncher.launch("image/*")
            else permissionLauncher.launch(arrayOf(perm))
        }
    }

    private fun uploadPhoto(uri: Uri) {
        if (_binding == null) return
        showToastSafe("Subiendo foto...")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val token = ApiConfig.getToken()
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes == null) return@withContext Pair(false, "")
                    val tempFile = File(requireContext().cacheDir, "profile_upload.jpg")
                    tempFile.writeBytes(bytes)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS).build()
                    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("photo", "profile.jpg",
                            tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())).build()
                    val request = Request.Builder().url("${ApiConfig.BASE_URL}?path=users/photo")
                        .addHeader("X-Token", token).post(body).build()
                    val response = client.newCall(request).execute()
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Pair(json.optBoolean("ok"),
                        json.optJSONObject("data")?.optString("photo") ?: "")
                }
                if (_binding == null) return@launch
                if (result.first) {
                    currentPhotoUrl = result.second
                    showToastSafe("✅ Foto actualizada")
                    if (!result.second.isNullOrEmpty()) {
                        Glide.with(this@ProfileFragment).load(result.second)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .circleCrop().into(binding.imgPhoto)
                    }
                } else showToastSafe("Error al subir foto")
            } catch (e: Exception) { showToastSafe(getErrorMessage(e)) }
        }
    }

    private fun updateStatus(status: String) {
        if (_binding == null) return
        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.updateStatus(body = mapOf("status" to status))
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    val (label, color) = getStatusInfo(status)
                    binding.tvStatus.text = label
                    binding.tvStatus.setTextColor(Color.parseColor(color))
                    showToastSafe("Estado: $label")
                }
            } catch (e: Exception) { if (_binding != null) showToastSafe(getErrorMessage(e)) }
        }
    }

    private fun getStatusInfo(status: String): Pair<String, String> = when(status) {
        "available"  -> Pair("🟢 Disponible",          "#10b981")
        "deployed"   -> Pair("🔴 Desplegado",           "#ef4444")
        "off_duty"   -> Pair("🟡 Saliente de Servicio", "#f59e0b")
        "commission" -> Pair("🟣 En Comisión",          "#8b5cf6")
        else         -> Pair("🟢 Disponible",           "#10b981")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
