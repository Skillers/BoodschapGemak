package nl.boodschapgemak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.boodschapgemak.data.AppViewModel

/**
 * First-run configuration. Only two things are asked for: the shared key and
 * which of the two of you is holding this phone. The server address already
 * defaults to this household's own machine, so it stays out of the way under
 * "Geavanceerd" until the day the server actually moves.
 */
@Composable
fun SetupScreen(vm: AppViewModel, canCancel: Boolean, onDone: () -> Unit) {
    var baseUrl by remember { mutableStateOf(vm.settings.baseUrl) }
    var householdKey by remember { mutableStateOf(vm.settings.householdKey) }
    var userName by remember { mutableStateOf(vm.settings.userName) }
    var showAdvanced by remember { mutableStateOf(false) }

    val canSave = baseUrl.isNotBlank() && householdKey.isNotBlank() && userName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("BoodschapGemak", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Vul de huissleutel in en je naam. Beide telefoons gebruiken " +
                "dezelfde sleutel.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = householdKey,
            onValueChange = { householdKey = it },
            label = { Text("Huissleutel") },
            supportingText = { Text("Dezelfde op beide telefoons") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("Jouw naam") },
            supportingText = { Text("Zo ziet de ander wie iets heeft afgevinkt") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showAdvanced) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Server-adres") },
                supportingText = { Text("Alleen wijzigen als de server verhuisd is") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showAdvanced = true }) { Text("Geavanceerd") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canSave,
                onClick = {
                    vm.saveSettings(baseUrl, householdKey, userName)
                    onDone()
                },
            ) { Text("Opslaan en verbinden") }

            if (canCancel) {
                TextButton(onClick = onDone) { Text("Annuleren") }
            }
        }
    }
}
