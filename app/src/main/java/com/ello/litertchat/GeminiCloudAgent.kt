package com.ello.litertchat

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Gemini calls go through Ello's authenticated Firebase Function; no model key ships in the APK. */
internal class GeminiCloudAgent(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        contents: JSONArray,
        skills: List<ElloSkill>,
        mcpTools: String,
        apiAnythingSites: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance(FirebaseOnboarding.firebaseApp(appContext))
        val user = auth.currentUser ?: FirebaseOnboarding.ensureUser(appContext)
        val token = user.getIdToken(false).await().token?.takeIf(String::isNotBlank)
            ?: error("Could not verify your Ello sign-in. Please sign in again.")
        val baseUrl = BuildConfig.AGENT_API_BASE_URL.trim().trimEnd('/')
        require(baseUrl.startsWith("https://")) { "Ello's Gemini cloud endpoint is not configured." }
        val body = JSONObject()
            .put("contents", contents)
            .put("skills", JSONArray().apply {
                skills.filter { it.selected }.take(40).forEach { skill ->
                    put(JSONObject().put("name", skill.name.take(80)).put("description", skill.description.take(500)))
                }
            })
            .put("mcpTools", mcpTools.take(12_000))
            .put("apiAnythingSites", apiAnythingSites.take(4_000))
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/agentTurn")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) {
                error(json.optString("error").ifBlank { "Gemini request failed (${response.code})." })
            }
            json
        }
    }
}
