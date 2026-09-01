package nl.boodschapgemak.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.boodschapgemak.data.AppViewModel
import nl.boodschapgemak.data.DishIngredientBody
import nl.boodschapgemak.data.ShoppingItem
import nl.boodschapgemak.data.Trip

@Composable
fun ShoppingListScreen(vm: AppViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val me = vm.settings.userName

    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var addingDish by remember { mutableStateOf(false) }

    val todo = items.filterNot { it.isChecked }
    val done = items.filter { it.isChecked }

    Column(Modifier.fillMaxSize()) {
        RunningTotal(trip = trip, onAdd = vm::addAmount)
        AddItemRow(onAdd = vm::addItem)

        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp)) {
            TextButton(onClick = { addingDish = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Gerecht toevoegen", Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider()

        if (items.isEmpty()) {
            EmptyHint("De lijst is leeg. Typ hierboven wat er mee moet.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(todo, key = { "item-" + it.id }) { item ->
                    ShoppingRow(
                        item = item,
                        me = me,
                        onToggle = { vm.setChecked(item, true) },
                        onClaim = { vm.toggleClaim(item) },
                        onLongPress = { editing = item },
                    )
                    HorizontalDivider()
                }

                // Ticked items sit down here in their own block, out of the way
                // of what still has to be found. They leave when the trip closes.
                if (done.isNotEmpty()) {
                    item(key = "done-header") {
                        Text(
                            text = "In de kar (" + done.size + ")",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                        HorizontalDivider()
                    }
                    items(done, key = { "item-" + it.id }) { item ->
                        ShoppingRow(
                            item = item,
                            me = me,
                            onToggle = { vm.setChecked(item, false) },
                            onClaim = { vm.toggleClaim(item) },
                            onLongPress = { editing = item },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (addingDish) {
        DishDialog(
            onDismiss = { addingDish = false },
            onSave = { dish, ingredients ->
                vm.addDish(dish, ingredients)
                addingDish = false
            },
        )
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, quantity ->
                vm.editItem(item, name, quantity)
                editing = null
            },
            onDelete = {
                vm.deleteItem(item)
                editing = null
            },
        )
    }
}

/**
 * What this shop has cost so far, and the one field that changes it. Type an
 * amount, tap +, the number climbs. Deliberately not tied to list items - you
 * are copying what the shelf label said, not itemising a receipt.
 */
@Composable
private fun RunningTotal(trip: Trip?, onAdd: (Int, String) -> Unit) {
    var amount by remember { mutableStateOf("") }

    val cents = parseAmountToCents(amount)
    val valid = cents != null && cents != 0

    fun submit() {
        val value = cents ?: return
        onAdd(value, "")
        amount = ""
    }

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("Totaal", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatMoney(trip?.totalCents ?: 0),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Bedrag erbij") },
                    isError = amount.isNotBlank() && !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = ::submit, enabled = valid) {
                    Icon(Icons.Outlined.Add, contentDescription = "Bij het totaal optellen")
                }
            }
        }
    }
}

@Composable
private fun AddItemRow(onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    fun submit() {
        if (name.isBlank()) return
        onAdd(name, quantity)
        name = ""
        quantity = ""
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Aantal") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = ::submit, enabled = name.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = "Toevoegen")
        }
    }
}

/**
 * Name a dish and list what it needs. Every line lands on the shopping list as
 * an ordinary row carrying the dish name, so mid-shop you can see that the
 * pesto is there for the pasta and not on its own account.
 */
@Composable
private fun DishDialog(onDismiss: () -> Unit, onSave: (String, List<DishIngredientBody>) -> Unit) {
    var dish by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf("" to "")) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Gerecht toevoegen", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = dish,
                    onValueChange = { dish = it },
                    label = { Text("Gerecht") },
                    supportingText = { Text("Bijvoorbeeld: pasta pesto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Ingredienten", style = MaterialTheme.typography.titleMedium)

                lines.forEachIndexed { index, line ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = line.first,
                            onValueChange = { newName ->
                                lines = lines.toMutableList().also { it[index] = newName to line.second }
                            },
                            label = { Text("Ingredient") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = line.second,
                            onValueChange = { newAmount ->
                                lines = lines.toMutableList().also { it[index] = line.first to newAmount }
                            },
                            label = { Text("Aantal") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { lines = lines.filterIndexed { i, _ -> i != index } },
                            enabled = lines.size > 1,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Regel verwijderen")
                        }
                    }
                }

                TextButton(onClick = { lines = lines + ("" to "") }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Regel toevoegen", Modifier.padding(start = 8.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuleren") }
                    Button(
                        enabled = dish.isNotBlank() && lines.any { it.first.isNotBlank() },
                        onClick = {
                            val ingredients = lines
                                .filter { it.first.isNotBlank() }
                                .map { DishIngredientBody(it.first.trim(), it.second.trim().ifEmpty { null }) }
                            onSave(dish, ingredients)
                        },
                    ) { Text("Op de lijst zetten") }
                }
            }
        }
    }
}

/**
 * Tap the row to tick something into the cart, tap the chip to say you are on
 * your way to it. The claim is the part that stops you both walking to the
 * same shelf, so it gets the colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    me: String,
    onToggle: () -> Unit,
    onClaim: () -> Unit,
    onLongPress: () -> Unit,
) {
    val claimedBy = item.claimedBy?.takeIf { it.isNotBlank() && !item.isChecked }
    val claimedByMe = claimedBy == me

    val container = when {
        claimedBy == null -> ListItemDefaults.containerColor
        claimedByMe -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onToggle, onLongClick = onLongPress),
        colors = ListItemDefaults.colors(containerColor = container),
        leadingContent = {
            Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })
        },
        headlineContent = {
            Text(
                text = item.name,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            val note = listOfNotNull(
                item.quantity?.takeIf { it.isNotBlank() },
                item.dish?.takeIf { it.isNotBlank() }?.let { "voor $it" },
                when {
                    item.isChecked -> item.checkedBy?.takeIf { it.isNotBlank() }?.let { "in de kar door $it" }
                    claimedByMe -> "jij loopt ernaartoe"
                    claimedBy != null -> "$claimedBy loopt ernaartoe"
                    else -> null
                },
            ).joinToString(" - ")
            if (note.isNotEmpty()) Text(note)
        },
        trailingContent = {
            if (!item.isChecked) {
                ClaimChip(claimedBy = claimedBy, claimedByMe = claimedByMe, onClick = onClaim)
            }
        },
    )
}

@Composable
private fun ClaimChip(claimedBy: String?, claimedByMe: Boolean, onClick: () -> Unit) {
    val label = when {
        claimedByMe -> "Jij"
        claimedBy != null -> claimedBy
        else -> "Pak ik"
    }

    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = if (claimedBy == null) {
            AssistChipDefaults.assistChipColors()
        } else {
            AssistChipDefaults.assistChipColors(
                containerColor = if (claimedByMe) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                labelColor = if (claimedByMe) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onTertiary
                },
            )
        },
    )
}

@Composable
private fun EditItemDialog(
    item: ShoppingItem,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Product aanpassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Aantal") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, quantity) }) {
                Text("Opslaan")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Verwijderen") }
                TextButton(onClick = onDismiss) { Text("Annuleren") }
            }
        },
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}
