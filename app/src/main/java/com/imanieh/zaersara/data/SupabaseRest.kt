package com.imanieh.zaersara.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseRest(private val prefs: AppPrefs) {
    private fun connection(path: String, method: String = "GET"): HttpURLConnection {
        val c = URL(prefs.baseUrl + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("apikey", prefs.anonKey)
        c.setRequestProperty("Content-Type", "application/json")
        if (prefs.accessToken.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${prefs.accessToken}")
        c.connectTimeout = 12000
        c.readTimeout = 12000
        return c
    }

    private fun response(c: HttpURLConnection): String {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}: $text")
        return text
    }

    fun signIn(email: String, password: String): String {
        val c = connection("/auth/v1/token?grant_type=password", "POST")
        c.doOutput = true
        c.outputStream.use { it.write(JSONObject().put("email", email).put("password", password).toString().toByteArray()) }
        val obj = JSONObject(response(c))
        val token = obj.getString("access_token")
        prefs.accessToken = token
        return token
    }

    fun get(path: String): JSONArray = JSONArray(response(connection(path)))

    fun post(path: String, body: JSONObject): JSONObject {
        val c = connection(path, "POST")
        c.doOutput = true
        c.setRequestProperty("Prefer", "return=representation")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val txt = response(c).trim()
        return if (txt.startsWith("[")) {
            val arr = JSONArray(txt)
            if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
        } else if (txt.startsWith("{")) JSONObject(txt) else JSONObject()
    }

    fun postArray(path: String, body: JSONArray) {
        val c = connection(path, "POST")
        c.doOutput = true
        c.setRequestProperty("Prefer", "return=minimal")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        response(c)
    }
}
