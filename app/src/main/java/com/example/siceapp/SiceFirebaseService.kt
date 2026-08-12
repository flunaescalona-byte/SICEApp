package com.example.siceapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.siceapp.ui.main.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SiceFirebaseService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "sice_alerts"
        const val CHANNEL_NAME = "SICE Alertas"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save FCM token to send to server
        getSharedPreferences("sice_fcm", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()

        // Send to server in background
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        Thread {
            try {
                val apiToken = getSharedPreferences("sice_prefs", Context.MODE_PRIVATE)
                    .getString("api_token", null) ?: return@Thread

                val url = java.net.URL("https://calendario.fernandolunatech.cl/api/v1/?path=users/fcm_token")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Token", apiToken)
                conn.doOutput = true
                conn.outputStream.write("""{"fcm_token":"$token"}""".toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {}
        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Check if notifications are enabled in settings
        val prefs = getSharedPreferences("sice_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_push", true)) return

        val title     = message.notification?.title ?: message.data["title"] ?: "SICE"
        val body      = message.notification?.body  ?: message.data["body"]  ?: ""
        val taskId    = message.data["task_id"]?.toIntOrNull()
        val taskTitle = message.data["task_title"]

        createChannel()
        showNotification(title, body, taskId, taskTitle,
            soundEnabled     = prefs.getBoolean("notif_sound", true),
            vibrationEnabled = prefs.getBoolean("notif_vibration", true)
        )
    }

    private fun showNotification(title: String, body: String, taskId: Int?, taskTitle: String?,
        soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("task_id",    taskId ?: 0)
            putExtra("task_title", taskTitle ?: "")
            putExtra("from_notification", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            taskId ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (vibrationEnabled) builder.setVibrate(longArrayOf(0, 400, 100, 400))
        else builder.setVibrate(longArrayOf(0))

        if (!soundEnabled) {
            builder.setSilent(true)
        } else {
            // Use selected ringtone
            val prefs = getSharedPreferences("sice_settings", Context.MODE_PRIVATE)
            val ringtoneUri = prefs.getString("notif_ringtone", null)?.let {
                android.net.Uri.parse(it)
            } ?: android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(ringtoneUri)
        }

        taskTitle?.let { builder.setSubText(it) }

        val notification = builder.build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskId ?: System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de tareas SICE"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 100, 400)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400), -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 400, 100, 400), -1)
                }
            }
        } catch (e: Exception) {}
    }
}
