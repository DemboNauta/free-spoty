package com.freespoty.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val kidsMode: Boolean = false,
    val pinHash: String = ""
)

class SettingsViewModel(private val prefs: AppPreferences) : ViewModel() {

    val uiState = combine(prefs.kidsModeFlow, prefs.kidsPinHashFlow) { kidsMode, pinHash ->
        SettingsUiState(kidsMode = kidsMode, pinHash = pinHash)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun verifyPin(pin: String, storedHash: String) = prefs.verifyPin(pin, storedHash)

    fun setPin(pin: String) {
        viewModelScope.launch { prefs.setPinHash(pin) }
    }

    fun setKidsMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setKidsMode(enabled) }
    }

    class Factory(private val prefs: AppPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(prefs) as T
    }
}
