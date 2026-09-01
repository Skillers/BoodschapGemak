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
 * First-run configuration: where the API lives, the shared key from the
 * server's .env, and which of the two of you is holding this phone.
 */
@Composable
fun SetupScreen(vm: AppViewModel, canCancel: Boolean, onDone: () -> Unit) {
    var baseUrl by remember { mutableStateOf(vm.settings.baseUrl.ifEmpty { "http://desktop-ctplf50:4000" }) }
    var householdKey by remember { mutableStateOf(vm.settings.householdKey) }
    var userName by remember { mutableStateOf(vm.settings.userName) }

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
            "Vul in waar de server draait. Beide telefoons moeten hetzelfde adres " +
                "en dezelfde sleutel gebruiken.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Server-adres") },
            supportingText = { Text("Tailscale-adres van de PC - werkt thuis en in de winkel") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = householdKey,
            onValueChange = { householdKey = it },
            label = { Text("Huissleutel") },
            supportingText = { Text("De HOUSEHOLD_KEY uit server/.env") },
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
