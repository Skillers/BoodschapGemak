package nl.boodschapgemak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.boodschapgemak.data.AppViewModel
import nl.boodschapgemak.data.IngredientBody
import nl.boodschapgemak.data.Recipe

/**
 * Recipes for the week. Each card opens and closes so the ingredient list is
 * out of the way until you want it.
 */
@Composable
fun RecipesScreen(vm: AppViewModel) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()

    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    var editing by remember { mutableStateOf<Recipe?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Recipe?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Recept toevoegen", Modifier.padding(start = 8.dp))
            }
        }

        if (recipes.isEmpty()) {
            EmptyHint("Nog geen recepten. Voeg er een toe voor deze week.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    isOpen = expanded[recipe.id] == true,
                    onToggle = { expanded[recipe.id] = expanded[recipe.id] != true },
                    onEdit = { editing = recipe },
                    onDelete = { deleting = recipe },
                )
            }
        }
    }

    if (creating) {
        RecipeEditor(
            initial = null,
            onDismiss = { creating = false },
            onSave = { title, notes, ingredients ->
                vm.saveRecipe(0, title, notes, ingredients)
                creating = false
            },
        )
    }

    editing?.let { recipe ->
        RecipeEditor(
            initial = recipe,
            onDismiss = { editing = null },
            onSave = { title, notes, ingredients ->
                vm.saveRecipe(recipe.id, title, notes, ingredients)
                editing = null
            },
        )
    }

    deleting?.let { recipe ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Recept verwijderen?") },
            text = { Text(recipe.title + " wordt met alle ingredienten verwijderd.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecipe(recipe)
                    deleting = null
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = recipe.ingredients.size.toString() + " ingredienten",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Icon(
                imageVector = if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (isOpen) "Inklappen" else "Uitklappen",
            )
        }

        AnimatedVisibility(visible = isOpen) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                if (recipe.ingredients.isEmpty()) {
                    Text(
                        text = "Nog geen ingredienten toegevoegd.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                recipe.ingredients.forEach { ingredient ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = ingredient.amount.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) { Text("Aanpassen") }
                    TextButton(onClick = onDelete) { Text("Verwijderen") }
                }
            }
        }
    }
}

/** Full-screen editor for one recipe and its ingredient lines. */
@Composable
private fun RecipeEditor(
    initial: Recipe?,
    onDismiss: () -> Unit,
    onSave: (String, String, List<IngredientBody>) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var lines by remember {
        val existing = initial?.ingredients.orEmpty().map { it.name to it.amount.orEmpty() }
        mutableStateOf(if (existing.isEmpty()) listOf("" to "") else existing)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (initial == null) "Nieuw recept" else "Recept aanpassen",
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notities") },
                    minLines = 2,
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
                            label = { Text("Hoeveelheid") },
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
                        enabled = title.isNotBlank(),
                        onClick = {
                            val ingredients = lines
                                .filter { it.first.isNotBlank() }
                                .map { IngredientBody(it.first.trim(), it.second.trim().ifEmpty { null }) }
                            onSave(title, notes, ingredients)
                        },
                    ) { Text("Opslaan") }
                }
            }
        }
    }
}
