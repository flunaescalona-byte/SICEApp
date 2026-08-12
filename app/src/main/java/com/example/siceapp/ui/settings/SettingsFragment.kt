package com.example.siceapp.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.app.DownloadManager
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.siceapp.UpdateChecker
import com.example.siceapp.databinding.FragmentSettingsBinding
import com.example.siceapp.utils.showToastSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val PREFS = "sice_settings"
    private var latestApkUrl = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Current version
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) { "1.0" }
        binding.tvCurrentVersion.text = "v$versionName (código ${UpdateChecker.CURRENT_VERSION_CODE})"

        // Dark mode removed - app always uses dark theme

        // Notification switches
        // Notificaciones push
        binding.switchNotifications.isChecked = prefs.getBoolean("notif_push", true)
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_push", checked).apply()
            // Enable/disable sound and vibration switches based on push toggle
            binding.switchSound.isEnabled     = checked
            binding.switchVibration.isEnabled = checked
            if (checked) showToastSafe("✅ Notificaciones activadas")
            else showToastSafe("🔕 Notificaciones desactivadas")
        }

        // Sound + ringtone picker
        binding.switchSound.isChecked  = prefs.getBoolean("notif_sound", true)
        binding.switchSound.isEnabled  = prefs.getBoolean("notif_push", true)
        binding.switchSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_sound", checked).apply()
            if (checked) {
                showToastSafe("🔊 Sonido activado")
                openRingtonePicker()
            } else showToastSafe("🔇 Sonido desactivado")
        }

        // Vibration
        binding.switchVibration.isChecked  = prefs.getBoolean("notif_vibration", true)
        binding.switchVibration.isEnabled  = prefs.getBoolean("notif_push", true)
        binding.switchVibration.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_vibration", checked).apply()
            if (checked) {
                showToastSafe("📳 Vibración activada")
                // Test vibration
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val vm = requireContext().getSystemService(android.os.VibratorManager::class.java)
                        vm?.defaultVibrator?.vibrate(
                            android.os.VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        val v = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE)
                                as android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
                        }
                    }
                } catch (e: Exception) {}
            } else showToastSafe("📵 Vibración desactivada")
        }

        // Download path - SICE folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS)
        val siceDir = java.io.File(downloadsDir, "SICE")
        if (!siceDir.exists()) siceDir.mkdirs()
        binding.tvDownloadPath.text = siceDir.absolutePath

        binding.btnOpenDownloads.setOnClickListener {
            try {
                // Try to open Downloads app
                val intent = Intent("android.intent.action.VIEW_DOWNLOADS")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    // Fallback: open file manager
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivity(Intent.createChooser(intent, "Abrir carpeta SICE"))
                } catch (ex: Exception) {
                    showToastSafe("📁 Archivos guardados en: Descargas/SICE")
                }
            }
        }

        // Check for updates
        binding.btnCheckUpdate.setOnClickListener { checkForUpdates() }

        binding.btnDownloadUpdate.setOnClickListener {
            if (latestApkUrl.isNotEmpty()) {
                downloadUpdate(latestApkUrl)
            }
        }
    }

    private fun checkForUpdates() {
        if (_binding == null) return
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "🔍 Buscando..."
        binding.tvUpToDate.visibility = View.GONE
        binding.rowNewVersion.visibility = View.GONE
        binding.btnDownloadUpdate.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    JSONObject(URL("https://calendario.fernandolunatech.cl/app/version.json").readText())
                }
                if (_binding == null) return@launch

                val latestCode = json.getInt("version_code")
                val latestName = json.getString("version_name")
                val changelog  = json.optString("changelog", "")
                latestApkUrl   = json.getString("apk_url")

                if (latestCode > UpdateChecker.CURRENT_VERSION_CODE) {
                    // New version available
                    binding.tvNewVersion.text = "v$latestName"
                    binding.rowNewVersion.visibility = View.VISIBLE
                    binding.btnDownloadUpdate.visibility = View.VISIBLE
                    binding.btnDownloadUpdate.text = "⬇️ Actualizar a v$latestName"
                    if (changelog.isNotEmpty()) {
                        showToastSafe("Novedades: $changelog")
                    }
                } else {
                    binding.tvUpToDate.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (_binding != null) showToastSafe("Error al buscar actualización")
            } finally {
                if (_binding != null) {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "🔍 Buscar actualización"
                }
            }
        }
    }

    private fun downloadUpdate(apkUrl: String) {
        if (_binding == null) return
        binding.btnDownloadUpdate.isEnabled = false
        binding.btnDownloadUpdate.text = "⏳ Descargando..."

        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    // Use app cache dir - always accessible via FileProvider
                    val dest = File(requireContext().cacheDir, "sice_update.apk")
                    if (dest.exists()) dest.delete()
                    URL(apkUrl).openStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
                if (_binding == null) return@launch
                showToastSafe("✅ Descargado. Instalando...")
                installApk(file)
            } catch (e: Exception) {
                if (_binding != null) {
                    showToastSafe("Error al descargar: ${e.message}")
                    binding.btnDownloadUpdate.isEnabled = true
                    binding.btnDownloadUpdate.text = "⬇️ Reintentar descarga"
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            showToastSafe("Error al instalar: ${e.message}")
        }
    }

    private val ringtoneLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(
                android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                requireContext().getSharedPreferences("sice_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putString("notif_ringtone", uri.toString()).apply()
                val ringtone = android.media.RingtoneManager.getRingtone(requireContext(), uri)
                showToastSafe("🔔 Sonido: ${ringtone.getTitle(requireContext())}")
            }
        }
    }

    private fun openRingtonePicker() {
        val prefs = requireContext().getSharedPreferences("sice_settings", android.content.Context.MODE_PRIVATE)
        val currentUri = prefs.getString("notif_ringtone", null)?.let {
            android.net.Uri.parse(it)
        } ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val intent = android.media.RingtoneManager.ACTION_RINGTONE_PICKER.let {
            android.content.Intent(it).apply {
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                    android.media.RingtoneManager.TYPE_NOTIFICATION)
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de notificación SICE")
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            }
        }
        ringtoneLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
