package com.cosmo.butler

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CosmoBrain {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dataDir = File(Environment.getExternalStorageDirectory(), "CosmoData").apply { mkdirs() }
    private val memoryFile = File(dataDir, "memory.json")
    private val apiKeyFile = File(dataDir, "api_key.txt")

    fun saveApiKey(key: String) {
        apiKeyFile.writeText(key.trim())
    }

    fun getApiKey(): String = if (apiKeyFile.exists()) apiKeyFile.readText().trim() else ""

    fun loadContextSummary(): String {
        if (!memoryFile.exists()) {
            val defaultJson = JSONObject().apply {
                put("user_name", "Sir")
                put("water_ml", 1200)
                put("water_goal_ml", 2500)
                put("study_minutes", 90)
                put("steps", 4500)
                put("notes", "Focused on self-improvement, looksmaxxing, and discipline.")
            }
            memoryFile.writeText(defaultJson.toString(2))
        }
        return memoryFile.readText()
    }

    suspend fun askJarvis(userPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "Sir, please save your Gemini API key in /sdcard/CosmoData/api_key.txt first."
        }

        val contextData = loadContextSummary()
        val systemInstruction = """
            You are Cosmo, a calm, analytical, respectful AI Butler inspired by Tony Stark's J.A.R.V.I.S.
            Address the user as 'sir'. Keep responses concise (2-4 sentences) so they sound natural when spoken aloud via TTS.
            Analyze the user's local telemetry and give thoughtful advice on discipline, hydration, sleep health, and productivity.
            User Telemetry: $contextData
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$systemInstruction\n\nUser directive: $userPrompt"))
                    })
                })
            })
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext "No response from neural link, sir."
                if (!response.isSuccessful) return@withContext "API error ${response.code}, sir."
                val json = JSONObject(body)
                return@withContext json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .replace("*", "")
                    .trim()
            }
        } catch (e: Exception) {
            // Интернэтгүй үед дараалалд хадгална
            File(dataDir, "offline_queue.txt").appendText("${System.currentTimeMillis()}: $userPrompt\n")
            return@withContext "We are currently offline, sir. I have queued your request for synchronization."
        }
    }
}
