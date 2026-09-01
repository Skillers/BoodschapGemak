package nl.boodschapgemak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.boodschapgemak.data.AppViewModel
import nl.boodschapgemak.data.ShoppingItem
import nl.boodschapgemak.data.Trip
import kotlin.math.roundToInt

private fun keyOf(item: ShoppingItem) = "item-" + item.id

@Composable
fun ShoppingListScreen(vm: AppViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val me = vm.settings.userName

    val listState = rememberLazyListState()

    var renamingId by remember { mutableStateOf<Int?>(null) }
    var addingChildFor by remember { mutableStateOf<Int?>(null) }

    // Which row the finger is carrying, and how far it has moved.
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val childrenOf = items.filter { it.parentId != null }.groupBy { it.parentId }
    val topLevel = items.filter { it.parentId == null }
    val todo = topLevel.filterNot { it.isChecked }
    val done = topLevel.filter { it.isChecked }

    /** Works out where a dragged row was let go, and writes that order. */
    fun commitDrag(siblings: List<ShoppingItem>) {
        val key = dragKey ?: return
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key }
        val movedId = siblings.firstOrNull { keyOf(it) == key }?.id
        if (dragged == null || movedId == null || siblings.size < 2) return

        val droppedCentre = dragged.offset + dragged.size / 2f + dragOffset
        // How many siblings now sit above where the finger let go.
        val target = siblings
            .filter { it.id != movedId }
            .count { sibling ->
                val row = info.visibleItemsInfo.firstOrNull { it.key == keyOf(sibling) }
                row != null && row.offset + row.size / 2f < droppedCentre
            }

        val order = siblings.map { it.id }.toMutableList()
        val from = order.indexOf(movedId)
        if (from == target) return
        order.removeAt(from)
        order.add(target.coerceIn(0, order.size), movedId)
        vm.reorder(order)
    }

    Column(Modifier.fillMaxSize()) {
        RunningTotal(trip = trip, onAdd = vm::addAmount)
        AddItemRow(onAdd = { name -> vm.addItem(name) })
        HorizontalDivider()

        if (items.isEmpty()) {
            EmptyHint("De lijst is leeg. Typ hierboven wat er mee moet.")
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

                fun block(parent: ShoppingItem, siblings: List<ShoppingItem>) {
                    val kids = childrenOf[parent.id].orEmpty()

                    item(key = keyOf(parent)) {
                        ItemRow(
                            item = parent,
                            me = me,
                            isSub = false,
                            hasChildren = kids.isNotEmpty(),
                            renaming = renamingId == parent.id,
                            dragging = dragKey == keyOf(parent),
                            dragOffset = dragOffset,
                            onStartRename = { renamingId = parent.id },
                            onRename = { vm.renameItem(parent, it); renamingId = null },
                            onToggle = { vm.setChecked(parent, !parent.isChecked) },
                            onClaim = { vm.toggleClaim(parent) },
                            onDelete = { vm.deleteItem(parent) },
                            onAddChild = { addingChildFor = parent.id },
                            onDragStart = { dragKey = keyOf(parent); dragOffset = 0f },
                            onDrag = { dragOffset += it },
                            onDragEnd = { commitDrag(siblings); dragKey = null; dragOffset = 0f },
                        )
                        HorizontalDivider()
                    }

                    items(kids.size, key = { keyOf(kids[it]) }) { index ->
                        val kid = kids[index]
                        ItemRow(
                            item = kid,
                            me = me,
                            isSub = true,
                            hasChildren = false,
                            renaming = renamingId == kid.id,
                            dragging = dragKey == keyOf(kid),
                            dragOffset = dragOffset,
                            onStartRename = { renamingId = kid.id },
                            onRename = { vm.renameItem(kid, it); renamingId = null },
                            onToggle = { vm.setChecked(kid, !kid.isChecked) },
                            onClaim = { vm.toggleClaim(kid) },
                            onDelete = { vm.deleteItem(kid) },
                            onAddChild = null,
                            onDragStart = { dragKey = keyOf(kid); dragOffset = 0f },
                            onDrag = { dragOffset += it },
                            onDragEnd = { commitDrag(kids); dragKey = null; dragOffset = 0f },
                        )
                        HorizontalDivider()
                    }

                    if (addingChildFor == parent.id) {
                        item(key = "add-child-" + parent.id) {
                            NewSubItemRow(
                                onDone = { name ->
                                    if (name.isNotBlank()) vm.addItem(name, parentId = parent.id)
                                    addingChildFor = null
                                },
                                onCancel = { addingChildFor = null },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                todo.forEach { block(it, todo) }

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
                    done.forEach { block(it, done) }
                }
            }
        }
    }
}

/**
 * One row, whether it is a gerecht or something under one. Tap the name to
 * rename it where it stands; drag the handle to move it among its own kind.
 */
@Composable
private fun ItemRow(
    item: ShoppingItem,
    me: String,
    isSub: Boolean,
    hasChildren: Boolean,
    renaming: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    onStartRename: () -> Unit,
    onRename: (String) -> Unit,
    onToggle: () -> Unit,
    onClaim: () -> Unit,
    onDelete: () -> Unit,
    onAddChild: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val claimedBy = item.claimedBy?.takeIf { it.isNotBlank() && !item.isChecked }
    val claimedByMe = claimedBy == me

    val background = when {
        dragging -> MaterialTheme.colorScheme.surfaceVariant
        claimedBy == null -> Color.Transparent
        claimedByMe -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
            .background(background)
            .padding(start = if (isSub) 28.dp else 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = "Verslepen",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(24.dp)
                .pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
        )

        Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })

        if (renaming) {
            val focus = remember { FocusRequester() }
            var text by remember(item.id) { mutableStateOf(item.name) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onRename(text) }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .focusRequester(focus),
            )
            LaunchedEffect(item.id) { focus.requestFocus() }
            IconButton(onClick = { onRename(text) }) {
                Icon(Icons.Outlined.Check, contentDescription = "Naam opslaan")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Verwijderen",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onStartRename)
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = item.name,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    style = if (hasChildren) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                )
                val note = listOfNotNull(
                    item.quantity?.takeIf { it.isNotBlank() },
                    when {
                        item.isChecked -> item.checkedBy?.takeIf { it.isNotBlank() }?.let { "in de kar door $it" }
                        claimedByMe -> "jij loopt ernaartoe"
                        claimedBy != null -> "$claimedBy loopt ernaartoe"
                        else -> null
                    },
                ).joinToString(" - ")
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // A gerecht is not something you walk to a shelf for, so it gets
            // the add button instead of a claim.
            if (!item.isChecked && !hasChildren) {
                ClaimChip(claimedBy = claimedBy, claimedByMe = claimedByMe, onClick = onClaim)
            }
            if (onAddChild != null) {
                IconButton(onClick = onAddChild) {
                    Icon(Icons.Outlined.Add, contentDescription = "Onderdeel toevoegen")
                }
            }
        }
    }
}

/** The inline field that appears under a gerecht when you tap its plus. */
@Composable
private fun NewSubItemRow(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth().padding(start = 52.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Onderdeel") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(text) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        IconButton(onClick = { onDone(text) }) {
            Icon(Icons.Outlined.Check, contentDescription = "Toevoegen")
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Delete, contentDescription = "Annuleren")
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * What this shop has cost so far, and the one field that changes it. Type an
 * amount, tap +, the number climbs.
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
private fun AddItemRow(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    fun submit() {
        if (name.isBlank()) return
        onAdd(name)
        name = ""
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product of gerecht") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = ::submit, enabled = name.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = "Toevoegen")
        }
    }
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
internal fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}
