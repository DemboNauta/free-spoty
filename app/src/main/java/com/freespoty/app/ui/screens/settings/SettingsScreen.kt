package com.freespoty.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freespoty.app.ui.rememberAppContainer

private const val PIN_LENGTH = 4

// Tracks which PIN dialog is currently shown
private sealed interface PinStep {
    data object None : PinStep
    // Ask user to enter existing PIN; onVerified is called when correct
    data class VerifyCurrent(val subtitle: String, val onVerified: () -> Unit) : PinStep
    // Ask user to enter a new PIN (first step)
    data class EnterNew(val onNext: (String) -> Unit) : PinStep
    // Ask user to confirm the new PIN entered in EnterNew
    data class ConfirmNew(val first: String, val onConfirmed: (String) -> Unit) : PinStep
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = rememberAppContainer()
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(container.appPreferences)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var pinStep by remember { mutableStateOf<PinStep>(PinStep.None) }

    fun requestVerify(subtitle: String, onVerified: () -> Unit) {
        pinStep = PinStep.VerifyCurrent(subtitle, onVerified)
    }

    fun startSetPin(onPinSaved: () -> Unit) {
        pinStep = PinStep.EnterNew { first ->
            pinStep = PinStep.ConfirmNew(first) { confirmed ->
                vm.setPin(confirmed)
                onPinSaved()
                pinStep = PinStep.None
            }
        }
    }

    fun onToggleKidsMode() {
        if (!state.kidsMode) {
            if (state.pinHash.isEmpty()) {
                startSetPin { vm.setKidsMode(true) }
            } else {
                requestVerify("Introduce el PIN para activar el modo niños") {
                    vm.setKidsMode(true)
                    pinStep = PinStep.None
                }
            }
        } else {
            requestVerify("Introduce el PIN para desactivar el modo niños") {
                vm.setKidsMode(false)
                pinStep = PinStep.None
            }
        }
    }

    fun onChangePinClick() {
        requestVerify("Introduce el PIN actual") {
            startSetPin {}
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Kids mode row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChildCare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modo Niños", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Filtra contenido maduro (YouTube Restricted Mode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.kidsMode, onCheckedChange = { onToggleKidsMode() })
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Change PIN row (only visible while kids mode is active) ────
            if (state.kidsMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cambiar PIN", style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { onChangePinClick() }) {
                        Text("Cambiar")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    // ── PIN dialogs ────────────────────────────────────────────────────────
    when (val step = pinStep) {
        is PinStep.None -> Unit

        is PinStep.VerifyCurrent -> VerifyPinDialog(
            subtitle = step.subtitle,
            storedHash = state.pinHash,
            onVerify = vm::verifyPin,
            onVerified = step.onVerified,
            onDismiss = { pinStep = PinStep.None }
        )

        is PinStep.EnterNew -> EnterNewPinDialog(
            title = "Crear PIN",
            subtitle = "Elige un PIN de $PIN_LENGTH dígitos",
            onNext = step.onNext,
            onDismiss = { pinStep = PinStep.None }
        )

        is PinStep.ConfirmNew -> EnterNewPinDialog(
            title = "Confirmar PIN",
            subtitle = "Repite el PIN para confirmar",
            expectedPin = step.first,
            onNext = step.onConfirmed,
            onDismiss = { pinStep = PinStep.None }
        )
    }
}

@Composable
private fun VerifyPinDialog(
    subtitle: String,
    storedHash: String,
    onVerify: (String, String) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN de seguridad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= PIN_LENGTH) {
                            pin = v
                            error = false
                        }
                    },
                    label = { Text("PIN") },
                    isError = error,
                    supportingText = if (error) ({ Text("PIN incorrecto") }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == PIN_LENGTH,
                onClick = {
                    if (onVerify(pin, storedHash)) onVerified() else error = true
                }
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun EnterNewPinDialog(
    title: String,
    subtitle: String,
    expectedPin: String? = null,
    onNext: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= PIN_LENGTH) {
                            pin = v
                            error = false
                        }
                    },
                    label = { Text("PIN") },
                    isError = error,
                    supportingText = if (error) ({ Text("Los PINs no coinciden") }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == PIN_LENGTH,
                onClick = {
                    if (expectedPin != null && pin != expectedPin) {
                        error = true
                    } else {
                        onNext(pin)
                    }
                }
            ) { Text(if (expectedPin != null) "Confirmar" else "Siguiente") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
