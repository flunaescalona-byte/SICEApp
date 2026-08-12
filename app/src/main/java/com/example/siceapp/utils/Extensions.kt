package com.example.siceapp.utils

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Safe toast that won't crash if fragment is detached
 */
fun Fragment.showToastSafe(msg: String) {
    if (isAdded && context != null) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Get user-friendly error message from exception
 */
fun getErrorMessage(e: Exception): String {
    return when (e) {
        is UnknownHostException    -> "Sin conexión a internet"
        is SocketTimeoutException  -> "El servidor tardó demasiado, intenta de nuevo"
        is java.net.ConnectException -> "No se pudo conectar al servidor"
        else -> "Error de conexión"
    }
}
