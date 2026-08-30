package com.imanieh.zaersara.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("zaersara", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = p.getString("base_url", "") ?: ""
        set(v) = p.edit().putString("base_url", v.trimEnd('/')).apply()
    var anonKey: String
        get() = p.getString("anon_key", "") ?: ""
        set(v) = p.edit().putString("anon_key", v).apply()
    var accessToken: String
        get() = p.getString("access_token", "") ?: ""
        set(v) = p.edit().putString("access_token", v).apply()
    var refreshToken: String
        get() = p.getString("refresh_token", "") ?: ""
        set(v) = p.edit().putString("refresh_token", v).apply()
    val configured get() = baseUrl.isNotBlank() && anonKey.isNotBlank()
    fun clearSession() { p.edit().remove("access_token").remove("refresh_token").apply() }
}
