package com.imanieh.zaersara.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SessionExpiredException(message: String = "نشست شما منقضی شده؛ دوباره وارد شوید.") : Exception(message)

class SupabaseRest(private val prefs: AppPrefs) {
    private fun connection(path: String, method: String = "GET", token: String = prefs.accessToken): HttpURLConnection {
        val c = URL(prefs.baseUrl + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("apikey", prefs.anonKey)
        c.setRequestProperty("Content-Type", "application/json")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 12000
        c.readTimeout = 12000
        return c
    }

    private data class Raw(val code: Int, val text: String)
    private fun raw(c: HttpURLConnection): Raw {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        return Raw(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    private fun saveSession(obj: JSONObject) {
        prefs.accessToken = obj.optString("access_token")
        val refresh = obj.optString("refresh_token")
        if (refresh.isNotBlank()) prefs.refreshToken = refresh
    }

    fun signIn(email: String, password: String): String {
        val c = connection("/auth/v1/token?grant_type=password", "POST", "")
        c.doOutput = true
        c.outputStream.use { it.write(JSONObject().put("email", email).put("password", password).toString().toByteArray()) }
        val r = raw(c)
        if (r.code !in 200..299) error("ورود ناموفق بود. ایمیل یا رمز عبور را بررسی کنید.")
        val obj = JSONObject(r.text)
        saveSession(obj)
        return prefs.accessToken
    }

    @Synchronized
    private fun refreshSession(): Boolean {
        if (prefs.refreshToken.isBlank()) return false
        return runCatching {
            val c = connection("/auth/v1/token?grant_type=refresh_token", "POST", "")
            c.doOutput = true
            c.outputStream.use { it.write(JSONObject().put("refresh_token", prefs.refreshToken).toString().toByteArray()) }
            val r = raw(c)
            if (r.code !in 200..299) return false
            saveSession(JSONObject(r.text))
            prefs.accessToken.isNotBlank()
        }.getOrDefault(false)
    }

    private fun execute(path: String, method: String = "GET", body: String? = null, prefer: String? = null): String {
        fun once(): Raw {
            val c = connection(path, method)
            if (prefer != null) c.setRequestProperty("Prefer", prefer)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            return raw(c)
        }
        var r = once()
        val jwtExpired = r.code == 401 || r.text.contains("PGRST303") || r.text.contains("JWT expired", ignoreCase = true)
        if (jwtExpired) {
            if (!refreshSession()) {
                prefs.clearSession()
                throw SessionExpiredException()
            }
            r = once()
        }
        if (r.code !in 200..299) {
            val friendly = runCatching { JSONObject(r.text).optString("message") }.getOrNull().orEmpty()
            error(if (friendly.isNotBlank()) friendly else "خطا در ارتباط با سرور (${r.code})")
        }
        return r.text
    }

    fun get(path: String): JSONArray = JSONArray(execute(path))

    fun post(path: String, body: JSONObject): JSONObject {
        val txt = execute(path, "POST", body.toString(), "return=representation").trim()
        return if (txt.startsWith("[")) {
            val arr = JSONArray(txt); if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
        } else if (txt.startsWith("{")) JSONObject(txt) else JSONObject()
    }

    fun patch(path: String, body: JSONObject) { execute(path, "PATCH", body.toString(), "return=minimal") }
    fun postArray(path: String, body: JSONArray) { execute(path, "POST", body.toString(), "return=minimal") }
}
