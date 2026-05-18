package com.freespoty.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    private val kidsModePref = booleanPreferencesKey("kids_mode")
    private val kidsPinHashPref = stringPreferencesKey("kids_pin_hash")

    val kidsModeFlow: Flow<Boolean> = context.dataStore.data.map { it[kidsModePref] ?: false }
    val kidsPinHashFlow: Flow<String> = context.dataStore.data.map { it[kidsPinHashPref] ?: "" }

    suspend fun setKidsMode(enabled: Boolean) {
        context.dataStore.edit { it[kidsModePref] = enabled }
    }

    suspend fun setPinHash(pin: String) {
        context.dataStore.edit { it[kidsPinHashPref] = sha256(pin) }
    }

    fun verifyPin(pin: String, storedHash: String): Boolean = sha256(pin) == storedHash

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
