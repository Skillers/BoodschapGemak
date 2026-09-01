package nl.boodschapgemak.data

import android.content.Context

/** Server address, shared key and which of the two of you this phone is. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("boodschapgemak", Context.MODE_PRIVATE)

    /**
     * Where the API lives. Defaults to this household's own server so nobody
     * has to type a URL on first run; the setup screen only exposes it under
     * "Geavanceerd", for the day the server moves.
     */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty().ifEmpty { DEFAULT_BASE_URL }
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    var householdKey: String
        get() = prefs.getString(KEY_HOUSEHOLD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOUSEHOLD, value.trim()).apply()

    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotEmpty() && householdKey.isNotEmpty() && userName.isNotEmpty()

    companion object {
        /** The Tailscale name of the PC that hosts this household's server. */
        const val DEFAULT_BASE_URL = "http://desktop-ctplf50:4000"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_HOUSEHOLD = "household_key"
        private const val KEY_USER = "user_name"
    }
}
