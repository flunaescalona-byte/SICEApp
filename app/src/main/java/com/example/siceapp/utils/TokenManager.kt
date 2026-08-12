package com.example.siceapp.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "sice_prefs")

class TokenManager(private val context: Context) {

    companion object {
        val TOKEN_KEY    = stringPreferencesKey("token")
        val USER_NAME    = stringPreferencesKey("user_name")
        val USER_EMAIL   = stringPreferencesKey("user_email")
        val USER_ROLE    = stringPreferencesKey("user_role")
        val USER_PHOTO   = stringPreferencesKey("user_photo")
        val USER_POS     = stringPreferencesKey("user_position")
        val USER_STATUS  = stringPreferencesKey("user_status")
        val USER_ID      = stringPreferencesKey("user_id")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }

    suspend fun saveSession(
        token: String,
        id: Int,
        name: String,
        email: String,
        role: String,
        photo: String?,
        position: String?,
        status: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY]   = token
            prefs[USER_ID]     = id.toString()
            prefs[USER_NAME]   = name
            prefs[USER_EMAIL]  = email
            prefs[USER_ROLE]   = role
            prefs[USER_PHOTO]  = photo ?: ""
            prefs[USER_POS]    = position ?: ""
            prefs[USER_STATUS] = status ?: "available"
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    fun getName():   Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    fun getRole():   Flow<String?> = context.dataStore.data.map { it[USER_ROLE] }
    fun getPhoto():  Flow<String?> = context.dataStore.data.map { it[USER_PHOTO] }
    fun getStatus(): Flow<String?> = context.dataStore.data.map { it[USER_STATUS] }
    fun getId():     Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    fun getPos():    Flow<String?> = context.dataStore.data.map { it[USER_POS] }
}