package nl.boodschapgemak.data

import android.content.Context

/** Server address, shared key and which of the two of you this phone is. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("boodschapgemak", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    var householdKey: String
        get() = prefs.getString(KEY_HOUSEHOLD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOUSEHOLD, value.trim()).apply()

    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotEmpty() && householdKey.isNotEmpty() && userName.isNotEmpty()

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_HOUSEHOLD = "household_key"
        const val KEY_USER = "user_name"
    }
}
