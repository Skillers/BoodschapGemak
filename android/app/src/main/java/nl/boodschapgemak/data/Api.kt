package nl.boodschapgemak.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")

/**
 * Thin wrapper over the BoodschapGemak API. Every call blocks, so callers
 * must be on a background dispatcher.
 */
class Api(private val baseUrl: String, private val householdKey: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false   // a null field is omitted, not sent as null
        encodeDefaults = true
        coerceInputValues = true
    }

    // --- plumbing ---------------------------------------------------------

    private fun builder(path: String) = Request.Builder()
        .url(baseUrl.trimEnd('/') + path)
        .header("x-household-key", householdKey)

    private fun send(request: Request): String {
        client.newCall(request).execute().use { response: Response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, body)
            return body
        }
    }

    private fun <T> body(value: T, serializer: kotlinx.serialization.SerializationStrategy<T>) =
        json.encodeToString(serializer, value).toRequestBody(JSON_MEDIA)

    // --- shopping list ----------------------------------------------------

    fun getItems(): List<ShoppingItem> =
        json.decodeFromString(ListSerializer(ShoppingItem.serializer()), send(builder("/api/items").get().build()))

    /** Returns the stored row, so the caller gets the server-assigned id. */
    fun addItem(item: NewItemBody): ShoppingItem = json.decodeFromString(
        ShoppingItem.serializer(),
        send(builder("/api/items").post(body(item, NewItemBody.serializer())).build()),
    )

    fun patchItem(id: Int, patch: ItemPatchBody) {
        send(builder("/api/items/$id").patch(body(patch, ItemPatchBody.serializer())).build())
    }

    fun deleteItem(id: Int) {
        send(builder("/api/items/$id").delete().build())
    }

    fun clearChecked() {
        send(builder("/api/items/clear-checked").post(EMPTY_BODY).build())
    }

    // --- running total ----------------------------------------------------

    fun getCurrentTrip(): Trip =
        json.decodeFromString(Trip.serializer(), send(builder("/api/trips/current").get().build()))

    fun getTripHistory(): List<TripSummary> =
        json.decodeFromString(ListSerializer(TripSummary.serializer()), send(builder("/api/trips").get().build()))

    fun addEntry(entry: NewEntryBody) {
        send(builder("/api/trips/current/entries").post(body(entry, NewEntryBody.serializer())).build())
    }

    fun deleteEntry(entryId: Int) {
        send(builder("/api/trips/current/entries/$entryId").delete().build())
    }

    fun closeTrip() {
        send(builder("/api/trips/current/close").post(EMPTY_BODY).build())
    }

    fun renameTrip(label: String) {
        send(builder("/api/trips/current").patch(body(TripLabelBody(label), TripLabelBody.serializer())).build())
    }

    // --- recipes ----------------------------------------------------------

    fun getRecipes(): List<Recipe> =
        json.decodeFromString(ListSerializer(Recipe.serializer()), send(builder("/api/recipes").get().build()))

    fun addRecipe(recipe: RecipeBody) {
        send(builder("/api/recipes").post(body(recipe, RecipeBody.serializer())).build())
    }

    fun updateRecipe(id: Int, recipe: RecipeBody) {
        send(builder("/api/recipes/$id").patch(body(recipe, RecipeBody.serializer())).build())
    }

    fun deleteRecipe(id: Int) {
        send(builder("/api/recipes/$id").delete().build())
    }

    // --- live connection --------------------------------------------------

    /** Opens the push socket. The caller owns cancelling the returned socket. */
    fun openLiveSocket(listener: WebSocketListener): WebSocket {
        val key = URLEncoder.encode(householdKey, "UTF-8")
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/live?key=$key").build()
        return client.newWebSocket(request, listener)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null, 0, 0)
    }
}
