package nl.boodschapgemak.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class LiveStatus { Offline, Connecting, Live }

/** Push events carry the changed row, so nothing has to be fetched back. */
@Serializable
private data class LiveEvent(
    val type: String,
    val item: ShoppingItem? = null,
    val id: Int? = null,
)

/**
 * Holds everything on screen.
 *
 * Two things keep the shopping list feeling immediate. Your own taps are
 * applied locally before the request goes out, so your screen never waits for
 * the network. And the server pushes the changed row itself rather than a
 * "go and refetch" ping, so the other phone renders after one hop instead of
 * a round trip.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)

    val items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val trip = MutableStateFlow<Trip?>(null)
    val history = MutableStateFlow<List<TripSummary>>(emptyList())
    val recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val live = MutableStateFlow(LiveStatus.Offline)
    val configured = MutableStateFlow(settings.isConfigured)
    val error = MutableStateFlow<String?>(null)

    private val json = Json { ignoreUnknownKeys = true }
    private var api: Api? = null
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    init {
        if (settings.isConfigured) connect()
    }

    // --- lifecycle --------------------------------------------------------

    /** (Re)builds the API client from the stored settings and opens the socket. */
    fun connect() {
        disconnect()
        if (!settings.isConfigured) {
            configured.value = false
            return
        }
        configured.value = true
        api = Api(settings.baseUrl, settings.householdKey)
        refreshAll()
        openSocket()
    }

    fun saveSettings(baseUrl: String, householdKey: String, userName: String) {
        settings.baseUrl = baseUrl
        settings.householdKey = householdKey
        settings.userName = userName
        connect()
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.cancel()
        socket = null
        live.value = LiveStatus.Offline
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    fun dismissError() {
        error.value = null
    }

    // --- realtime ---------------------------------------------------------

    private fun openSocket() {
        val client = api ?: return
        live.value = LiveStatus.Connecting
        socket = client.openLiveSocket(object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                live.value = LiveStatus.Live
                // Catch up on anything missed while the socket was down.
                refreshAll()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { json.decodeFromString(LiveEvent.serializer(), text) }.getOrNull()
                when (event?.type) {
                    "item.upserted" -> event.item?.let(::applyItem)
                    "item.deleted" -> event.id?.let(::removeItem)
                    "items.reload" -> refreshItems()
                    "trip.changed" -> refreshTrip()
                    "recipes.changed" -> refreshRecipes()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = scheduleReconnect()

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleReconnect()
        })
    }

    /** Backs off up to 15s so a phone with no signal does not hammer the radio. */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        live.value = LiveStatus.Offline
        reconnectJob = viewModelScope.launch {
            val waitMs = minOf(15_000L, 1_000L * (1L shl minOf(reconnectAttempt, 4)))
            reconnectAttempt++
            delay(waitMs)
            if (settings.isConfigured) openSocket()
        }
    }

    // --- local list bookkeeping -------------------------------------------

    /** Same order the server uses: unticked first, then by position. */
    private fun List<ShoppingItem>.inListOrder() =
        sortedWith(compareBy({ it.isChecked }, { it.sortOrder }, { it.id }))

    private fun applyItem(item: ShoppingItem) {
        items.value = (items.value.filterNot { it.id == item.id } + item).inListOrder()
    }

    private fun removeItem(id: Int) {
        items.value = items.value.filterNot { it.id == id }
    }

    // --- reads ------------------------------------------------------------

    /**
     * Runs a call off the main thread. [onFailure] undoes anything that was
     * applied locally in advance of the server agreeing to it.
     */
    private fun call(onFailure: (() -> Unit)? = null, block: suspend (Api) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val client = api ?: return@launch
            try {
                block(client)
            } catch (e: Exception) {
                error.value = e.message ?: "Kon de server niet bereiken"
                onFailure?.invoke()
            }
        }

    fun refreshAll() {
        refreshItems()
        refreshTrip()
        refreshRecipes()
    }

    fun refreshItems() = call { items.value = it.getItems() }

    fun refreshTrip() = call {
        trip.value = it.getCurrentTrip()
        history.value = it.getTripHistory()
    }

    fun refreshRecipes() = call { recipes.value = it.getRecipes() }

    // --- shopping list ----------------------------------------------------

    fun addItem(name: String, quantity: String) = call {
        // No local id to invent, so this one waits for the server. Adding is
        // not the part you do while racing each other down an aisle.
        applyItem(it.addItem(NewItemBody(name.trim(), quantity.trim().ifEmpty { null }, settings.userName)))
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) {
        applyItem(
            item.copy(
                isChecked = checked,
                checkedBy = if (checked) settings.userName else null,
                claimedBy = null,
            )
        )
        call(onFailure = { refreshItems() }) {
            it.patchItem(item.id, ItemPatchBody(isChecked = checked, by = settings.userName))
        }
    }

    /** "I am walking to this shelf" - tap again to let it go. */
    fun toggleClaim(item: ShoppingItem) {
        val claim = if (item.claimedBy == settings.userName) null else settings.userName
        applyItem(item.copy(claimedBy = claim))
        call(onFailure = { refreshItems() }) {
            it.patchItem(item.id, ItemPatchBody(claimedBy = claim.orEmpty()))
        }
    }

    fun editItem(item: ShoppingItem, name: String, quantity: String) {
        applyItem(item.copy(name = name.trim(), quantity = quantity.trim().ifEmpty { null }))
        call(onFailure = { refreshItems() }) {
            it.patchItem(item.id, ItemPatchBody(name = name.trim(), quantity = quantity.trim()))
        }
    }

    fun deleteItem(item: ShoppingItem) {
        removeItem(item.id)
        call(onFailure = { refreshItems() }) { it.deleteItem(item.id) }
    }

    fun clearChecked() = call(onFailure = { refreshItems() }) {
        it.clearChecked()
        items.value = it.getItems()
    }

    // --- running total ----------------------------------------------------

    fun addAmount(amountCents: Int, note: String) = call {
        it.addEntry(NewEntryBody(amountCents, note.trim().ifEmpty { null }, settings.userName))
        trip.value = it.getCurrentTrip()
    }

    fun deleteEntry(entry: TripEntry) = call {
        it.deleteEntry(entry.id)
        trip.value = it.getCurrentTrip()
    }

    fun closeTrip() = call {
        it.closeTrip()
        trip.value = it.getCurrentTrip()
        history.value = it.getTripHistory()
    }

    fun renameTrip(label: String) = call {
        it.renameTrip(label.trim())
        trip.value = it.getCurrentTrip()
        history.value = it.getTripHistory()
    }

    // --- recipes ----------------------------------------------------------

    fun saveRecipe(id: Int, title: String, notes: String, ingredients: List<IngredientBody>) = call {
        val bodyToSend = RecipeBody(title.trim(), notes.trim(), null, ingredients)
        if (id == 0) it.addRecipe(bodyToSend) else it.updateRecipe(id, bodyToSend)
        recipes.value = it.getRecipes()
    }

    fun deleteRecipe(recipe: Recipe) = call {
        it.deleteRecipe(recipe.id)
        recipes.value = it.getRecipes()
    }
}
