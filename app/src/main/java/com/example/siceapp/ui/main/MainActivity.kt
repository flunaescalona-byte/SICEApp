package com.example.siceapp.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.ActivityMainBinding
import com.example.siceapp.ui.calendar.CalendarFragment
import com.example.siceapp.ui.history.HistoryFragment
import com.example.siceapp.ui.login.LoginActivity
import com.example.siceapp.ui.notifications.NotificationsFragment
import com.example.siceapp.ui.profile.ProfileFragment
import com.example.siceapp.ui.tasks.TasksFragment
import com.example.siceapp.utils.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.siceapp.ui.tasks.TaskDetailFragment
import com.example.siceapp.UpdateChecker
import com.example.siceapp.ui.settings.SettingsFragment
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply saved theme
        val prefs = getSharedPreferences("sice_settings", android.content.Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Request notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        tokenManager = TokenManager(this)

        lifecycleScope.launch {
            val token = tokenManager.token.first()
            if (token.isNullOrEmpty()) { goToLogin(); return@launch }
            ApiConfig.setToken(token)
            registerFcmToken()
            UpdateChecker.check(this@MainActivity)

            // Handle notification tap AFTER token is loaded
            val fromNotif = intent?.getBooleanExtra("from_notification", false) ?: false
            val taskId    = intent?.getIntExtra("task_id", 0) ?: 0
            val taskTitle = intent?.getStringExtra("task_title") ?: ""
            if (fromNotif && taskId > 0) {
                binding.bottomNav.selectedItemId = R.id.nav_notifications
                loadFragment(TaskDetailFragment.newInstance(taskId, taskTitle))
            }
        }

        // Default fragment — only if NOT from notification
        if (savedInstanceState == null) {
            val fromNotif = intent?.getBooleanExtra("from_notification", false) ?: false
            if (!fromNotif) loadFragment(CalendarFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_calendar      -> loadFragment(CalendarFragment())
                R.id.nav_tasks         -> loadFragment(TasksFragment())
                R.id.nav_notifications -> {
                    // Clear badge when opening notifications
                    clearNotificationBadge()
                    loadFragment(NotificationsFragment())
                }
                R.id.nav_profile       -> loadFragment(ProfileFragment())
                R.id.nav_settings      -> loadFragment(SettingsFragment())
            }
            true
        }
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                lifecycleScope.launch {
                    try {
                        ApiConfig.service.updateFcmToken(body = mapOf("fcm_token" to token))
                    } catch (e: Exception) {}
                }
            }
    }

    fun updateNotificationBadge(count: Int) {
        runOnUiThread {
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_notifications)
            if (count > 0) {
                badge.isVisible = true
                badge.number   = count
                badge.backgroundColor = 0xFFef4444.toInt()
                badge.badgeTextColor  = 0xFFFFFFFF.toInt()
            } else {
                badge.isVisible = false
            }
        }
    }

    fun clearNotificationBadge() {
        binding.bottomNav.removeBadge(R.id.nav_notifications)
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment, addToBackStack: Boolean = false) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val fromNotif = intent?.getBooleanExtra("from_notification", false) ?: false
        val taskId    = intent?.getIntExtra("task_id", 0) ?: 0
        val taskTitle = intent?.getStringExtra("task_title") ?: ""
        if (fromNotif && taskId > 0) {
            binding.bottomNav.selectedItemId = R.id.nav_notifications
            loadFragment(TaskDetailFragment.newInstance(taskId, taskTitle))
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: update the intent
        handleNotificationIntent(intent)
    }

    fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    fun logout() {
        lifecycleScope.launch {
            tokenManager.clearSession()
            ApiConfig.setToken("")
            goToLogin()
        }
    }
}
