package nl.boodschapgemak.data

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingItem(
    val id: Int,
    /** Set on a sub-item, pointing at the gerecht it belongs to. */
    val parentId: Int? = null,
    val name: String,
    val quantity: String? = null,
    val isChecked: Boolean = false,
    val addedBy: String = "",
    val checkedBy: String? = null,
    /** Who is walking to the shelf for this right now, if anyone. */
    val claimedBy: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class TripEntry(
    val id: Int,
    val amountCents: Int,
    val note: String? = null,
    val addedBy: String = "",
    val createdAt: String? = null,
)

/** The open trip: its entries plus the running total they add up to. */
@Serializable
data class Trip(
    val id: Int,
    val label: String,
    val status: String = "open",
    val startedAt: String? = null,
    val entries: List<TripEntry> = emptyList(),
    val totalCents: Int = 0,
)

/** A row in the trip history. */
@Serializable
data class TripSummary(
    val id: Int,
    val label: String,
    val status: String = "closed",
    val startedAt: String? = null,
    val closedAt: String? = null,
    val totalCents: Int = 0,
    val entryCount: Int = 0,
)

@Serializable
data class Ingredient(
    val id: Int = 0,
    val name: String,
    val amount: String? = null,
)

@Serializable
data class Recipe(
    val id: Int = 0,
    val title: String,
    val notes: String? = null,
    val plannedFor: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
)

// --- request bodies -------------------------------------------------------
// The Json config drops nulls, so a field left null here is simply not sent
// and the server leaves that column alone. To *clear* a text column, send "".

@Serializable
data class NewItemBody(
    val name: String,
    val quantity: String? = null,
    val addedBy: String = "",
    /** Set to put this item inside a gerecht. */
    val parentId: Int? = null,
)

@Serializable
data class ItemPatchBody(
    val name: String? = null,
    val quantity: String? = null,
    val isChecked: Boolean? = null,
    val by: String? = null,
    /** A name takes the claim, "" releases it. */
    val claimedBy: String? = null,
)

/** The ids of one sibling group, in the order they now appear on screen. */
@Serializable
data class ReorderBody(val ids: List<Int>)

@Serializable
data class NewEntryBody(val amountCents: Int, val note: String? = null, val addedBy: String = "")

@Serializable
data class TripLabelBody(val label: String)

@Serializable
data class IngredientBody(val name: String, val amount: String? = null)

@Serializable
data class RecipeBody(
    val title: String,
    val notes: String? = null,
    val plannedFor: String? = null,
    val ingredients: List<IngredientBody> = emptyList(),
)
