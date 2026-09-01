package nl.boodschapgemak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.boodschapgemak.data.AppViewModel
import nl.boodschapgemak.data.Trip

/**
 * The free-form running total: type an amount, tap +, watch the number climb.
 * Nothing here is tied to a shopping-list item on purpose.
 */
@Composable
fun TotalScreen(vm: AppViewModel) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    val current = trip
    if (current == null) {
        EmptyHint("Bezig met laden...")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { TotalCard(trip = current, onRenameClick = { renaming = true }) }

        item { AddAmountRow(onAdd = vm::addAmount) }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { closing = true },
                    enabled = current.entries.isNotEmpty(),
                ) { Text("Boodschappen afsluiten") }
            }
        }

        if (current.entries.isEmpty()) {
            item {
                Text(
                    text = "Nog niets bijgeteld.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        items(current.entries, key = { "entry-" + it.id }) { entry ->
            ListItem(
                headlineContent = { Text(formatMoney(entry.amountCents)) },
                supportingContent = {
                    val note = listOfNotNull(
                        entry.note?.takeIf { it.isNotBlank() },
                    ).joinToString(" - ")
                    if (note.isNotEmpty()) Text(note)
                },
                trailingContent = {
                    IconButton(onClick = { vm.deleteEntry(entry) }) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Ongedaan maken")
                    }
                },
            )
            HorizontalDivider()
        }

        val closed = history.filter { it.status == "closed" }
        if (closed.isNotEmpty()) {
            item {
                Text(
                    text = "Eerdere boodschappen",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(closed, key = { "trip-" + it.id }) { past ->
                ListItem(
                    headlineContent = { Text(past.label) },
                    supportingContent = { Text(past.entryCount.toString() + " keer bijgeteld") },
                    trailingContent = {
                        Text(formatMoney(past.totalCents), style = MaterialTheme.typography.titleMedium)
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (renaming) {
        RenameDialog(
            initial = current.label,
            onDismiss = { renaming = false },
            onSave = { newLabel ->
                vm.renameTrip(newLabel)
                renaming = false
            },
        )
    }

    if (closing) {
        AlertDialog(
            onDismissRequest = { closing = false },
            title = { Text("Afsluiten?") },
            text = {
                Text(
                    "Het totaal van " + formatMoney(current.totalCents) +
                        " wordt bewaard en de teller begint opnieuw."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.closeTrip()
                    closing = false
                }) { Text("Afsluiten") }
            },
            dismissButton = { TextButton(onClick = { closing = false }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun TotalCard(trip: Trip, onRenameClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = trip.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onRenameClick).padding(4.dp),
            )
            Text(
                text = formatMoney(trip.totalCents),
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

@Composable
private fun AddAmountRow(onAdd: (Int, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val cents = parseAmountToCents(amount)
    val valid = cents != null && cents != 0

    fun submit() {
        val value = cents ?: return
        onAdd(value, note)
        amount = ""
        note = ""
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Bedrag") },
            isError = amount.isNotBlank() && !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.2f),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notitie") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = ::submit, enabled = valid) {
            Icon(Icons.Outlined.Add, contentDescription = "Bij het totaal optellen")
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naam van deze boodschappen") },
        text = {
            OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true)
        },
        confirmButton = {
            Button(enabled = label.isNotBlank(), onClick = { onSave(label) }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
