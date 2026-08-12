package com.example.siceapp.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.FragmentEditProfileBinding
import com.example.siceapp.utils.getErrorMessage
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.Dispatchers
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

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private var userId = 0
    private var userName = ""
    private var cameraImageUri: Uri? = null
    private var currentPhotoUrl: String? = null

    companion object {
        fun newInstance(userId: Int, userName: String): EditProfileFragment {
            val f = EditProfileFragment()
            f.arguments = Bundle().apply {
                putInt("user_id", userId)
                putString("user_name", userName)
            }
            return f
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success && cameraImageUri != null) uploadPhoto(cameraImageUri!!) }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { uploadPhoto(it) } }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> if (perms.values.all { it }) openCamera() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId   = arguments?.getInt("user_id") ?: 0
        userName = arguments?.getString("user_name") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = if (userId == 0) "✏️ Mi Perfil" else "✏️ Editar: $userName"
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.imgPhoto.setOnClickListener { showPhotoOptions() }
        binding.btnChangePhoto.setOnClickListener { showPhotoOptions() }

        loadProfile()
    }

    private fun loadProfile() {
        if (_binding == null) return
        lifecycleScope.launch {
            try {
                val path = if (userId == 0) "users/me" else "users/$userId"
                // Use getMe for own profile, getUserById for others
                val response = if (userId == 0)
                    ApiConfig.service.getMe()
                else
                    ApiConfig.service.getUserById(path = "users/$userId")

                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    val u = response.body()!!.data!!
                    binding.etName.setText(u.name)
                    binding.etPosition.setText(u.position ?: "")
                    binding.etBio.setText(u.bio ?: "")
                    currentPhotoUrl = u.photo
                    if (!u.photo.isNullOrEmpty()) {
                        Glide.with(this@EditProfileFragment)
                            .load(u.photo).circleCrop().into(binding.imgPhoto)
                    }
                } else {
                    showToastSafe("Error cargando perfil: ${response.body()?.error}")
                }
            } catch (e: Exception) {
                showToastSafe(getErrorMessage(e))
            }
        }
    }

    private fun saveProfile() {
        val name     = binding.etName.text.toString().trim()
        val position = binding.etPosition.text.toString().trim()
        val bio      = binding.etBio.text.toString().trim()

        if (name.isEmpty()) {
            showToastSafe("El nombre es requerido")
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Guardando..."

        lifecycleScope.launch {
            try {
                val path = if (userId == 0) "users/me" else "users/$userId"
                val response = ApiConfig.service.updateProfile(
                    path = path,
                    body = mapOf("name" to name, "position" to position, "bio" to bio)
                )
                if (_binding == null) return@launch
                if (response.isSuccessful && response.body()?.ok == true) {
                    showToastSafe("✅ Perfil actualizado")
                    parentFragmentManager.popBackStack()
                } else {
                    showToastSafe("Error al guardar")
                }
            } catch (e: Exception) {
                showToastSafe(getErrorMessage(e))
            } finally {
                if (_binding != null) {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Guardar"
                }
            }
        }
    }

    private fun showPhotoOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cambiar foto")
            .setItems(arrayOf("📷 Tomar foto", "🖼️ Elegir de galería")) { _, which ->
                when (which) {
                    0 -> {
                        val perms = mutableListOf(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (perms.all { ContextCompat.checkSelfPermission(requireContext(), it) ==
                                PackageManager.PERMISSION_GRANTED }) openCamera()
                        else permissionLauncher.launch(perms.toTypedArray())
                    }
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "SICE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_",
            ".jpg", requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.provider", photoFile)
        cameraLauncher.launch(cameraImageUri)
    }

    private fun uploadPhoto(uri: Uri) {
        if (_binding == null) return
        showToastSafe("Subiendo foto...")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val token = ApiConfig.getToken()
                    val photoPath = if (userId == 0) "users/photo" else "users/$userId/photo"
                    val url = "${ApiConfig.BASE_URL}?path=$photoPath"
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
                    val request = Request.Builder().url(url)
                        .addHeader("X-Token", token).post(body).build()
                    val response = client.newCall(request).execute()
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Pair(json.optBoolean("ok"),
                        json.optJSONObject("data")?.optString("photo") ?: "")
                }
                if (_binding == null) return@launch
                if (result.first) {
                    showToastSafe("✅ Foto actualizada")
                    currentPhotoUrl = result.second
                    if (!result.second.isNullOrEmpty()) {
                        Glide.with(this@EditProfileFragment).load(result.second)
                            .circleCrop().into(binding.imgPhoto)
                    }
                } else showToastSafe("Error al subir foto")
            } catch (e: Exception) {
                showToastSafe(getErrorMessage(e))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
