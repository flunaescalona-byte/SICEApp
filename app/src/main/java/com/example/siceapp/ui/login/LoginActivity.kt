package com.example.siceapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.ActivityLoginBinding
import com.example.siceapp.model.LoginRequest
import com.example.siceapp.ui.main.MainActivity
import com.example.siceapp.utils.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        // Check if already logged in
        lifecycleScope.launch {
            val token = tokenManager.token.first()
            if (!token.isNullOrEmpty()) {
                ApiConfig.setToken(token)
                goToMain()
            }
        }

        // Toggle password visibility
        binding.btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            binding.etPassword.transformationMethod = if (passwordVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        // Login button
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                showError("Ingresa tu correo y contraseña")
                return@setOnClickListener
            }

            doLogin(email, password)
        }
    }

    private fun doLogin(email: String, password: String) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val response = ApiConfig.service.login(
                    body = LoginRequest(email, password)
                )
                if (response.isSuccessful && response.body()?.ok == true) {
                    val data = response.body()!!.data!!
                    val user = data.user

                    // Save token and user info
                    ApiConfig.setToken(data.token)
                    tokenManager.saveSession(
                        token    = data.token,
                        id       = user.id,
                        name     = user.name,
                        email    = user.email,
                        role     = user.role,
                        photo    = user.photo,
                        position = user.position,
                        status   = user.status
                    )

                    goToMain()
                } else {
                    val errorMsg = response.body()?.error ?: "Credenciales incorrectas"
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                showError("Error de conexión. Verifica tu internet.")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }
}