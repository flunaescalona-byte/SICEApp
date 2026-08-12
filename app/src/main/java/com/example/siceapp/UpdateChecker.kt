package com.example.siceapp

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat // Importación asegurada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

object UpdateChecker {

    private const val VERSION_URL = "https://calendario.fernandolunatech.cl/version.json"
    const val CURRENT_VERSION_CODE = 6 // Incrementa esto en cada release

    suspend fun check(context: Context) {
        try {
            val json = withContext(Dispatchers.IO) {
                JSONObject(URL(VERSION_URL).readText())
            }

            val latestCode = json.getInt("version_code")
            val latestName = json.getString("version_name")
            val apkUrl     = json.getString("apk_url")
            val changelog  = json.getString("changelog")
            val forceUpdate = json.optBoolean("force_update", false)

            android.util.Log.d("SICE_UPDATE", "Latest: $latestCode, Current: $CURRENT_VERSION_CODE")

            if (latestCode > CURRENT_VERSION_CODE) {
                android.util.Log.d("SICE_UPDATE", "Update available! Showing dialog...")
                showUpdateDialog(context, latestName, apkUrl, changelog, forceUpdate)
            } else {
                android.util.Log.d("SICE_UPDATE", "No update needed")
            }
        } catch (e: Exception) {
            android.util.Log.e("SICE_UPDATE", "Error: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    private fun showUpdateDialog(
        context: Context, version: String, apkUrl: String,
        changelog: String, forceUpdate: Boolean
    ) {
        val message = "Versión $version disponible\n\n$changelog"

        val builder = AlertDialog.Builder(context)
            .setTitle("🆕 Actualización disponible")
            .setMessage(message)
            .setPositiveButton("Descargar e instalar") { _, _ ->
                downloadAndInstall(context, apkUrl)
            }

        if (!forceUpdate) {
            builder.setNegativeButton("Más tarde", null)
        }

        builder.setCancelable(!forceUpdate)
        builder.show()
    }

    private fun downloadAndInstall(context: Context, apkUrl: String) {
        val fileName = "sice_update.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Descargando actualización SICE")
            setDescription("Por favor espera...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(file))
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
            )
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Listen for download complete
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    installApk(context, file)
                }
            }
        }

        // CORRECCIÓN SEGURO DE BROADCAST: Usar ContextCompat para manejar los flags de Android 14+ de forma correcta
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED // IMPORTANTE: Cambiado a EXPORTED para que escuche al DownloadManager del sistema
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}