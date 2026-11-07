package com.ello.litertchat

import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object EmailOtpAuth {
    private fun endpoint(path: String): String {
        val base = BuildConfig.EMAIL_OTP_API_BASE_URL.trim().trimEnd('/')
        check(base.startsWith("https://") || base.startsWith("http://10.0.2.2")) {
            "Email sign-in server is not configured in this app build."
        }
        return "$base/api/$path"
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun requestCode(email: String) = withOkHttp {
        post(endpoint("requestEmailCode"), JSONObject().put("email", email.trim()).toString())
    }

    suspend fun verifyCode(context: Context, email: String, code: String) = withOkHttp {
        val response = post(
            endpoint("verifyEmailCode"),
            JSONObject().put("email", email.trim()).put("code", code.trim()).toString(),
        )
        val token = response.optString("customToken")
        check(token.isNotBlank()) { "The server did not return a sign-in token." }
        FirebaseAuth.getInstance(FirebaseOnboarding.firebaseApp(context))
            .signInWithCustomToken(token)
            .await()
        response.optString("email")
    }

    private suspend fun <T> withOkHttp(block: suspend () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun post(url: String, body: String): JSONObject {
        val request = Request.Builder().url(url).post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) error(parsed.optString("error").ifBlank { "Request failed (${response.code})." })
            return parsed
        }
    }
}
